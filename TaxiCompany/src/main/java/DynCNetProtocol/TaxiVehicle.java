package DynCNetProtocol;

import java.util.List;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.rand.RandomProvider;
import com.github.rinde.rinsim.core.model.rand.RandomUser;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import DynCNetProtocol.ACLStructure.Informative;

public class TaxiVehicle extends Vehicle implements CommUser, RandomUser {

	private static final double SPEED = 5000;
	private static final double RANGE = Customer.getMaxRange();
	private static final double RELIABILITY = 1;

	private Optional<Customer> currPassanger;
	private Optional<CommDevice> urh;
	private Optional<RandomGenerator> rnd;
	private Optional<Point> destination;
	private boolean inCargo = false;
	private boolean isReserved = false;
	private int messageCounter = 0;
	private int switchProvisionalCustomerCounter = 0;
	private int deliveredPassangersCounter = 0;
	private int passangerInCargoTickCounter = 0;
	private int passangerNotInCargoTickCounter = 0; // ASK RINDE if its enough to substract the passangerInCargoTickCounter from the TEST_STOP_TIME or not... - should be enough.

	Customer provisionCustomer = null;

	protected TaxiVehicle(Point startPosition, int capacity) {
		super(VehicleDTO.builder().capacity(capacity).startPosition(startPosition).speed(SPEED).build());
		currPassanger = Optional.absent();
		urh = Optional.absent();
		rnd = Optional.absent();
		destination = Optional.absent();
	}

	@Override
	public void setRandomGenerator(RandomProvider randomProvider) {
		rnd = Optional.of(randomProvider.newInstance());
	}

	@Override
	public Optional<Point> getPosition() {
		final RoadModel rm = getRoadModel();
		if (rm.containsObject(this)) {
			return Optional.of(rm.getPosition(this));
		}
		return Optional.absent();
	}

	@Override
	public void setCommDevice(CommDeviceBuilder commDeviceBuilder) {
		if (RANGE >= 0) {
			commDeviceBuilder.setMaxRange(RANGE);
		}
		urh = Optional.of(commDeviceBuilder.setReliability(RELIABILITY).build());
		System.out.println("Taxi communication range is: " + RANGE + " in real life: " + urh.get().getMaxRange());
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();

		if (!time.hasTimeLeft()) {
			return;
		}

		// if they dont have any passanger, than move randomly
		// ha megy akkor ezt próbáljuk meg törölni!
		if (!destination.isPresent()) {
			nextDestination(rm);
		}
		// biztos, hogy itt mind a két if kell?? mi a különbség? a destination
		// új pontot ad ha nincs épp destination, a másik is ezt csinálja
		// a fenti ifnek nem kéne hogy értelme legyen!

		if (!currPassanger.isPresent()) { // if there is no task, move randomly
			rm.moveTo(this, destination.get(), time);
			if (rm.getPosition(this).equals(destination.get())) {
				nextDestination(rm);
			}
		}

		List<Message> messages = ImmutableList.of();
		if (urh.get().getUnreadCount() > 0) {
			messages = urh.get().getUnreadMessages();
		}

		for (Message message : messages) {

			ACLStructure msg = (ACLStructure) message.getContents();
			CommUser sender = message.getSender();
			ACLStructure answer;

			switch (msg.getInformative()) {
			case CFP:
				if (time.getStartTime() < msg.getDeadline()) {
					if (!currPassanger.isPresent()) { // lehet ezt majd egy
														// bool-ra át kell
														// cserélni attól függ,
														// hogy ez tényleg csak
														// akkor true-e amikor
														// már felvan véve a
														// kocsiba!
						messageCounter++;
						if (!isReserved) {
							double bid = calcullateDistance(rm.getShortestPathTo(this, (Customer) sender));
							answer = new ACLStructure(Informative.PROPOSE, bid);
							urh.get().send(answer, sender);
						} else if (isReserved && provisionCustomer != null) {
							double provCustDist = calcullateDistance(rm.getShortestPathTo(this, provisionCustomer));
							double newCustDist = calcullateDistance(rm.getShortestPathTo(this, (Customer) sender));
							if (newCustDist < provCustDist) {
								answer = new ACLStructure(Informative.FAILURE);
								urh.get().send(answer, provisionCustomer);
								isReserved = false;
								double bid = newCustDist;
								answer = new ACLStructure(Informative.PROPOSE, bid);
								urh.get().send(answer, sender);
							} else { // newCustDist>provCustDist
								answer = new ACLStructure(Informative.REFUSE);
								urh.get().send(answer, sender);
							}

						} else {
							answer = new ACLStructure(Informative.REFUSE);
							urh.get().send(answer, sender);
						}
					} else {
						answer = new ACLStructure(Informative.REFUSE);
						urh.get().send(answer, sender);
					}
				}
				break;

			case REJECT_PROPOSAL:
				// keep going
				break;

			case PROVISIONAL_ACCEPT:
				if (!currPassanger.isPresent()) {
					if (!isReserved) {
						destination = sender.getPosition();
						answer = new ACLStructure(Informative.AGREE);
						provisionCustomer = (Customer) sender;
						isReserved = true;
						urh.get().send(answer, sender);
					} else if (provisionCustomer != null) { // if isReserved
						double provCustDist = calcullateDistance(rm.getShortestPathTo(this, provisionCustomer));
						double newCustDist = calcullateDistance(rm.getShortestPathTo(this, (Customer) sender));
						if (newCustDist < provCustDist) {
							answer = new ACLStructure(Informative.FAILURE);
							urh.get().send(answer, provisionCustomer);
							destination = sender.getPosition();
							answer = new ACLStructure(Informative.AGREE);
							provisionCustomer = (Customer) sender;
							urh.get().send(answer, sender);
							switchProvisionalCustomerCounter++;
						} else {
							answer = new ACLStructure(Informative.FAILURE);
							urh.get().send(answer, sender);
						}
					} else {
						answer = new ACLStructure(Informative.FAILURE);
						urh.get().send(answer, sender);
					}
				}
				break;
			case ACK:
				if (!currPassanger.isPresent()) {
					currPassanger = Optional.fromNullable(provisionCustomer);
					// pm.pickup(this, currPassanger.get(), time);
					provisionCustomer = null;
				}
				break;

			case REJECT:
				if (provisionCustomer != null) {
					provisionCustomer = null;
					isReserved = false;
				}
				break;

			default:
				// No answer
				break;
			}
		}

		// HANDLE PARCEL
		ACLStructure answer;

		if (provisionCustomer != null) {
			rm.moveTo(this, provisionCustomer, time);
			if (rm.equalPosition(this, provisionCustomer)) {
				// pickup customer
				answer = new ACLStructure(Informative.BOUND);
				urh.get().send(answer, provisionCustomer);
			}
		}
		
		if(inCargo){
			passangerInCargoTickCounter++;
		}

		if (currPassanger.isPresent()) {

			final boolean inCargo = pm.containerContains(this, currPassanger.get());
			// sanity check: if it is not in our cargo AND it is also not on the
			// RoadModel, we cannot go to curr anymore.
			System.out.println("Eooo " + inCargo);
			if (!inCargo && !rm.containsObject(currPassanger.get())) {
				currPassanger = Optional.absent();
			} else if (inCargo) {
				// if it is in cargo, go to its destination
				rm.moveTo(this, currPassanger.get().getDeliveryLocation(), time);
				if (rm.getPosition(this).equals(currPassanger.get().getDeliveryLocation())) {
					// deliver when we arrive
					pm.deliver(this, currPassanger.get(), time);
					deliveredPassangersCounter++;
					isReserved = false; // eztmég ellenõrizni!
					ACLStructure msg = new ACLStructure(Informative.FA);
					urh.get().broadcast(msg);
				}
			} else {
				// it is still available, go there as fast as possible
				rm.moveTo(this, currPassanger.get(), time);
				if (rm.equalPosition(this, currPassanger.get())) {
					// pickup customer
					pm.pickup(this, currPassanger.get(), time);
				}
			}
		}

	}

	public int getPassangerInCargoTickCounter() {
		return passangerInCargoTickCounter;
	}

	private void nextDestination(RoadModel rm) {
		destination = Optional.of(rm.getRandomPosition(rnd.get()));
	}

	// not sure if i need it
	private double calcullateDistance(List<Point> shortestPath) {
		Point from = shortestPath.get(0);
		double distance = 0;

		for (int i = 1; i < shortestPath.size(); i++) {
			Point to = shortestPath.get(i);
			distance += Point.distance(from, to);
			from = to;
		}
		return distance;
	}

	public boolean hasPassanger() {
		return currPassanger.isPresent();
	}

	public boolean isInCargo() {
		return inCargo;
	}

	public int getMessageCounter() {
		return messageCounter;
	}

	public int getSwitchProvisionalCustomerCounter() {
		return switchProvisionalCustomerCounter;
	}
	
	public int getDeliveredPassangersCounter() {
		return deliveredPassangersCounter;
	}
}
