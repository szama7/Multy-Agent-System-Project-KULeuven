package CNetProtocol;

import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import CNetProtocol.ACLStructure.Informative;

public class Customer extends Parcel implements CommUser, TickListener {

	// Utasoknál a min/max range lehet ha telefonál a taxinak - így broadcast
	// bármelyik hallhatja majd
	// vagy csak integet - így jóval szûkebben csak a környezõ autósok látják. -
	// ez alapján a színük is változhatna
	// hogy a szimulációban lássuk - és külön kéne számolni hogy mekkora
	// százalékban adják fel a próbálkozást.

	static final double MIN_RANGE = 100;
	static final double MAX_RANGE = 20000;
	static final double RANGE_STEP = .4;

	static final int ANSWER_DELAY = 2; // number of ticks.
	static final double REABILITY = 1;

	Optional<CommDevice> device;

	private double range;
	private final double reliability;
	private final RandomGenerator rnd;
	private TreeMap<Double, TaxiVehicle> bids = new TreeMap<>();
	private Long deadline = null;
	private boolean hasTransporter = false;
	private boolean startComm = true;

	Customer(RandomGenerator rnd, ParcelDTO dto) {
		super(dto);
		this.rnd = rnd;
		device = Optional.absent();
		range = MAX_RANGE; // MIN_RANGE;
		reliability = REABILITY;
	}

	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
	}

	@Override
	public void afterTick(TimeLapse arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void tick(TimeLapse timeLapse) {
		if (device.isPresent()) {

			RoadModel rm = getRoadModel();

			if (startComm && !hasTransporter) {
				deadline = timeLapse.getStartTime() + timeLapse.getTickLength() * ANSWER_DELAY;
				ACLStructure msg = new ACLStructure(Informative.CFP, deadline);
				device.get().broadcast(msg);
				
				startComm = false;
			}

			handleIncomingMessages(rm, timeLapse);

			if (!hasTransporter) {
				System.out.println("A");
				if (timeLapse.getStartTime() == deadline) {
					// select the best proposal
					System.out.println("B");
					boolean first = true;
					for (Entry<Double, TaxiVehicle> entry : bids.entrySet()) {
						System.out.println("C");
						TaxiVehicle to = entry.getValue();
						System.out.println("entry.getValue: " + entry.getValue().toString());
						ACLStructure msg;
						if (first) { // The first element in the Map has the
										// smallest distance from this parcel
							msg = new ACLStructure(Informative.ACCEPT_PROPOSAL);
							first = false;
						} else {
							msg = new ACLStructure(Informative.REJECT_PROPOSAL);
						}
						device.get().send(msg, to);
						System.out.println("Customer respond for the BID proposal message : " + msg.getInformative());
					}

				} else if (timeLapse.getStartTime() > deadline) {
					// Start over with higher range
					// TODO nextRange();
					startComm = true;
				}
			}
		}
	}

	private void handleIncomingMessages(RoadModel rm, TimeLapse timeLapse) {
		List<Message> messages = ImmutableList.of();

		if (device.get().getUnreadCount() > 0) {
			messages = device.get().getUnreadMessages();
		}

		for (Message message : messages) {

			ACLStructure msg = (ACLStructure) message.getContents();
			CommUser sender = message.getSender();
			
			System.out.println("The message what the customer get is a: " + msg.getInformative());
			switch (msg.getInformative()) {
			case REFUSE:
				// Do nothing
				break;

			case PROPOSE:
				System.out.println("B'");
				if (timeLapse.getStartTime() <= deadline) {
					Double bid = msg.getBid().get();
					bids.put(bid, (TaxiVehicle) sender);
					System.out.println("'C");
				}
				break;

			case FAILURE:
				startComm = true;
				hasTransporter = false;
				// TODO range = MIN_RANGE;
				break;

			case AGREE:
				hasTransporter = true;
				break;

			default:
				// No answer
				break;
			}
		}
	}

	@Override
	public Optional<Point> getPosition() {
		final RoadModel rm = getRoadModel();
		if (rm.containsObject(this)) {
			return Optional.of(getPickupLocation());
		}
		return Optional.absent();
	}

	@Override
	public void setCommDevice(CommDeviceBuilder commDeviceBuilder) {
		if (range >= 0) {
			commDeviceBuilder.setMaxRange(range);
		}
		
		device = Optional.of(commDeviceBuilder.setReliability(reliability).build());
		System.out.println("customer communication range is: " + range + " in real life: " + device.get().getMaxRange());
	}

	// NEM IS FOG KELLENI
	private void nextRange() {
		if (range < MAX_RANGE)
			range += RANGE_STEP;
	}
}
