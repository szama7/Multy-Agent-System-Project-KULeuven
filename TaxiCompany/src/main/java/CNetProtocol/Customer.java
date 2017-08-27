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
	static final double MIN_RANGE = 20000;
	static final double MAX_RANGE = MIN_RANGE*1.5;
	public static double getMaxRange() {
		return MAX_RANGE;
	}

	static final double RANGE_STEP = .4;

	static final int ANSWER_DELAY = 2; 
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
		range = MAX_RANGE;
		reliability = REABILITY;
	}

	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
	}

	@Override
	public void afterTick(TimeLapse arg0) {
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
						if (first) {
							msg = new ACLStructure(Informative.ACCEPT_PROPOSAL);
							first = false;
						} else {
							msg = new ACLStructure(Informative.REJECT_PROPOSAL);
						}
						device.get().send(msg, to);
					}

				} else if (timeLapse.getStartTime() > deadline) {
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
				messageCounter++;
				hasTransporter = true;
				break;

			default:
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
