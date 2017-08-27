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
	private static final double RANGE = Customer.getMaxRange();
	private static final double RELIABILITY = 1;
	private static final int BATTERY = 6000;
	private final int TAXI_KM_PRICE = 2; // in euro
	private final int TAXI_START_PRICE = 4; // in euro

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
	private double price = 0;
	private double totalMoneyCounter = 0;
	private double deliveryPrice = 0;
	TaxiBase closestTaxiBase = null;

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

		if (!currPassanger.isPresent()) {
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
					if (!currPassanger.isPresent() && !hasToChargeBattery && rm.containsObject(sender)) {
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
				break;

			case ACCEPT_PROPOSAL:
				if (!currPassanger.isPresent() && !hasToChargeBattery) {
					messageCounter++;
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
				break;
			}
		}
		totalTickNumCounter++;
		actualyBattery--;
		ACLStructure answer;
		System.out.println(actualyBattery);
		if (actualyBattery < (4500) && !isPassangerInCargo) {
			hasToChargeBattery = true;
			if (actualCustomer != null) {
				System.out.println("I had a customer");
				answer = new ACLStructure(Informative.FAILURE);
				urh.get().send(answer, actualCustomer);
				actualCustomer = null;
				currPassanger = Optional.absent();
				System.out.println("REFUSE an actual cust");
			}
			goToClosesTaxiBase(rm, time);
		}

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
					totalMoneyCounter += deliveryPrice;
					isPassangerInCargo = false;
					actualCustomer = null;
				}
			} else {
				System.out.println("Im going to the customer");
				rm.moveTo(this, currPassanger.get(), time);
				if (rm.equalPosition(this, currPassanger.get())) {
					isPassangerInCargo = true;
					pm.pickup(this, currPassanger.get(), time);
					deliveryPrice = calculatePrice(rm);
				}
			}
		}

	}

	public double getTotalMoneyCounter() {
		return totalMoneyCounter;
	}

	public double calculatePrice(RoadModel rm) {
		double distance = calcullateDistance(rm.getShortestPathTo(currPassanger.get().getPickupLocation(),
				currPassanger.get().getDeliveryLocation()));
		price = (distance / 10000) * TAXI_KM_PRICE + TAXI_START_PRICE;
		double capacity = currPassanger.get().getNeededCapacity();
		return price * capacity;
	}

	public void goToClosesTaxiBase(RoadModel rm, TimeLapse time) {
		System.out.println("Go to Taxi Base");
		taxiBaseList = TaxiExample.getTaxiBaseList();
		double max = Double.MAX_VALUE;
		double dist = Double.MAX_VALUE;
		if(closestTaxiBase == null){
			for (TaxiBase taxiBase : taxiBaseList) {
				dist = calcullateDistance(rm.getShortestPathTo(this, taxiBase));
				if (dist < max) {
					max = dist;
					closestTaxiBase = taxiBase;
				}
			}
		}else if(closestTaxiBase != null) {
			System.out.println("Yes im going!");
			destination = closestTaxiBase.getOptionalPosition();
			rm.moveTo(this, closestTaxiBase.getPosition(), time);
			if (rm.getPosition(this).equals(closestTaxiBase.getPosition())) {
				actualyBattery = BATTERY;
				hasToChargeBattery = false;
				batteryChargingCounter++;
				closestTaxiBase = null;
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
