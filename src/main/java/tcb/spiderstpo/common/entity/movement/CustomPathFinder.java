package tcb.spiderstpo.common.entity.movement;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.minecraft.entity.MobEntity;
import net.minecraft.pathfinding.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Region;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CustomPathFinder extends PathFinder {
	public static final Heuristic DEFAULT_HEURISTIC = (start, end, isTargetHeuristic) -> start.func_224757_c(end); //distanceManhattan
	private final PathHeap path = new PathHeap();
	private final PathPoint[] pathOptions = new PathPoint[32];
	private final NodeProcessor nodeProcessor;
	private int maxExpansions = 200;
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
		Map<FlaggedPathPoint, BlockPos> map = checkpoints.stream().collect(Collectors.toMap((p_224782_1_) -> {
			return this.nodeProcessor.func_224768_a(p_224782_1_.getX(), p_224782_1_.getY(), p_224782_1_.getZ());
		}, Function.identity()));
		Path path = this.findPath(pathpoint, map, maxDistance, checkpointRange, maxExpansionsMultiplier);
		this.nodeProcessor.postProcess();
		return path;
	}

	@Nullable
	private Path findPath(PathPoint start, Map<FlaggedPathPoint, BlockPos> mappedCheckpoints, float maxDistance, int checkpointRange, float maxExpansionsMultiplier) {
		Set<FlaggedPathPoint> checkpoints = mappedCheckpoints.keySet();
		start.totalPathDistance = 0.0F;
		start.distanceToNext = this.func_224776_a(start, checkpoints);
		start.distanceToTarget = start.distanceToNext;
		this.path.clearPath();
		this.path.addPoint(start);
		int i = 0;
		Set<FlaggedPathPoint> crossedCheckpoints = Sets.newHashSetWithExpectedSize(checkpoints.size());
		int j = (int) (this.maxExpansions * maxExpansionsMultiplier);

		while (!this.path.isPathEmpty()) {
			++i;
			if(i >= j) {
				break;
			}

			PathPoint pathpoint = this.path.dequeue();
			pathpoint.visited = true;

			for(FlaggedPathPoint flaggedpathpoint : checkpoints) {
				if(pathpoint.func_224757_c(flaggedpathpoint) <= (float) checkpointRange) {
					flaggedpathpoint.func_224764_e();
					crossedCheckpoints.add(flaggedpathpoint);
				}
			}

			if(!crossedCheckpoints.isEmpty()) {
				break;
			}

			if(!(pathpoint.distanceTo(start) >= maxDistance)) {
				int k = this.nodeProcessor.func_222859_a(this.pathOptions, pathpoint);

				for(int l = 0; l < k; ++l) {
					PathPoint pathpoint1 = this.pathOptions[l];
					float f = pathpoint.distanceTo(pathpoint1);
					pathpoint1.field_222861_j = pathpoint.field_222861_j + f;
					float f1 = pathpoint.totalPathDistance + f + pathpoint1.costMalus;
					if(pathpoint1.field_222861_j < maxDistance && (!pathpoint1.isAssigned() || f1 < pathpoint1.totalPathDistance)) {
						pathpoint1.previous = pathpoint;
						pathpoint1.totalPathDistance = f1;
						pathpoint1.distanceToNext = this.func_224776_a(pathpoint1, checkpoints) * 1.5F;
						if(pathpoint1.isAssigned()) {
							this.path.changeDistance(pathpoint1, pathpoint1.totalPathDistance + pathpoint1.distanceToNext);
						} else {
							pathpoint1.distanceToTarget = pathpoint1.totalPathDistance + pathpoint1.distanceToNext;
							this.path.addPoint(pathpoint1);
						}
					}
				}
			}
		}

		Optional<Path> path;
		if(!crossedCheckpoints.isEmpty()) {
			path = crossedCheckpoints.stream()
					.map((mappedCheckpoint) -> {
						return this.func_224780_a(mappedCheckpoint.func_224763_d(), mappedCheckpoints.get(mappedCheckpoint), true);
					})
					.min(Comparator.comparingInt(Path::getCurrentPathLength));
		} else {
			path = checkpoints.stream()
					.map((mappedCheckpoint) -> {
						return this.func_224780_a(mappedCheckpoint.func_224763_d(), mappedCheckpoints.get(mappedCheckpoint), false);
					})
					.min(Comparator.comparingDouble(Path::func_224769_l).thenComparingInt(Path::getCurrentPathLength));
		}
		return !path.isPresent() ? null : path.get();
	}

	//TODO Re-implement custom heuristics

	private float func_224776_a(PathPoint p_224776_1_, Set<FlaggedPathPoint> p_224776_2_) {
		float f = Float.MAX_VALUE;

		for(FlaggedPathPoint flaggedpathpoint : p_224776_2_) {
			float f1 = p_224776_1_.distanceTo(flaggedpathpoint);
			flaggedpathpoint.func_224761_a(f1, p_224776_1_);
			f = Math.min(f1, f);
		}

		return f;
	}

	private Path func_224780_a(PathPoint start, BlockPos target, boolean isCheckpoint) {
		List<PathPoint> points = Lists.newArrayList();
		PathPoint pathpoint = start;
		points.add(0, start);

		while (pathpoint.previous != null) {
			pathpoint = pathpoint.previous;
			points.add(0, pathpoint);
		}

		return new Path(points, target, isCheckpoint);
	}

	public interface Heuristic {
		float compute(PathPoint start, PathPoint end, boolean isTargetHeuristic);
	}

	/*@Override
	@Nullable
	public Path findPath(IBlockAccess worldIn, MobEntity MobEntityIn, Entity targetEntity, float maxDistance) {
		return this.findPath(worldIn, MobEntityIn, targetEntity.posX, targetEntity.getEntityBoundingBox().minY, targetEntity.posZ, maxDistance);
	}
	
	@Override
	@Nullable
	public Path findPath(IBlockAccess worldIn, MobEntity MobEntityIn, BlockPos targetPos, float maxDistance) {
		return this.findPath(worldIn, MobEntityIn, (double)((float)targetPos.getX() + 0.5F), (double)((float)targetPos.getY() + 0.5F), (double)((float)targetPos.getZ() + 0.5F), maxDistance);
	}
	
	@Nullable
	private Path findPath(IBlockAccess worldIn, MobEntity MobEntityIn, double x, double y, double z, float maxDistance) {
		this.path.clearPath();
		this.nodeProcessor.init(worldIn, MobEntityIn);
		PathPoint startPathPoint = this.nodeProcessor.getStart();
		PathPoint targetPathPoint = this.nodeProcessor.getPathPointToCoords(x, y, z);
		Path path = this.findPath(startPathPoint, targetPathPoint, maxDistance);
		this.nodeProcessor.postProcess();
		return path;
	}
	
	@Nullable
	private Path findPath(PathPoint startPathPoint, PathPoint targetPathPoint, float maxDistance) {
		startPathPoint.totalPathDistance = 0.0F;
		startPathPoint.distanceToNext = this.heuristic.compute(startPathPoint, targetPathPoint, true);
		startPathPoint.distanceToTarget = startPathPoint.distanceToNext;
		this.path.clearPath();
		this.path.addPoint(startPathPoint);
		PathPoint finalPathPoint = startPathPoint;
	
		int expansions = 0;
	
		while(!this.path.isPathEmpty()) {
			++expansions;
	
			if(expansions >= this.maxExpansions) {
				break;
			}
	
			PathPoint openPathPoint = this.path.dequeue();
	
			if(openPathPoint.equals(targetPathPoint)) {
				finalPathPoint = targetPathPoint;
				break;
			}
	
			if(this.heuristic.compute(openPathPoint, targetPathPoint, true) < this.heuristic.compute(finalPathPoint, targetPathPoint, true)) {
				finalPathPoint = openPathPoint;
			}
	
			openPathPoint.visited = true;
			int numOptions = this.nodeProcessor.findPathOptions(this.pathOptions, openPathPoint, targetPathPoint, maxDistance);
	
			for(int i = 0; i < numOptions; ++i) {
				PathPoint successorPathPoint = this.pathOptions[i];
	
				float costHeuristic = this.heuristic.compute(openPathPoint, successorPathPoint, false);
	
				//distanceFromOrigin corresponds to the total path cost of the evaluation function
				successorPathPoint.distanceFromOrigin = openPathPoint.distanceFromOrigin + costHeuristic;
				successorPathPoint.cost = costHeuristic + successorPathPoint.costMalus;
	
				float totalSuccessorPathCost = openPathPoint.totalPathDistance + successorPathPoint.cost;
	
				if(successorPathPoint.distanceFromOrigin < maxDistance && (!successorPathPoint.isAssigned() || totalSuccessorPathCost < successorPathPoint.totalPathDistance)) {
					successorPathPoint.previous = openPathPoint;
					successorPathPoint.totalPathDistance = totalSuccessorPathCost;
	
					//distanceToNext corresponds to the heuristic part of the evaluation function
					successorPathPoint.distanceToNext = this.heuristic.compute(successorPathPoint, targetPathPoint, true) + successorPathPoint.costMalus;
	
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
	
		if(finalPathPoint == startPathPoint) {
			return null;
		} else {
			Path path = this.createPath(startPathPoint, finalPathPoint);
			return path;
		}
	}
	
	private Path createPath(PathPoint start, PathPoint end) {
		int pathLength = 1;
	
		for(PathPoint pathpoint = end; pathpoint.previous != null; pathpoint = pathpoint.previous) {
			++pathLength;
		}
	
		PathPoint[] path = new PathPoint[pathLength];
		PathPoint currentPathPoint = end;
		--pathLength;
	
		for(path[pathLength] = end; currentPathPoint.previous != null; path[pathLength] = currentPathPoint) {
			currentPathPoint = currentPathPoint.previous;
			--pathLength;
		}
	
		return new Path(path);
	}*/
}
