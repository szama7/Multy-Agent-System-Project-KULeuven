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
	private TreeMap<Double, TaxiVehicle> bids = new TreeMap<>();
	private Long deadline = null;
	private boolean hasTransporter = false;
	private boolean startComm = true;
	private int messageCounter = 0;
	
	public int getMessageCounter() {
		return messageCounter;
	}

	Customer(ParcelDTO dto) {
		super(dto);
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
				if (timeLapse.getStartTime() == deadline) {
					// select the best proposal
					boolean first = true;
					for (Entry<Double, TaxiVehicle> entry : bids.entrySet()) {
						TaxiVehicle to = entry.getValue();
						ACLStructure msg;
						if (first) { // The first element in the Map has the
										// smallest distance from this parcel
							msg = new ACLStructure(Informative.ACCEPT_PROPOSAL);
							first = false;
						} else {
							msg = new ACLStructure(Informative.REJECT_PROPOSAL);
						}
						device.get().send(msg, to);
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
			
			switch (msg.getInformative()) {
			case REFUSE:
				// Do nothing
				break;

			case PROPOSE:
				if (timeLapse.getStartTime() <= deadline) {
					Double bid = msg.getBid().get();
					bids.put(bid, (TaxiVehicle) sender);
					messageCounter++;
				}
				break;

			case FAILURE:
				startComm = true;
				hasTransporter = false;
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
	}
}
