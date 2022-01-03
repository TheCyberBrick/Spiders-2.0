package tcb.spiderstpo.common.entity.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.*;

public class AdvancedPathFinder extends CustomPathFinder {
	private static class PathFinderNode {
		private final PathFinderNode previous;
		private final DirectionalPathPoint pathPoint;
		private final Direction side;
		private final int depth;

		private PathFinderNode(@Nullable PathFinderNode previous, DirectionalPathPoint pathPoint) {
			this.previous = previous;
			this.depth = previous != null ? previous.depth + 1 : 0;
			this.pathPoint = pathPoint;
			this.side = pathPoint.getPathSide();
		}

		private PathFinderNode(PathFinderNode previous, int depth, DirectionalPathPoint pathPoint) {
			this.previous = previous;
			this.depth = depth;
			this.pathPoint = pathPoint;
			this.side = pathPoint.getPathSide();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((this.pathPoint == null) ? 0 : this.pathPoint.hashCode());
			result = prime * result + ((this.side == null) ? 0 : this.side.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj) {
				return true;
			}
			if(obj == null) {
				return false;
			}
			if(this.getClass() != obj.getClass()) {
				return false;
			}
			PathFinderNode other = (PathFinderNode) obj;
			if(this.pathPoint == null) {
				if(other.pathPoint != null) {
					return false;
				}
			} else if(!this.pathPoint.equals(other.pathPoint)) {
				return false;
			}
			if(this.side != other.side) {
				return false;
			}
			return true;
		}
	}

	private static final Direction[] DOWN = new Direction[] { Direction.DOWN };

	public AdvancedPathFinder(NodeEvaluator processor, int maxExpansions) {
		super(processor, maxExpansions);
	}

	@Override
	protected Path createPath(Node _targetPoint, BlockPos target, boolean isTargetReached) {
		List<Node> points = new ArrayList<>();

		//Backtrack path from target point back to entity
		this.backtrackPath(points, _targetPoint);

		//Retrace path with valid side transitions
		PathFinderNode end = this.retraceSidedPath(points, true);

		if(end == null) {
			return new Path(Collections.emptyList(), target, isTargetReached);
		}

		points.clear();

		//Backtrack retraced path
		this.backtrackPath(points, end);

		return new Path(points, target, isTargetReached);
	}

	private void backtrackPath(List<Node> points, Node start) {
		Node currentPathPoint = start;
		points.add(start);

		while(currentPathPoint.cameFrom != null) {
			currentPathPoint = currentPathPoint.cameFrom;
			points.add(currentPathPoint);
		}
	}

	private void backtrackPath(List<Node> points, PathFinderNode start) {
		PathFinderNode currentPathFinderNode = start;
		points.add(start.pathPoint);

		while(currentPathFinderNode.previous != null) {
			currentPathFinderNode = currentPathFinderNode.previous;
			points.add(currentPathFinderNode.pathPoint);
		}
	}

	private static Direction[] getPathableSidesWithFallback(DirectionalPathPoint point) {
		if(point.getPathableSides().length == 0) {
			return DOWN;
		} else {
			return point.getPathableSides();
		}
	}

	private static boolean isOmnidirectionalPoint(DirectionalPathPoint point) {
		return point.type == BlockPathTypes.WATER || point.type == BlockPathTypes.LAVA;
	}

	private PathFinderNode retraceSidedPath(List<Node> points, boolean isReversed) {
		if(points.isEmpty()) {
			return null;
		}

		final Deque<PathFinderNode> queue = new LinkedList<>();

		final DirectionalPathPoint targetPoint = this.ensureDirectional(points.get(0));

		for(Direction direction : getPathableSidesWithFallback(targetPoint)) {
			queue.add(new PathFinderNode(null, targetPoint.assignPathSide(direction)));
		}

		PathFinderNode end = null;

		final int maxExpansions = 200;
		final Set<PathFinderNode> checkedSet = new HashSet<>();

		int expansions = 0;
		while(!queue.isEmpty()) {
			if(expansions++ > maxExpansions) {
				break;
			}

			PathFinderNode current = queue.removeFirst();

			if(current.depth == points.size() - 1) {
				end = current;
				break;
			}

			Direction currentSide = current.side;

			DirectionalPathPoint next = this.ensureDirectional(points.get(current.depth + 1));

			for(Direction nextSide : getPathableSidesWithFallback(next)) {
				PathFinderNode nextPathFinderNode = null;

				if((isReversed && current.pathPoint.isDrop()) || (!isReversed && next.isDrop())) {

					//Side doesn't matter if node represents a drop
					nextPathFinderNode = new PathFinderNode(current, next.assignPathSide(nextSide));

				} else {
					int dx = (int)Math.signum(next.x - current.pathPoint.x);
					int dy = (int)Math.signum(next.y - current.pathPoint.y);
					int dz = (int)Math.signum(next.z - current.pathPoint.z);

					int adx = Math.abs(dx);
					int ady = Math.abs(dy);
					int adz = Math.abs(dz);

					int d = adx + ady + adz;

					if(d == 1) {
						//Path is straight line

						if(nextSide == currentSide) {

							//Allow movement on the same side
							nextPathFinderNode = new PathFinderNode(current, next.assignPathSide(nextSide));

						} else if(nextSide.getAxis() != currentSide.getAxis()) {

							//Allow movement around corners, but insert new point with transitional side inbetween

							PathFinderNode intermediary;
							if(Math.abs(currentSide.getStepX()) == adx && Math.abs(currentSide.getStepY()) == ady && Math.abs(currentSide.getStepZ()) == adz) {
								intermediary = new PathFinderNode(current, current.pathPoint.assignPathSide(nextSide));
							} else {
								intermediary = new PathFinderNode(current, next.assignPathSide(currentSide));
							}

							nextPathFinderNode = new PathFinderNode(intermediary, intermediary.depth, next.assignPathSide(nextSide));

						}
					} else if(d == 2) {
						//Diagonal

						int currentSidePlaneMatch = (currentSide.getStepX() == -dx ? 1 : 0) + (currentSide.getStepY() == -dy ? 1 : 0) + (currentSide.getStepZ() == -dz ? 1 : 0);

						if(currentSide == nextSide && currentSidePlaneMatch == 0) {

							//Allow diagonal movement, no need to insert transitional side since the diagonal's plane's normal is the same as the path's side
							nextPathFinderNode = new PathFinderNode(current, next.assignPathSide(nextSide));

						} else {
							//Allow movement, but insert new point with transitional side inbetween

							PathFinderNode intermediary = null;
							if(currentSidePlaneMatch == 2) {
								for(Direction intermediarySide : getPathableSidesWithFallback(current.pathPoint)) {
									if(intermediarySide != currentSide && (intermediarySide.getStepX() == dx ? 1 : 0) + (intermediarySide.getStepY() == dy ? 1 : 0) + (intermediarySide.getStepZ() == dz ? 1 : 0) == 2) {
										intermediary = new PathFinderNode(current, current.pathPoint.assignPathSide(intermediarySide));
										break;
									}
								}
							} else {
								for(Direction intermediarySide : getPathableSidesWithFallback(next)) {
									if(intermediarySide != nextSide && (intermediarySide.getStepX() == -dx ? 1 : 0) + (intermediarySide.getStepY() == -dy ? 1 : 0) + (intermediarySide.getStepZ() == -dz ? 1 : 0) == 2) {
										intermediary = new PathFinderNode(current, next.assignPathSide(intermediarySide));
										break;
									}
								}
							}

							if(intermediary != null) {
								nextPathFinderNode = new PathFinderNode(intermediary, intermediary.depth, next.assignPathSide(nextSide));
							} else {
								nextPathFinderNode = new PathFinderNode(current, next.assignPathSide(nextSide));
							}
						}
					}
				}

				if(nextPathFinderNode != null && checkedSet.add(nextPathFinderNode)) {
					queue.addLast(nextPathFinderNode);
				}
			}
		}

		return end;
	}

	private DirectionalPathPoint ensureDirectional(Node point) {
		if(point instanceof DirectionalPathPoint) {
			return (DirectionalPathPoint) point;
		} else {
			return new DirectionalPathPoint(point);
		}
	}
}
