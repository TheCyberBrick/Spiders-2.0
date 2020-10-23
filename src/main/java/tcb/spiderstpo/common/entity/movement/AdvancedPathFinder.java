package tcb.spiderstpo.common.entity.movement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.pathfinding.NodeProcessor;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public class AdvancedPathFinder extends CustomPathFinder {
	public AdvancedPathFinder(NodeProcessor processor, int maxExpansions) {
		super(processor, maxExpansions);
	}

	private static class Node {
		private final Node previous;
		private final DirectionalPathPoint pathPoint;
		private final Direction side;
		private final int depth;

		private Node(@Nullable Node previous, DirectionalPathPoint pathPoint) {
			this.previous = previous;
			this.depth = previous != null ? previous.depth + 1 : 0;
			this.pathPoint = pathPoint;
			this.side = pathPoint.getPathSide();
		}

		private Node(@Nullable Node previous, int depth, DirectionalPathPoint pathPoint) {
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
			Node other = (Node) obj;
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

	@Override
	protected Path createPath(PathPoint _targetPoint, BlockPos target, boolean isTargetReached) {
		List<PathPoint> points = new ArrayList<>();

		//Backtrack path from target point back to entity
		this.backtrackPath(points, _targetPoint);

		//Retrace path with valid side transitions
		Node end = this.retraceSidedPath(points);

		if(end == null) {
			return new Path(Collections.emptyList(), target, isTargetReached);
		}

		points.clear();

		//Backtrack retraced path
		this.backtrackPath(points, end);

		return new Path(points, target, isTargetReached);
	}

	private void backtrackPath(List<PathPoint> points, PathPoint start) {
		PathPoint currentPathPoint = start;
		points.add(start);

		while(currentPathPoint.previous != null) {
			currentPathPoint = currentPathPoint.previous;
			points.add(currentPathPoint);
		}
	}

	private void backtrackPath(List<PathPoint> points, Node start) {
		Node currentNode = start;
		points.add(start.pathPoint);

		while(currentNode.previous != null) {
			currentNode = currentNode.previous;
			points.add(currentNode.pathPoint);
		}
	}

	private Node retraceSidedPath(List<PathPoint> points) {
		if(points.isEmpty()) {
			return null;
		}

		final Deque<Node> queue = new LinkedList<>();

		final DirectionalPathPoint targetPoint = this.ensureDirectional(points.get(0));

		for(Direction direction : targetPoint.getPathableSides()) {
			queue.add(new Node(null, targetPoint.assignPathSide(direction)));
		}

		Node end = null;

		final int maxExpansions = 200;
		final Set<Node> checkedSet = new HashSet<>();

		int expansions = 0;
		while(!queue.isEmpty()) {
			if(expansions++ > maxExpansions) {
				break;
			}

			Node current = queue.removeFirst();

			if(current.depth == points.size() - 1) {
				end = current;
				break;
			}

			DirectionalPathPoint next = this.ensureDirectional(points.get(current.depth + 1));

			for(Direction nextDir : next.getPathableSides()) {
				for(Direction currentDir : current.pathPoint.getPathableSides()) {
					Node node = null;

					if(nextDir == currentDir) {
						//Allow movement on the same side
						node = new Node(current, next.assignPathSide(nextDir));
					} else if(nextDir.getAxis() != currentDir.getAxis()) {
						if(Math.abs(next.x - current.pathPoint.x) + Math.abs(next.y - current.pathPoint.y) + Math.abs(next.z - current.pathPoint.z) == 1) {
							//Allow movement around corners, but insert new point at next position but with previous side
							Node intermediary = new Node(current, next.assignPathSide(currentDir));
							node = new Node(intermediary, intermediary.depth, next.assignPathSide(nextDir));
						} else {
							node = new Node(current, next.assignPathSide(nextDir));
						}
					}

					if(node != null && checkedSet.add(node)) {
						queue.addLast(node);
					}
				}
			}
		}

		return end;
	}

	private DirectionalPathPoint ensureDirectional(PathPoint point) {
		if(point instanceof DirectionalPathPoint) {
			return (DirectionalPathPoint) point;
		} else {
			return new DirectionalPathPoint(point);
		}
	}
}
