package tcb.spiderstpo.common.entity.movement;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.entity.MobEntity;
import net.minecraft.pathfinding.GroundPathNavigator;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

public class AdvancedGroundPathNavigator<T extends MobEntity & IAdvancedPathFindingEntity> extends GroundPathNavigator {
	protected CustomPathFinder pathFinder;
	protected long lastTimeUpdated;
	protected BlockPos targetPos;

	protected final T obstructionAwareEntity;
	protected final boolean checkObstructions;

	protected int stuckCheckTicks = 0;

	protected int checkpointRange;

	public AdvancedGroundPathNavigator(T entity, World worldIn) {
		this(entity, worldIn, true);
	}

	@SuppressWarnings("unchecked")
	public AdvancedGroundPathNavigator(T entity, World worldIn, boolean checkObstructions) {
		super(entity, worldIn);
		this.obstructionAwareEntity = entity;
		this.checkObstructions = checkObstructions;

		if(this.nodeProcessor instanceof AdvancedWalkNodeProcessor) {
			AdvancedWalkNodeProcessor<T> processor = (AdvancedWalkNodeProcessor<T>) this.nodeProcessor;
			processor.setObstructionAwareEntity(entity);
			processor.setCheckObstructions(checkObstructions);
		}
	}

	public CustomPathFinder getAssignedPathFinder() {
		return this.pathFinder;
	}

	@Override
	protected final PathFinder getPathFinder(int maxExpansions) {
		this.pathFinder = this.createPathFinder(maxExpansions);
		this.nodeProcessor = this.pathFinder.getNodeProcessor();
		return this.pathFinder;
	}

	protected CustomPathFinder createPathFinder(int maxExpansions) {
		AdvancedWalkNodeProcessor<T> nodeProcessor = new AdvancedWalkNodeProcessor<>();
		nodeProcessor.setCanEnterDoors(true);
		return new CustomPathFinder(nodeProcessor, maxExpansions);
	}

	@Nullable
	@Override
	protected Path func_225464_a(Set<BlockPos> waypoints, int padding, boolean startAbove, int checkpointRange) {
		//Offset waypoints according to entity's size so that the lower AABB corner is at the offset waypoint and center is at the original waypoint
		Set<BlockPos> adjustedWaypoints = new HashSet<>();
		for(BlockPos pos : waypoints) {
			adjustedWaypoints.add(pos.add(-MathHelper.ceil(this.entity.getWidth()) + 1, -MathHelper.ceil(this.entity.getHeight()) + 1, -MathHelper.ceil(this.entity.getWidth()) + 1));
		}

		Path path = super.func_225464_a(adjustedWaypoints, padding, startAbove, checkpointRange);

		if(path != null && path.getTarget() != null) {
			this.checkpointRange = checkpointRange;
		}

		return path;
	}

	@Override
	public void updatePath() {
		if(this.world.getGameTime() - this.lastTimeUpdated > 20L) {
			if(this.targetPos != null) {
				this.currentPath = null;
				this.currentPath = this.getPathToPos(this.targetPos, this.checkpointRange);
				this.lastTimeUpdated = this.world.getGameTime();
				this.tryUpdatePath = false;
			}
		} else {
			this.tryUpdatePath = true;
		}
	}

	@Override
	protected void checkForStuck(Vector3d entityPos) {
		super.checkForStuck(entityPos);

		if(this.checkObstructions && this.currentPath != null && !this.currentPath.isFinished()) {
			Vector3d target = this.currentPath.getVectorFromIndex(this.obstructionAwareEntity, Math.min(this.currentPath.getCurrentPathLength() - 1, this.currentPath.getCurrentPathIndex() + 0));
			Vector3d diff = target.subtract(entityPos);

			int axis = 0;
			double maxDiff = 0;
			for(int i = 0; i < 3; i++) {
				double d;

				switch(i) {
				default:
				case 0:
					d = Math.abs(diff.x);
					break;
				case 1:
					d = Math.abs(diff.y);
					break;
				case 2:
					d = Math.abs(diff.z);
					break;
				}

				if(d > maxDiff) {
					axis = i;
					maxDiff = d;
				}
			}

			int height = MathHelper.floor(this.obstructionAwareEntity.getHeight() + 1.0F);

			int ceilHalfWidth = MathHelper.ceil(this.obstructionAwareEntity.getWidth() / 2.0f + 0.05F);

			Vector3d checkPos;
			switch(axis) {
			default:
			case 0:
				checkPos = new Vector3d(entityPos.x + Math.signum(diff.x) * ceilHalfWidth, entityPos.y, target.z);
				break;
			case 1:
				checkPos = new Vector3d(entityPos.x, entityPos.y + (diff.y > 0 ? (height + 1) : -1), target.z);
				break;
			case 2:
				checkPos = new Vector3d(target.x, entityPos.y, entityPos.z + Math.signum(diff.z) * ceilHalfWidth);
				break;
			}

			Vector3d facingDiff = checkPos.subtract(entityPos.add(0, axis == 1 ? this.entity.getHeight() / 2 : 0, 0));
			Direction facing = Direction.getFacingFromVector((float)facingDiff.x, (float)facingDiff.y, (float)facingDiff.z);

			boolean blocked = false;

			AxisAlignedBB checkBox = this.obstructionAwareEntity.getBoundingBox().expand(Math.signum(diff.x) * 0.2D, Math.signum(diff.y) * 0.2D, Math.signum(diff.z) * 0.2D);

			loop: for(int yo = 0; yo < height; yo++) {
				for(int xzo = -ceilHalfWidth; xzo <= ceilHalfWidth; xzo++) {
					BlockPos pos = new BlockPos(checkPos.x + (axis != 0 ? xzo : 0), checkPos.y + (axis != 1 ? yo : 0), checkPos.z + (axis != 2 ? xzo : 0));

					BlockState state = this.obstructionAwareEntity.world.getBlockState(pos);

					PathNodeType nodeType = state.allowsMovement(this.obstructionAwareEntity.world, pos, PathType.LAND) ? PathNodeType.OPEN : PathNodeType.BLOCKED;

					if(nodeType == PathNodeType.BLOCKED) {
						VoxelShape collisionShape = state.getShape(this.obstructionAwareEntity.world, pos, ISelectionContext.forEntity(this.obstructionAwareEntity)).withOffset(pos.getX(), pos.getY(), pos.getZ());

						//TODO Use ILineConsumer
						if(collisionShape != null && collisionShape.toBoundingBoxList().stream().anyMatch(aabb -> aabb.intersects(checkBox))) {
							blocked = true;
							break loop;
						}
					}
				}
			}

			if(blocked) {
				this.stuckCheckTicks++;

				if(this.stuckCheckTicks > this.obstructionAwareEntity.getMaxStuckCheckTicks()) {
					this.obstructionAwareEntity.onPathingObstructed(facing);
					this.stuckCheckTicks = 0;
				}
			} else {
				this.stuckCheckTicks = Math.max(this.stuckCheckTicks - 2, 0);
			}
		} else {
			this.stuckCheckTicks = Math.max(this.stuckCheckTicks - 4, 0);
		}
	}
}