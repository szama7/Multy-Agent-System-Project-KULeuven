package CNetProtocol;

import java.util.LinkedList;
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

import CNetProtocol.ACLStructure.Informative;
import CNetProtocol.Customer;
import CNetProtocol.TaxiBase;

public class TaxiVehicle extends Vehicle implements CommUser, RandomUser {

	private static final double SPEED = 5000;
	private static final double RANGE = 20000;
	private static final double RELIABILITY = 1;
	private static final int BATTERY = 6000;
	private Optional<Customer> currPassanger;
	private Optional<CommDevice> urh;
	private Optional<RandomGenerator> rnd;
	private Optional<Point> destination;
	private boolean inCargo = false;

	private int messageCounter = 0;
	private int deliveredPassangersCounter = 0;
	private int passangerInCargoTickCounter = 0;
	private int totalTickNumCounter = 0;
	private int batteryChargingCounter = 0;

	private int actualyBattery = BATTERY;
	Customer actualCustomer = null;
	private static LinkedList<TaxiBase> taxiBaseList = null;
	private boolean hasToChargeBattery = false;
	private boolean isPassangerInCargo = false;
	
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
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = getRoadModel();
		final PDPModel pm = getPDPModel();

		if (!time.hasTimeLeft()) {
			return;
		}

		if (!destination.isPresent()) {
			nextDestination(rm);
		}

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
			Customer sender = (Customer) message.getSender();
			ACLStructure answer;

			switch (msg.getInformative()) {
			case CFP:
				if (time.getStartTime() < msg.getDeadline()) {
					if (!currPassanger.isPresent() && !hasToChargeBattery) {
						messageCounter++;
						double bid = calcullateDistance(rm.getShortestPathTo(this, sender));
						answer = new ACLStructure(Informative.PROPOSE, bid);

					} else {
						answer = new ACLStructure(Informative.REFUSE);
					}

					urh.get().send(answer, sender);
				}
				break;

			case REJECT_PROPOSAL:
				// keep going
				break;

			case ACCEPT_PROPOSAL:
				if (!currPassanger.isPresent() && !hasToChargeBattery) {
					destination = sender.getPosition();
					currPassanger = Optional.fromNullable(sender);
					answer = new ACLStructure(Informative.AGREE);
					actualCustomer = sender;
				} else {
					answer = new ACLStructure(Informative.FAILURE);
				}

				urh.get().send(answer, sender);
				break;

			default:
				// No answer
				break;
			}
		}
		totalTickNumCounter++;
		actualyBattery--;
		System.out.println("Actual battery: " + actualyBattery);
		ACLStructure answer;
		if (actualyBattery < (5000) && !isPassangerInCargo) {
			if(rm.containsObject(actualCustomer)){
				answer = new ACLStructure(Informative.FAILURE);
				urh.get().send(answer, actualCustomer);
				actualCustomer = null;
			}
			goToClosesTaxiBase(rm, time);
		}

		// HANDLE PARCEL
		if (currPassanger.isPresent()) {
			final boolean inCargo = pm.containerContains(this, currPassanger.get());
			if (!inCargo && !rm.containsObject(currPassanger.get())) {
				currPassanger = Optional.absent();
			} else if (inCargo) {
				rm.moveTo(this, currPassanger.get().getDeliveryLocation(), time);
				passangerInCargoTickCounter++;
				if (rm.getPosition(this).equals(currPassanger.get().getDeliveryLocation())) {
					pm.deliver(this, currPassanger.get(), time);
					deliveredPassangersCounter++;
					isPassangerInCargo = false;
					actualCustomer = null;
				}
			} else {
				rm.moveTo(this, currPassanger.get(), time);
				if (rm.equalPosition(this, currPassanger.get())) {
					isPassangerInCargo = true;
					pm.pickup(this, currPassanger.get(), time);
				}
			}
		}

	}

	public void goToClosesTaxiBase(RoadModel rm, TimeLapse time) {
		hasToChargeBattery = true;
		System.out.println("GO TO TAXIBASE");
		taxiBaseList = TaxiExample.getTaxiBaseList();
		double max = Double.MAX_VALUE;
		double dist = Double.MAX_VALUE;
		TaxiBase closestTaxiBase = null;
		for (TaxiBase taxiBase : taxiBaseList) {
			dist = calcullateDistance(rm.getShortestPathTo(this, taxiBase));
			if (dist < max) {
				max = dist;
				closestTaxiBase = taxiBase;
			}
		}
		if (closestTaxiBase != null) {
			rm.moveTo(this, closestTaxiBase.getPosition(), time);
			if (rm.getPosition(this).equals(closestTaxiBase.getPosition())) {
				actualyBattery = BATTERY;
				hasToChargeBattery = false;
				batteryChargingCounter++;
			}
		}
	}

	private void nextDestination(RoadModel rm) {
		destination = Optional.of(rm.getRandomPosition(rnd.get()));
	}

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

	public int getDeliveredPassangersCounter() {
		return deliveredPassangersCounter;
	}

	public int getPassangerInCargoTickCounter() {
		return passangerInCargoTickCounter;
	}

	public int getTotalTickNumCounter() {
		return totalTickNumCounter;
	}

	public int getBatteryChargingCounter() {
		return batteryChargingCounter;
	}

}
