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

	public static final Heuristic DEFAULT_HEURISTIC = (start, end, isTargetHeuristic) -> start.func_224757_c(end); //distanceManhattan

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
	public Path func_227478_a_(Region region, MobEntity entity, Set<BlockPos> checkpoints, float maxDistance, int checkpointRange, float maxExpansionsMultiplier) {
		this.path.clearPath();

		this.nodeProcessor.func_225578_a_(region, entity);

		PathPoint pathpoint = this.nodeProcessor.getStart();

		//Create a checkpoint for each block pos in the checkpoints set
		Map<FlaggedPathPoint, BlockPos> checkpointsMap = checkpoints.stream().collect(Collectors.toMap((pos) -> {
			return this.nodeProcessor.func_224768_a(pos.getX(), pos.getY(), pos.getZ());
		}, Function.identity()));

		Path path = this.findPath(pathpoint, checkpointsMap, maxDistance, checkpointRange, maxExpansionsMultiplier);
		this.nodeProcessor.postProcess();

		return path;
	}

	//TODO Re-implement custom heuristics

	@Nullable
	private Path findPath(PathPoint start, Map<FlaggedPathPoint, BlockPos> checkpointsMap, float maxDistance, int checkpointRange, float maxExpansionsMultiplier) {
		Set<FlaggedPathPoint> checkpoints = checkpointsMap.keySet();

		start.totalPathDistance = 0.0F;
		start.distanceToNext = this.computeHeuristic(start, checkpoints);
		start.distanceToTarget = start.distanceToNext;

		this.path.clearPath();
		this.path.addPoint(start);

		Set<FlaggedPathPoint> reachedCheckpoints = Sets.newHashSetWithExpectedSize(checkpoints.size());

		int expansions = 0;
		int maxExpansions = (int) (this.maxExpansions * maxExpansionsMultiplier);

		while(!this.path.isPathEmpty() && ++expansions < maxExpansions) {
			PathPoint openPathPoint = this.path.dequeue();
			openPathPoint.visited = true;

			for(FlaggedPathPoint checkpoint : checkpoints) {
				if(openPathPoint.func_224757_c(checkpoint) <= checkpointRange) {
					checkpoint.func_224764_e();
					reachedCheckpoints.add(checkpoint);
				}
			}

			if(!reachedCheckpoints.isEmpty()) {
				break;
			}

			if(openPathPoint.distanceTo(start) < maxDistance) {
				int numOptions = this.nodeProcessor.func_222859_a(this.pathOptions, openPathPoint);

				for(int i = 0; i < numOptions; ++i) {
					PathPoint successorPathPoint = this.pathOptions[i];

					float costHeuristic = openPathPoint.distanceTo(successorPathPoint); //TODO Replace with cost heuristic

					//field_222861_j corresponds to the total path cost of the evaluation function
					successorPathPoint.field_222861_j = openPathPoint.field_222861_j + costHeuristic;

					float totalSuccessorPathCost = openPathPoint.totalPathDistance + costHeuristic + successorPathPoint.costMalus;

					if(successorPathPoint.field_222861_j < maxDistance && (!successorPathPoint.isAssigned() || totalSuccessorPathCost < successorPathPoint.totalPathDistance)) {
						successorPathPoint.previous = openPathPoint;
						successorPathPoint.totalPathDistance = totalSuccessorPathCost;

						//distanceToNext corresponds to the heuristic part of the evaluation function
						successorPathPoint.distanceToNext = this.computeHeuristic(successorPathPoint, checkpoints) * 1.0f; //TODO Vanilla's 1.5 multiplier is too greedy :( Move to custom heuristic stuff

						if(successorPathPoint.isAssigned()) {
							this.path.changeDistance(successorPathPoint, successorPathPoint.totalPathDistance + successorPathPoint.distanceToNext);
						} else {
							//distanceToTarget corresponds to the evaluation function, i.e. total path cost + heuristic
							successorPathPoint.distanceToTarget = successorPathPoint.totalPathDistance + successorPathPoint.distanceToNext;
							this.path.addPoint(successorPathPoint);
						}
					}
				}
			}
		}

		Optional<Path> path;

		if(!reachedCheckpoints.isEmpty()) {
			//Use shortest path towards next reached checkpoint
			path = reachedCheckpoints.stream().map((checkpoint) -> {
				return this.createPath(checkpoint.func_224763_d(), checkpointsMap.get(checkpoint), true);
			}).min(Comparator.comparingInt(Path::getCurrentPathLength));
		} else {
			//Use lowest cost path towards any checkpoint
			path = checkpoints.stream().map((checkpoint) -> {
				return this.createPath(checkpoint.func_224763_d(), checkpointsMap.get(checkpoint), false);
			}).min(Comparator.comparingDouble(Path::func_224769_l /*TODO Replace calculation with cost heuristic*/).thenComparingInt(Path::getCurrentPathLength));
		}

		return !path.isPresent() ? null : path.get();
	}

	private float computeHeuristic(PathPoint pathPoint, Set<FlaggedPathPoint> checkpoints) {
		float minDst = Float.MAX_VALUE;

		for(FlaggedPathPoint checkpoint : checkpoints) {
			float dst = pathPoint.distanceTo(checkpoint); //TODO Replace with target heuristic
			checkpoint.func_224761_a(dst, pathPoint);
			minDst = Math.min(dst, minDst);
		}

		return minDst;
	}

	protected Path createPath(PathPoint start, BlockPos target, boolean isTargetReached) {
		List<PathPoint> points = Lists.newArrayList();

		PathPoint currentPathPoint = start;
		points.add(0, start);

		while(currentPathPoint.previous != null) {
			currentPathPoint = currentPathPoint.previous;
			points.add(0, currentPathPoint);
		}

		return new Path(points, target, isTargetReached);
	}
}