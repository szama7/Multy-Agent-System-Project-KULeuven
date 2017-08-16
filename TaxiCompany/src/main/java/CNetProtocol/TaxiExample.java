/*
 * Copyright (C) 2011-2017 Rinde van Lon, imec-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package CNetProtocol;

import static com.google.common.collect.Maps.newHashMap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.math3.random.RandomGenerator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.comm.CommModel;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.MultiAttributeData;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.io.DotGraphIO;
import com.github.rinde.rinsim.geom.io.Filters;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.CommRenderer;
import com.github.rinde.rinsim.ui.renderers.GraphRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

import CNetProtocol.TaxiRenderer.Language;
import CNetProtocol.Customer;
import CNetProtocol.TaxiBase;
import CNetProtocol.TaxiVehicle;

/**
 * Example showing a fleet of taxis that have to pickup and transport customers
 * around the city of Leuven.
 * <p>
 * If this class is run on MacOS it might be necessary to use
 * -XstartOnFirstThread as a VM argument.
 * 
 * @author Rinde van Lon
 */
public final class TaxiExample {

	private static final int NUM_DEPOTS = 1;
	private static final int NUM_TAXIS = 20;
	private static final int NUM_CUSTOMERS = 30;

	// time in ms
	private static final long SERVICE_DURATION = 60000;
	private static final int TAXI_CAPACITY = 10;
	private static final int DEPOT_CAPACITY = 100;

	private static final int SPEED_UP = 4;
	private static final int MAX_CAPACITY = 3;
	private static final double NEW_CUSTOMER_PROB = .007;

	private static final String MAP_FILE = "/data/maps/leuven-simple.dot";
	private static final Map<String, Graph<MultiAttributeData>> GRAPH_CACHE = newHashMap();

	private static final long TEST_STOP_TIME = 90 * 60 * 1000;
	private static final int TEST_SPEED_UP = 64;

	private static LinkedList<TaxiVehicle> taxiList = null;
	private static LinkedList<Customer> customerList = null;
	private static LinkedList<TaxiBase> taxiBaseList = null;

	private TaxiExample() {
	}

	/**
	 * Starts the {@link TaxiExample}.
	 * 
	 * @param args
	 *            The first option may optionally indicate the end time of the
	 *            simulation.
	 */
	public static void main(@Nullable String[] args) {
		final long endTime = args != null && args.length >= 1 ? Long.parseLong(args[0]) : Long.MAX_VALUE;

		final String graphFile = args != null && args.length >= 2 ? args[1] : MAP_FILE;
		run(false, endTime, graphFile, null /* new Display() */, null, null);
	}

	/**
	 * Run the example.
	 * 
	 * @param testing
	 *            If <code>true</code> enables the test mode.
	 */
	public static void run(boolean testing) {
		run(testing, Long.MAX_VALUE, MAP_FILE, null, null, null);
	}

	/**
	 * Starts the example.
	 * 
	 * @param testing
	 *            Indicates whether the method should run in testing mode.
	 * @param endTime
	 *            The time at which simulation should stop.
	 * @param graphFile
	 *            The graph that should be loaded.
	 * @param display
	 *            The display that should be used to show the ui on.
	 * @param m
	 *            The monitor that should be used to show the ui on.
	 * @param list
	 *            A listener that will receive callbacks from the ui.
	 * @return The simulator instance.
	 */
	public static Simulator run(boolean testing, final long endTime, String graphFile, @Nullable Display display,
			@Nullable Monitor m, @Nullable Listener list) {

		final View.Builder view = createGui(testing, display, m, list);
		taxiList = new LinkedList<TaxiVehicle>();
		customerList = new LinkedList<Customer>();
		taxiBaseList = new LinkedList<TaxiBase>();

		// use map of leuven
		final Simulator simulator = Simulator.builder().addModel(RoadModelBuilders.staticGraph(loadGraph(graphFile)))
				.addModel(DefaultPDPModel.builder()).addModel(CommModel.builder()).addModel(view).build();
		final RandomGenerator rng = simulator.getRandomGenerator();

		final RoadModel roadModel = simulator.getModelProvider().getModel(RoadModel.class);
		// add depots, taxis and parcels to simulator
		for (int i = 0; i < NUM_DEPOTS; i++) {
			TaxiBase taxiBaseInstance = new TaxiBase(roadModel.getRandomPosition(rng), DEPOT_CAPACITY);
			simulator.register(taxiBaseInstance);
			taxiBaseList.add(taxiBaseInstance);
		}
		for (int i = 0; i < NUM_TAXIS; i++) {
			TaxiVehicle taxiInstance = new TaxiVehicle(roadModel.getRandomPosition(rng), TAXI_CAPACITY);
			simulator.register(taxiInstance);
			taxiList.add(taxiInstance);
		}
		for (int i = 0; i < NUM_CUSTOMERS; i++) {
			Customer customerInstance = new Customer(Parcel
					.builder(roadModel.getRandomPosition(rng), roadModel.getRandomPosition(rng))
					.serviceDuration(SERVICE_DURATION).neededCapacity(1 + rng.nextInt(MAX_CAPACITY)).buildDTO());
			simulator.register(customerInstance);
			customerList.add(customerInstance);
		}

		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse time) {
				if (time.getStartTime() > TEST_STOP_TIME) {
					simulator.stop();
					printTaxiData();
					printCustomerData();
				} else if (rng.nextDouble() < NEW_CUSTOMER_PROB) {
					simulator.register(new Customer(
							Parcel.builder(roadModel.getRandomPosition(rng), roadModel.getRandomPosition(rng))
									.serviceDuration(SERVICE_DURATION).neededCapacity(1 + rng.nextInt(MAX_CAPACITY))
									.buildDTO()));
				}
			}
			
			public void printTaxiData() {
				int messageCounter = 0;
				int switchProvisionalCustomerCounter = 0;
				int deliveredPassangersCounter = 0;
				int passangerInCargoTickCounter = 0;
				int totalTickNumCounter = 0;
				int batteryChargingCounter = 0;
				for (TaxiVehicle taxiVehicle : taxiList) {
					messageCounter += taxiVehicle.getMessageCounter();
					deliveredPassangersCounter += taxiVehicle.getDeliveredPassangersCounter();
					passangerInCargoTickCounter += taxiVehicle.getPassangerInCargoTickCounter();
					totalTickNumCounter += taxiVehicle.getTotalTickNumCounter();
					batteryChargingCounter += taxiVehicle.getBatteryChargingCounter();
				}
				System.out.println("In case of DynCNet, these are the statistics: \n\t\n\tTAXI\n\t" + "Total messages: "
						+ messageCounter + "\n\t" + "Total delivered passangers: " + deliveredPassangersCounter + "\n\t"
						+ "Total time what the passangers spend in cargo: " + passangerInCargoTickCounter + "\n\t"
						+ "Total tick numbers: " + totalTickNumCounter + "\n\t"
						+ "Total battery charging times: " + batteryChargingCounter);
			}

			public void printCustomerData() {
				int messageCounter = 0;
				for (Customer customer : customerList) {
					messageCounter += customer.getMessageCounter();
				}
				System.out.println("\n\t\n\tCUSTOMER\n\t" + "Total messages: " + messageCounter);
			}


			@Override
			public void afterTick(TimeLapse timeLapse) {
			}
		});
		simulator.start();

		return simulator;
	}

	static View.Builder createGui(boolean testing, @Nullable Display display, @Nullable Monitor m,
			@Nullable Listener list) {

		View.Builder view = View.builder().with(GraphRoadModelRenderer.builder())
				.with(RoadUserRenderer.builder()
						.withImageAssociation(TaxiBase.class, "/graphics/perspective/tall-building-64.png")
						.withImageAssociation(TaxiVehicle.class, "/graphics/flat/taxi-32.png")
						.withImageAssociation(Customer.class, "/graphics/flat/person-red-32.png"))
				.with(TaxiRenderer.builder(Language.ENGLISH)).withTitleAppendix("Taxi Company")
		// .with(CommRenderer.builder()
		// .withReliabilityColors()
		// .withMessageCount())
		;

		if (testing) {
			view = view.withAutoClose().withAutoPlay().withSimulatorEndTime(TEST_STOP_TIME).withSpeedUp(TEST_SPEED_UP);
		} else if (m != null && list != null && display != null) {
			view = view.withMonitor(m).withSpeedUp(SPEED_UP)
					.withResolution(m.getClientArea().width, m.getClientArea().height).withDisplay(display)
					.withCallback(list).withAsync().withAutoPlay().withAutoClose();
		}
		return view;
	}

	// load the graph file
	static Graph<MultiAttributeData> loadGraph(String name) {
		try {
			if (GRAPH_CACHE.containsKey(name)) {
				return GRAPH_CACHE.get(name);
			}
			final Graph<MultiAttributeData> g = DotGraphIO.getMultiAttributeGraphIO(Filters.selfCycleFilter())
					.read(TaxiExample.class.getResourceAsStream(name));

			GRAPH_CACHE.put(name, g);
			return g;
		} catch (final FileNotFoundException e) {
			throw new IllegalStateException(e);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
