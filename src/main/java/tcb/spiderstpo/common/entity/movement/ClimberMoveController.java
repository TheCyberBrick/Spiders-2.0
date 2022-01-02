package tcb.spiderstpo.common.entity.movement;

import javax.annotation.Nullable;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.JumpController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.pathfinding.NodeProcessor;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.Orientation;

public class ClimberMoveController<T extends MobEntity & IClimberEntity> extends MovementController {
	protected final IClimberEntity climber;

	@Nullable
	protected BlockPos block;

	@Nullable
	protected Direction side;

	public ClimberMoveController(T entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	public void setWantedPosition(double x, double y, double z, double speedIn) {
		this.setMoveTo(x, y, z, null, null, speedIn);
	}

	public void setMoveTo(double x, double y, double z, BlockPos block, Direction side, double speedIn) {
		super.setWantedPosition(x, y, z, speedIn);
		this.block = block;
		this.side = side;
	}

	@Override
	public void tick() {
		double speed = this.climber.getMovementSpeed() * this.speedModifier;

		if(this.operation == MovementController.Action.STRAFE) {
			this.operation = MovementController.Action.WAIT;

			float forward = this.strafeForwards;
			float strafe = this.strafeRight;
			float moveSpeed = MathHelper.sqrt(forward * forward + strafe * strafe);
			if(moveSpeed < 1.0F) {
				moveSpeed = 1.0F;
			}

			moveSpeed = (float)speed / moveSpeed;
			forward = forward * moveSpeed;
			strafe = strafe * moveSpeed;

			Orientation orientation = this.climber.getOrientation();

			Vector3d forwardVector = orientation.getGlobal(this.mob.yRot, 0);
			Vector3d strafeVector = orientation.getGlobal(this.mob.yRot - 90.0f, 0);

			if(!this.isWalkableAtOffset(forwardVector.x * forward + strafeVector.x * strafe, forwardVector.y * forward + strafeVector.y * strafe, forwardVector.z * forward + strafeVector.z * strafe)) {
				this.strafeForwards = 1.0F;
				this.strafeRight = 0.0F;
			}

			this.mob.setSpeed((float) speed);
			this.mob.setZza(this.strafeForwards);
			this.mob.setXxa(this.strafeRight);
		} else if(this.operation == MovementController.Action.MOVE_TO) {
			this.operation = MovementController.Action.WAIT;

			double dx = this.wantedX - this.mob.getX();
			double dy = this.wantedY - this.mob.getY();
			double dz = this.wantedZ - this.mob.getZ();

			if(this.side != null && this.block != null) {
				VoxelShape shape = this.mob.level.getBlockState(this.block).getCollisionShape(this.mob.level, this.block);

				AxisAlignedBB aabb = this.mob.getBoundingBox();

				double ox = 0;
				double oy = 0;
				double oz = 0;

				//Use offset towards pathing side if mob is above that pathing side
				switch(this.side) {
				case DOWN:
					if(aabb.minY >= this.block.getY() + shape.max(Direction.Axis.Y) - 0.01D) {
						ox -= 0.1D;
					}
					break;
				case UP:
					if(aabb.maxY <= this.block.getY() + shape.min(Direction.Axis.Y) + 0.01D) {
						oy += 0.1D;
					}
					break;
				case WEST:
					if(aabb.minX >= this.block.getX() + shape.max(Direction.Axis.X) - 0.01D) {
						ox -= 0.1D;
					}
					break;
				case EAST:
					if(aabb.maxX <= this.block.getX() + shape.min(Direction.Axis.X) + 0.01D) {
						ox += 0.1D;
					}
					break;
				case NORTH:
					if(aabb.minZ >= this.block.getZ() + shape.max(Direction.Axis.Z) - 0.01D) {
						oz -= 0.1D;
					}
					break;
				case SOUTH:
					if(aabb.maxZ <= this.block.getZ() + shape.min(Direction.Axis.Z) + 0.01D) {
						oz += 0.1D;
					}
					break;
				}

				AxisAlignedBB blockAabb = new AxisAlignedBB(this.block.relative(this.side.getOpposite()));

				//If mob is on the pathing side block then only apply the offsets if the block is above the according side of the voxel shape
				if(aabb.intersects(blockAabb)) {
					Direction.Axis offsetAxis = this.side.getAxis();
					double offset;

					switch(offsetAxis) {
					default:
					case X:
						offset = this.side.getStepX() * 0.5f;
						break;
					case Y:
						offset = this.side.getStepY() * 0.5f;
						break;
					case Z:
						offset = this.side.getStepZ() * 0.5f;
						break;
					}

					double allowedOffset = shape.collide(offsetAxis, aabb.move(-this.block.getX(), -this.block.getY(), -this.block.getZ()), offset);

					switch(this.side) {
					case DOWN:
						if(aabb.minY + allowedOffset < this.block.getY() + shape.max(Direction.Axis.Y) - 0.01D) {
							oy = 0;
						}
						break;
					case UP:
						if(aabb.maxY + allowedOffset > this.block.getY() + shape.min(Direction.Axis.Y) + 0.01D) {
							oy = 0;
						}
						break;
					case WEST:
						if(aabb.minX + allowedOffset < this.block.getX() + shape.max(Direction.Axis.X) - 0.01D) {
							ox = 0;
						}
						break;
					case EAST:
						if(aabb.maxX + allowedOffset > this.block.getX() + shape.min(Direction.Axis.X) + 0.01D) {
							ox = 0;
						}
						break;
					case NORTH:
						if(aabb.minZ + allowedOffset < this.block.getZ() + shape.max(Direction.Axis.Z) - 0.01D) {
							oz = 0;
						}
						break;
					case SOUTH:
						if(aabb.maxZ + allowedOffset > this.block.getZ() + shape.min(Direction.Axis.Z) + 0.01D) {
							oz = 0;
						}
						break;
					}
				}

				dx += ox;
				dy += oy;
				dz += oz;
			}

			Direction mainOffsetDir = Direction.getNearest(dx, dy, dz);

			float reach;
			switch(mainOffsetDir) {
			case DOWN:
				reach = 0;
				break;
			case UP:
				reach = this.mob.getBbHeight();
				break;
			default:
				reach = this.mob.getBbWidth() * 0.5f;
				break;
			}

			double verticalOffset = Math.abs(mainOffsetDir.getStepX() * dx) + Math.abs(mainOffsetDir.getStepY() * dy) + Math.abs(mainOffsetDir.getStepZ() * dz);

			Direction groundDir = this.climber.getGroundDirection().getLeft();

			Vector3d jumpDir = null;

			if(this.side != null && verticalOffset > reach - 0.05f && groundDir != this.side && groundDir.getAxis() != this.side.getAxis()) {
				double hdx = (1 - Math.abs(mainOffsetDir.getStepX())) * dx;
				double hdy = (1 - Math.abs(mainOffsetDir.getStepY())) * dy;
				double hdz = (1 - Math.abs(mainOffsetDir.getStepZ())) * dz;

				double hdsq = hdx * hdx + hdy * hdy + hdz * hdz;
				if(hdsq < 0.707f) {
					dx -= this.side.getStepX() * 0.2f;
					dy -= this.side.getStepY() * 0.2f;
					dz -= this.side.getStepZ() * 0.2f;

					if(hdsq < 0.1f) {
						jumpDir = new Vector3d(mainOffsetDir.getStepX(), mainOffsetDir.getStepY(), mainOffsetDir.getStepZ());
					}
				}
			}

			Orientation orientation = this.climber.getOrientation();

			Vector3d up = orientation.getGlobal(this.mob.yRot, -90);

			Vector3d offset = new Vector3d(dx, dy, dz);

			Vector3d targetDir = offset.subtract(up.scale(offset.dot(up)));
			double targetDist = targetDir.length();
			targetDir = targetDir.normalize();

			if(targetDist < 0.0001D) {
				this.mob.setZza(0);
			} else {
				float rx = (float) orientation.localZ.dot(targetDir);
				float ry = (float) orientation.localX.dot(targetDir);

				this.mob.yRot = this.rotlerp(this.mob.yRot, 270.0f - (float) Math.toDegrees(MathHelper.atan2(rx, ry)), 90.0f);

				if(jumpDir == null && this.side != null && targetDist < 0.1D && groundDir == this.side.getOpposite()) {
					jumpDir = new Vector3d(this.side.getStepX(), this.side.getStepY(), this.side.getStepZ());
				}
				
				if(jumpDir == null && this.side != null && Math.abs(this.climber.getGroundDirection().getRight().y) > 0.5f && (!this.climber.canAttachToSide(this.side) || !this.climber.canAttachToSide(Direction.getNearest(dx, dy, dz))) && this.wantedY > this.mob.getY() + 0.1f && verticalOffset > this.mob.maxUpStep) {
					jumpDir = new Vector3d(0, 1, 0);
				}

				if(jumpDir != null) {
					this.mob.setSpeed((float) speed * 0.5f);

					JumpController jumpController = this.mob.getJumpControl();

					if(jumpController instanceof ClimberJumpController) {
						((ClimberJumpController) jumpController).setJumping(jumpDir);
					}
				} else {
					this.mob.setSpeed((float) speed);
				}
			}
		} else if(this.operation == MovementController.Action.JUMPING) {
			this.mob.setSpeed((float) speed);

			if(this.mob.isOnGround()) {
				this.operation = MovementController.Action.WAIT;
			}
		} else {
			this.mob.setZza(0);
		}
	}

	private boolean isWalkableAtOffset(double x, double y, double z) {
		PathNavigator navigator = this.mob.getNavigation();

		if(navigator != null) {
			NodeProcessor processor = navigator.getNodeEvaluator();

			if(processor != null && processor.getBlockPathType(this.mob.level, MathHelper.floor(this.mob.getX() + x), MathHelper.floor(this.mob.getY() + this.mob.getBbHeight() * 0.5f + y), MathHelper.floor(this.mob.getZ() + z)) != PathNodeType.WALKABLE) {
				return false;
			}
		}

		return true;
	}
}