package tcb.spiderstpo.common.entity.movement;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import net.minecraft.entity.MobEntity;
import net.minecraft.pathfinding.FlaggedPathPoint;
import net.minecraft.pathfinding.NodeProcessor;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathHeap;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Region;

public class CustomPathFinder extends PathFinder {
	private final PathHeap path = new PathHeap();
	private final PathPoint[] pathOptions = new PathPoint[32];
	private final NodeProcessor nodeProcessor;

	private int maxExpansions = 200;

	public static interface Heuristic {
		public float compute(PathPoint start, PathPoint end, boolean isTargetHeuristic);
	}

	public static final Heuristic DEFAULT_HEURISTIC = (start, end, isTargetHeuristic) -> start.distanceManhattan(end); //distanceManhattan

	private Heuristic heuristic = DEFAULT_HEURISTIC;

	public CustomPathFinder(NodeProcessor processor, int maxExpansions) {
		super(processor, maxExpansions);
		this.nodeProcessor = processor;
		this.maxExpansions = maxExpansions;
	}

	public NodeProcessor getNodeProcessor() {
		return this.nodeProcessor;
	}

	public CustomPathFinder setMaxExpansions(int expansions) {
		this.maxExpansions = expansions;
		return this;
	}

	public CustomPathFinder setHeuristic(Heuristic heuristic) {
		this.heuristic = heuristic;
		return this;
	}

	@Nullable
	@Override
	public Path findPath(Region region, MobEntity entity, Set<BlockPos> checkpoints, float maxDistance, int checkpointRange, float maxExpansionsMultiplier) {
		this.path.clear();

		this.nodeProcessor.prepare(region, entity);

		PathPoint pathpoint = this.nodeProcessor.getStart();

		//Create a checkpoint for each block pos in the checkpoints set
		Map<FlaggedPathPoint, BlockPos> checkpointsMap = checkpoints.stream().collect(Collectors.toMap((pos) -> {
			return this.nodeProcessor.getGoal(pos.getX(), pos.getY(), pos.getZ());
		}, Function.identity()));

		Path path = this.findPath(pathpoint, checkpointsMap, maxDistance, checkpointRange, maxExpansionsMultiplier);
		this.nodeProcessor.done();

		return path;
	}

	//TODO Re-implement custom heuristics

	@Nullable
	private Path findPath(PathPoint start, Map<FlaggedPathPoint, BlockPos> checkpointsMap, float maxDistance, int checkpointRange, float maxExpansionsMultiplier) {
		Set<FlaggedPathPoint> checkpoints = checkpointsMap.keySet();

		start.g = 0.0F;
		start.h = this.computeHeuristic(start, checkpoints);
		start.f = start.h;

		this.path.clear();
		this.path.insert(start);

		Set<FlaggedPathPoint> reachedCheckpoints = Sets.newHashSetWithExpectedSize(checkpoints.size());

		int expansions = 0;
		int maxExpansions = (int) (this.maxExpansions * maxExpansionsMultiplier);

		while(!this.path.isEmpty() && ++expansions < maxExpansions) {
			PathPoint openPathPoint = this.path.pop();
			openPathPoint.closed = true;

			for(FlaggedPathPoint checkpoint : checkpoints) {
				if(openPathPoint.distanceManhattan(checkpoint) <= checkpointRange) {
					checkpoint.setReached();
					reachedCheckpoints.add(checkpoint);
				}
			}

			if(!reachedCheckpoints.isEmpty()) {
				break;
			}

			if(openPathPoint.distanceTo(start) < maxDistance) {
				int numOptions = this.nodeProcessor.getNeighbors(this.pathOptions, openPathPoint);

				for(int i = 0; i < numOptions; ++i) {
					PathPoint successorPathPoint = this.pathOptions[i];

					float costHeuristic = openPathPoint.distanceTo(successorPathPoint); //TODO Replace with cost heuristic

					//walkedDistance corresponds to the total path cost of the evaluation function
					successorPathPoint.walkedDistance = openPathPoint.walkedDistance + costHeuristic;

					float totalSuccessorPathCost = openPathPoint.g + costHeuristic + successorPathPoint.costMalus;

					if(successorPathPoint.walkedDistance < maxDistance && (!successorPathPoint.inOpenSet() || totalSuccessorPathCost < successorPathPoint.g)) {
						successorPathPoint.cameFrom = openPathPoint;
						successorPathPoint.g = totalSuccessorPathCost;

						//distanceToNext corresponds to the heuristic part of the evaluation function
						successorPathPoint.h = this.computeHeuristic(successorPathPoint, checkpoints) * 1.0f; //TODO Vanilla's 1.5 multiplier is too greedy :( Move to custom heuristic stuff

						if(successorPathPoint.inOpenSet()) {
							this.path.changeCost(successorPathPoint, successorPathPoint.g + successorPathPoint.h);
						} else {
							//distanceToTarget corresponds to the evaluation function, i.e. total path cost + heuristic
							successorPathPoint.f = successorPathPoint.g + successorPathPoint.h;
							this.path.insert(successorPathPoint);
						}
					}
				}
			}
		}

		Optional<Path> path;

		if(!reachedCheckpoints.isEmpty()) {
			//Use shortest path towards next reached checkpoint
			path = reachedCheckpoints.stream().map((checkpoint) -> {
				return this.createPath(checkpoint.getBestNode(), checkpointsMap.get(checkpoint), true);
			}).min(Comparator.comparingInt(Path::getNodeCount));
		} else {
			//Use lowest cost path towards any checkpoint
			path = checkpoints.stream().map((checkpoint) -> {
				return this.createPath(checkpoint.getBestNode(), checkpointsMap.get(checkpoint), false);
			}).min(Comparator.comparingDouble(Path::getDistToTarget /*TODO Replace calculation with cost heuristic*/).thenComparingInt(Path::getNodeCount));
		}

		return !path.isPresent() ? null : path.get();
	}

	private float computeHeuristic(PathPoint pathPoint, Set<FlaggedPathPoint> checkpoints) {
		float minDst = Float.MAX_VALUE;

		for(FlaggedPathPoint checkpoint : checkpoints) {
			float dst = pathPoint.distanceTo(checkpoint); //TODO Replace with target heuristic
			checkpoint.updateBest(dst, pathPoint);
			minDst = Math.min(dst, minDst);
		}

		return minDst;
	}

	protected Path createPath(PathPoint start, BlockPos target, boolean isTargetReached) {
		List<PathPoint> points = Lists.newArrayList();

		PathPoint currentPathPoint = start;
		points.add(0, start);

		while(currentPathPoint.cameFrom != null) {
			currentPathPoint = currentPathPoint.cameFrom;
			points.add(0, currentPathPoint);
		}

		return new Path(points, target, isTargetReached);
	}
}
