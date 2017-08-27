package CNetProtocol;

import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;

public class TaxiBase extends Depot {
	
	private Point position;
	
	TaxiBase(Point position, double capacity) {
		super(position);
		setCapacity(capacity);
		this.position = position;
	}
	
	public Point getPosition() {
		return position;
	}
	
	public Optional<Point> getOptionalPosition() {
		final RoadModel rm = getRoadModel();
		if (rm.containsObject(this)) {
			return Optional.of(getPosition());
		}
		return Optional.absent();
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
	}
}