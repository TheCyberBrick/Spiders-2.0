package tcb.spiderstpo.common.entity.movement;

import java.util.EnumSet;

import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.Direction;

public class DirectionalPathPoint extends PathPoint {
	protected static final long ALL_DIRECTIONS = AdvancedWalkNodeProcessor.packDirection(Direction.UP, AdvancedWalkNodeProcessor.packDirection(Direction.DOWN, AdvancedWalkNodeProcessor.packDirection(Direction.NORTH, AdvancedWalkNodeProcessor.packDirection(Direction.EAST, AdvancedWalkNodeProcessor.packDirection(Direction.SOUTH, AdvancedWalkNodeProcessor.packDirection(Direction.WEST, 0L))))));

	protected static final Direction[] DIRECTIONS = Direction.values();

	public final Direction[] directions;

	public DirectionalPathPoint(int x, int y, int z, long packed) {
		super(x, y, z);

		EnumSet<Direction> directionsSet = EnumSet.noneOf(Direction.class);
		for(int i = 0; i < DIRECTIONS.length; i++) {
			Direction dir = DIRECTIONS[i];

			if(AdvancedWalkNodeProcessor.unpackDirection(dir, packed)) {
				directionsSet.add(dir);
			}
		}

		this.directions = directionsSet.toArray(new Direction[0]);
	}

	public DirectionalPathPoint(PathPoint point, long packed) {
		this(point.x, point.y, point.z, packed);

		this.index = point.index;
		this.totalPathDistance = point.totalPathDistance;
		this.distanceToNext = point.distanceToNext;
		this.distanceToTarget = point.distanceToTarget;
		this.previous = point.previous;
		this.visited = point.visited;
		this.field_222861_j = point.field_222861_j;
		this.costMalus = point.costMalus;
		this.nodeType = point.nodeType;
	}

	public DirectionalPathPoint(PathPoint point) {
		this(point, ALL_DIRECTIONS);
	}

	private DirectionalPathPoint(int x, int y, int z, Direction[] directions) {
		super(x, y, z);

		this.directions = new Direction[directions.length];
		System.arraycopy(directions, 0, this.directions, 0, directions.length);
	}

	@Override
	public PathPoint cloneMove(int x, int y, int z) {
		PathPoint pathPoint = new DirectionalPathPoint(x, y, z, this.directions);
		pathPoint.index = this.index;
		pathPoint.totalPathDistance = this.totalPathDistance;
		pathPoint.distanceToNext = this.distanceToNext;
		pathPoint.distanceToTarget = this.distanceToTarget;
		pathPoint.previous = this.previous;
		pathPoint.visited = this.visited;
		pathPoint.field_222861_j = this.field_222861_j;
		pathPoint.costMalus = this.costMalus;
		pathPoint.nodeType = this.nodeType;
		return pathPoint;
	}
}
