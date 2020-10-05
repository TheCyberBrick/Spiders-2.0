package tcb.spiderstpo.common.entity.movement;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockState;
import net.minecraft.dispenser.IPosition;
import net.minecraft.entity.Entity;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.pathfinding.PathType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;

import javax.annotation.Nullable;

public class AdvancedClimberPathNavigator<T extends AbstractClimberEntity> extends AdvancedGroundPathNavigator<T> {
	protected final AbstractClimberEntity climber;

	protected Direction verticalFacing = Direction.DOWN;

	protected boolean findDirectPathPoints = false;

	public AdvancedClimberPathNavigator(T entity, World worldIn, boolean checkObstructions, boolean canPathWalls, boolean canPathCeiling) {
		super(entity, worldIn, checkObstructions);

		this.climber = entity;

		if(this.nodeProcessor instanceof AdvancedWalkNodeProcessor) {
			@SuppressWarnings("unchecked")
			AdvancedWalkNodeProcessor<T> processor = (AdvancedWalkNodeProcessor<T>) this.nodeProcessor;
			processor.setStartPathOnGround(false);
			processor.setCanPathWalls(canPathWalls);
			processor.setCanPathCeiling(canPathCeiling);
		}
	}

	protected static double swizzle(Vec3d vec, Direction.Axis axis) {
		switch(axis) {
		case X:
			return vec.x;
		case Y:
			return vec.y;
		case Z:
			return vec.z;
		}
		return 0;
	}

	protected static int swizzle(int x, int y, int z, Direction.Axis axis) {
		switch(axis) {
		case X:
			return x;
		case Y:
			return y;
		case Z:
			return z;
		}
		return 0;
	}

	protected static int unswizzle(int x, int y, int z, Direction.Axis ax, Direction.Axis ay, Direction.Axis az, Direction.Axis axis) {
		Direction.Axis unswizzle;
		if(axis == ax) {
			unswizzle = Direction.Axis.X;
		} else if(axis == ay) {
			unswizzle = Direction.Axis.Y;
		} else {
			unswizzle = Direction.Axis.Z;
		}
		return swizzle(x, y, z, unswizzle);
	}

	private static boolean func_237488_a_(Vec3d object, IPosition p_237488_1_, double p_237488_2_) {
		return object.squareDistanceTo(p_237488_1_.getX(), p_237488_1_.getY(), p_237488_1_.getZ()) < p_237488_2_ * p_237488_2_;
	}

	@Override
	protected boolean canNavigate() {
		return !this.isInLiquid() || this.getCanSwim() && this.isInLiquid() || this.entity.getRidingEntity() == null;
	}

	@Override
	protected Vec3d getEntityPosition() {
		return this.entity.getPositionVec().add(0, this.entity.getHeight() / 2.0f, 0);
	}

	@Override
	@Nullable
	public Path getPathToPos(BlockPos pos, int checkpointRange) {
		return this.func_225464_a(ImmutableSet.of(pos), 8, false, checkpointRange);
	}

	@Override
	@Nullable
	public Path getPathToEntity(Entity entityIn, int checkpointRange) {
		return this.func_225464_a(ImmutableSet.of(entityIn.getPosition()), 16, true, checkpointRange);
	}

	@Override
	protected void pathFollow() {
		Vec3d pos = this.getEntityPosition();

		this.maxDistanceToWaypoint = this.entity.getWidth() > 0.75F ? this.entity.getWidth() / 2.0F : 0.75F - this.entity.getWidth() / 2.0F;
		float maxDistanceToWaypointY = Math.max(1 /*required for e.g. slabs*/, this.entity.getHeight() > 0.75F ? this.entity.getHeight() / 2.0F : 0.75F - this.entity.getHeight() / 2.0F);
		Vec3d currentTarget = this.currentPath.getCurrentPos(); //getCurrentPos

		/*float offsetX = MathHelper.ceil(this.entity.getWidth()) - this.entity.getWidth();
		float offsetY = MathHelper.ceil(this.entity.getHeight()) - this.entity.getHeight();
		float offsetZ = offsetX;
		
		double dx = Math.abs(currentTarget.x - (this.entity.getBoundingBox().minX + offsetX));
		double dy = Math.abs(currentTarget.y - (this.entity.getBoundingBox().minY + offsetY));
		double dz = Math.abs(currentTarget.z - (this.entity.getBoundingBox().minZ + offsetZ));*/

		double dx = Math.abs(currentTarget.x + (int) (this.entity.getWidth() + 1.0f) * 0.5f - this.entity.getPosX());
		double dy = Math.abs(currentTarget.y - this.entity.getPosY());
		double dz = Math.abs(currentTarget.z + (int) (this.entity.getWidth() + 1.0f) * 0.5f - this.entity.getPosZ());

		boolean isWaypointInReach = dx < this.maxDistanceToWaypoint && dy < maxDistanceToWaypointY && dz < this.maxDistanceToWaypoint;

		int sizeX = MathHelper.ceil(this.entity.getWidth());
		int sizeY = MathHelper.ceil(this.entity.getHeight());
		int sizeZ = sizeX;

		AbstractClimberEntity.Orientation orientation = this.climber.getOrientation(1);
		Vec3d upVector = orientation.getDirection(this.climber.rotationYaw, -90);

		this.verticalFacing = Direction.getFacingFromVector((float) upVector.x, (float) upVector.y, (float) upVector.z);

		if(isWaypointInReach || (canPass(this.currentPath.getFinalPathPoint().nodeType) && this.isNextTargetInLine(pos, sizeX, sizeY, sizeZ))) {
			this.currentPath.setCurrentPathIndex(this.currentPath.getCurrentPathIndex() + 1);
		}

		if(this.findDirectPathPoints) {
			Direction.Axis verticalAxis = this.verticalFacing.getAxis();

			int firstDifferentHeightPoint = this.currentPath.getCurrentPathLength();

			switch(verticalAxis) {
			case X:
				for(int i = this.currentPath.getCurrentPathIndex(); i < this.currentPath.getCurrentPathLength(); ++i) {
					if(this.currentPath.getPathPointFromIndex(i).x != Math.floor(pos.x)) {
						firstDifferentHeightPoint = i;
						break;
					}
				}
				break;
			case Y:
				for(int i = this.currentPath.getCurrentPathIndex(); i < this.currentPath.getCurrentPathLength(); ++i) {
					if(this.currentPath.getPathPointFromIndex(i).y != Math.floor(pos.y)) {
						firstDifferentHeightPoint = i;
						break;
					}
				}
				break;
			case Z:
				for(int i = this.currentPath.getCurrentPathIndex(); i < this.currentPath.getCurrentPathLength(); ++i) {
					if(this.currentPath.getPathPointFromIndex(i).z != Math.floor(pos.z)) {
						firstDifferentHeightPoint = i;
						break;
					}
				}
				break;
			}

			for(int i = firstDifferentHeightPoint - 1; i >= this.currentPath.getCurrentPathIndex(); --i) {
				if(this.isDirectPathBetweenPoints(pos, this.currentPath.getVectorFromIndex(this.entity, i), sizeX, sizeY, sizeZ)) {
					this.currentPath.setCurrentPathIndex(i);
					break;
				}
			}
		}

		this.checkForStuck(pos);
	}

	private boolean isNextTargetInLine(Vec3d pos, int sizeX, int sizeY, int sizeZ) {
		if(this.currentPath.getCurrentPathIndex() + 1 >= this.currentPath.getCurrentPathLength()) {
			return false;
		} else {
			Vec3d currentTarget = new Vec3d(this.currentPath.getTarget());

			if(!func_237488_a_(pos, currentTarget, 2.0D)) {
				return false;
			} else {
				PathPoint pathPoint = this.currentPath.getPathPointFromIndex(this.currentPath.getCurrentPathIndex() + 1);
				Vec3d nextTarget = new Vec3d(pathPoint.x + .5, pathPoint.y, pathPoint.z + .5);
				Vec3d targetDir = nextTarget.subtract(currentTarget);
				Vec3d currentDir = pos.subtract(currentTarget);

				if(targetDir.dotProduct(currentDir) > 0.0D) {
					Direction.Axis ax, ay, az;
					boolean invertY;

					switch(this.verticalFacing.getAxis()) {
					case X:
						ax = Direction.Axis.Z;
						ay = Direction.Axis.X;
						az = Direction.Axis.Y;
						invertY = this.verticalFacing.getXOffset() < 0;
						break;
					default:
					case Y:
						ax = Direction.Axis.X;
						ay = Direction.Axis.Y;
						az = Direction.Axis.Z;
						invertY = this.verticalFacing.getYOffset() < 0;
						break;
					case Z:
						ax = Direction.Axis.Y;
						ay = Direction.Axis.Z;
						az = Direction.Axis.X;
						invertY = this.verticalFacing.getZOffset() < 0;
						break;
					}

					//Make sure that the mob can stand at the next point in the same orientation it currently has
					return this.isSafeToStandAt(MathHelper.floor(nextTarget.x), MathHelper.floor(nextTarget.y), MathHelper.floor(nextTarget.z), sizeX, sizeY, sizeZ, currentTarget, 0, 0, -1, ax, ay, az, invertY);
				}

				return false;
			}
		}
	}

	@Override
	protected boolean isDirectPathBetweenPoints(Vec3d start, Vec3d end, int sizeX, int sizeY, int sizeZ) {
		switch(this.verticalFacing.getAxis()) {
		case X:
			return this.isDirectPathBetweenPoints(start, end, sizeX, sizeY, sizeZ, Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Y, 0.0D, this.verticalFacing.getXOffset() < 0);
		case Y:
			return this.isDirectPathBetweenPoints(start, end, sizeX, sizeY, sizeZ, Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z, 0.0D, this.verticalFacing.getYOffset() < 0);
		case Z:
			return this.isDirectPathBetweenPoints(start, end, sizeX, sizeY, sizeZ, Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X, 0.0D, this.verticalFacing.getZOffset() < 0);
		}
		return false;
	}

	protected boolean isDirectPathBetweenPoints(Vec3d start, Vec3d end, int sizeX, int sizeY, int sizeZ, Direction.Axis ax, Direction.Axis ay, Direction.Axis az, double minDotProduct, boolean invertY) {
		int bx = MathHelper.floor(swizzle(start, ax));
		int bz = MathHelper.floor(swizzle(start, az));
		double dx = swizzle(end, ax) - swizzle(start, ax);
		double dz = swizzle(end, az) - swizzle(start, az);
		double dSq = dx * dx + dz * dz;

		int by = (int) swizzle(start, ay);

		int sizeX2 = swizzle(sizeX, sizeY, sizeZ, ax);
		int sizeY2 = swizzle(sizeX, sizeY, sizeZ, ay);
		int sizeZ2 = swizzle(sizeX, sizeY, sizeZ, az);

		if(dSq < 1.0E-8D) {
			return false;
		} else {
			double d3 = 1.0D / Math.sqrt(dSq);
			dx = dx * d3;
			dz = dz * d3;
			sizeX2 = sizeX2 + 2;
			sizeZ2 = sizeZ2 + 2;

			if(!this.isSafeToStandAt(unswizzle(bx, by, bz, ax, ay, az, Direction.Axis.X), unswizzle(bx, by, bz, ax, ay, az, Direction.Axis.Y), unswizzle(bx, by, bz, ax, ay, az, Direction.Axis.Z), unswizzle(sizeX2, sizeY2, sizeZ2, ax, ay, az, Direction.Axis.X), unswizzle(sizeX2, sizeY2, sizeZ2, ax, ay, az, Direction.Axis.Y), unswizzle(sizeX2, sizeY2, sizeZ2, ax, ay, az, Direction.Axis.Z), start, dx, dz, minDotProduct, ax, ay, az, invertY)) {
				return false;
			} else {
				sizeX2 = sizeX2 - 2;
				sizeZ2 = sizeZ2 - 2;
				double stepX = 1.0D / Math.abs(dx);
				double stepZ = 1.0D / Math.abs(dz);
				double relX = (double) bx - swizzle(start, ax);
				double relZ = (double) bz - swizzle(start, az);

				if(dx >= 0.0D) {
					++relX;
				}

				if(dz >= 0.0D) {
					++relZ;
				}

				relX = relX / dx;
				relZ = relZ / dz;
				int dirX = dx < 0.0D ? -1 : 1;
				int dirZ = dz < 0.0D ? -1 : 1;
				int ex = MathHelper.floor(swizzle(end, ax));
				int ez = MathHelper.floor(swizzle(end, az));
				int offsetX = ex - bx;
				int offsetZ = ez - bz;

				while (offsetX * dirX > 0 || offsetZ * dirZ > 0) {
					if(relX < relZ) {
						relX += stepX;
						bx += dirX;
						offsetX = ex - bx;
					} else {
						relZ += stepZ;
						bz += dirZ;
						offsetZ = ez - bz;
					}

					if(!this.isSafeToStandAt(unswizzle(bx, by, bz, ax, ay, az, Direction.Axis.X), unswizzle(bx, by, bz, ax, ay, az, Direction.Axis.Y), unswizzle(bx, by, bz, ax, ay, az, Direction.Axis.Z), unswizzle(sizeX2, sizeY2, sizeZ2, ax, ay, az, Direction.Axis.X), unswizzle(sizeX2, sizeY2, sizeZ2, ax, ay, az, Direction.Axis.Y), unswizzle(sizeX2, sizeY2, sizeZ2, ax, ay, az, Direction.Axis.Z), start, dx, dz, minDotProduct, ax, ay, az, invertY)) {
						return false;
					}
				}

				return true;
			}
		}
	}

	protected boolean isSafeToStandAt(int x, int y, int z, int sizeX, int sizeY, int sizeZ, Vec3d start, double dx, double dz, double minDotProduct, Direction.Axis ax, Direction.Axis ay, Direction.Axis az, boolean invertY) {
		int sizeX2 = swizzle(sizeX, sizeY, sizeZ, ax);
		int sizeZ2 = swizzle(sizeX, sizeY, sizeZ, az);

		int bx = swizzle(x, y, z, ax) - sizeX2 / 2;
		int bz = swizzle(x, y, z, az) - sizeZ2 / 2;

		int by = swizzle(x, y, z, ay);

		if(!this.isPositionClear(unswizzle(bx, y, bz, ax, ay, az, Direction.Axis.X), unswizzle(bx, y, bz, ax, ay, az, Direction.Axis.Y), unswizzle(bx, y, bz, ax, ay, az, Direction.Axis.Z), sizeX, sizeY, sizeZ, start, dx, dz, minDotProduct, ax, ay, az)) {
			return false;
		} else {
			for(int obx = bx; obx < bx + sizeX2; ++obx) {
				for(int obz = bz; obz < bz + sizeZ2; ++obz) {
					double offsetX = (double) obx + 0.5D - swizzle(start, ax);
					double offsetZ = (double) obz + 0.5D - swizzle(start, az);

					if(offsetX * dx + offsetZ * dz >= minDotProduct) {
						PathNodeType nodeTypeBelow = this.nodeProcessor.getPathNodeType(this.world, unswizzle(obx, by + (invertY ? 1 : -1), obz, ax, ay, az, Direction.Axis.X), unswizzle(obx, by + (invertY ? 1 : -1), obz, ax, ay, az, Direction.Axis.Y), unswizzle(obx, by + (invertY ? 1 : -1), obz, ax, ay, az, Direction.Axis.Z), this.entity, sizeX, sizeY, sizeZ, true, true);

						if(nodeTypeBelow == PathNodeType.WATER) {
							return false;
						}

						if(nodeTypeBelow == PathNodeType.LAVA) {
							return false;
						}

						if(nodeTypeBelow == PathNodeType.OPEN) {
							return false;
						}

						PathNodeType nodeType = this.nodeProcessor.getPathNodeType(this.world, unswizzle(obx, by, obz, ax, ay, az, Direction.Axis.X), unswizzle(obx, by, obz, ax, ay, az, Direction.Axis.Y), unswizzle(obx, by, obz, ax, ay, az, Direction.Axis.Z), this.entity, sizeX, sizeY, sizeZ, true, true);
						float f = this.entity.getPathPriority(nodeType);

						if(f < 0.0F || f >= 8.0F) {
							return false;
						}

						if(nodeType == PathNodeType.DAMAGE_FIRE || nodeType == PathNodeType.DANGER_FIRE || nodeType == PathNodeType.DAMAGE_OTHER) {
							return false;
						}
					}
				}
			}

			return true;
		}
	}

	protected boolean isPositionClear(int x, int y, int z, int sizeX, int sizeY, int sizeZ, Vec3d start, double dx, double dz, double minDotProduct, Direction.Axis ax, Direction.Axis ay, Direction.Axis az) {
		for(BlockPos pos : BlockPos.getAllInBoxMutable(new BlockPos(x, y, z), new BlockPos(x + sizeX - 1, y + sizeY - 1, z + sizeZ - 1))) {
			double offsetX = swizzle(pos.getX(), pos.getY(), pos.getZ(), ax) + 0.5D - swizzle(start, ax);
			double pffsetZ = swizzle(pos.getX(), pos.getY(), pos.getZ(), az) + 0.5D - swizzle(start, az);

			if(offsetX * dx + pffsetZ * dz >= minDotProduct) {
				BlockState state = this.world.getBlockState(pos);

				if(!state.allowsMovement(this.world, pos, PathType.LAND)) {
					return false;
				}
			}
		}

		return true;
	}

	protected boolean canPass(PathNodeType nodeType) {
		return nodeType != PathNodeType.DANGER_FIRE && nodeType != PathNodeType.DANGER_CACTUS && nodeType != PathNodeType.DANGER_OTHER;
	}
}
