package tcb.spiderstpo.common.entity.movement;

import javax.annotation.Nullable;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.JumpController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.pathfinding.NodeProcessor;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.Orientation;

public class ClimberMoveController<T extends MobEntity & IClimberEntity> extends MovementController {
	protected final IClimberEntity climber;

	@Nullable
	protected Direction side;

	public ClimberMoveController(T entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	public void setMoveTo(double x, double y, double z, double speedIn) {
		this.setMoveTo(x, y, z, null, speedIn);
	}

	public void setMoveTo(double x, double y, double z, Direction side, double speedIn) {
		super.setMoveTo(x, y, z, speedIn);
		this.side = side;
	}

	@Override
	public void tick() {
		double speed = this.climber.getMovementSpeed() * this.speed;

		if(this.action == MovementController.Action.STRAFE) {
			this.action = MovementController.Action.WAIT;

			float forward = this.moveForward;
			float strafe = this.moveStrafe;
			float moveSpeed = MathHelper.sqrt(forward * forward + strafe * strafe);
			if(moveSpeed < 1.0F) {
				moveSpeed = 1.0F;
			}

			moveSpeed = (float)speed / moveSpeed;
			forward = forward * moveSpeed;
			strafe = strafe * moveSpeed;

			Orientation orientation = this.climber.getOrientation();

			Vector3d forwardVector = orientation.getGlobal(this.mob.rotationYaw, 0);
			Vector3d strafeVector = orientation.getGlobal(this.mob.rotationYaw + 90.0f, 0);

			if(!this.isWalkableAtOffset(forwardVector.x * forward + strafeVector.x * strafe, forwardVector.y * forward + strafeVector.y * strafe, forwardVector.z * forward + strafeVector.z * strafe)) {
				this.moveForward = 1.0F;
				this.moveStrafe = 0.0F;
			}

			this.mob.setAIMoveSpeed((float) speed);
			this.mob.setMoveForward(this.moveForward);
			this.mob.setMoveStrafing(this.moveStrafe);
		} else if(this.action == MovementController.Action.MOVE_TO) {
			this.action = MovementController.Action.WAIT;

			double dx = this.posX - this.mob.getPosX();
			double dy = this.posY - this.mob.getPosY();
			double dz = this.posZ - this.mob.getPosZ();

			Direction mainOffsetDir = Direction.getFacingFromVector(dx, dy, dz);

			float reach;
			switch(mainOffsetDir) {
			case DOWN:
				reach = 0;
				break;
			case UP:
				reach = this.mob.getHeight();
				break;
			default:
				reach = this.mob.getWidth() * 0.5f;
				break;
			}

			double verticalOffset = Math.abs(mainOffsetDir.getXOffset() * dx) + Math.abs(mainOffsetDir.getYOffset() * dy) + Math.abs(mainOffsetDir.getZOffset() * dz);

			Direction groundDir = this.climber.getGroundDirection().getLeft();

			Vector3d jumpDir = null;

			if(this.side != null && verticalOffset > reach - 0.05f && groundDir != this.side && groundDir.getAxis() != this.side.getAxis()) {
				double hdx = (1 - Math.abs(mainOffsetDir.getXOffset())) * dx;
				double hdy = (1 - Math.abs(mainOffsetDir.getYOffset())) * dy;
				double hdz = (1 - Math.abs(mainOffsetDir.getZOffset())) * dz;

				double hdsq = hdx * hdx + hdy * hdy + hdz * hdz;
				if(hdsq < 0.707f) {
					dx -= this.side.getXOffset() * 0.2f;
					dy -= this.side.getYOffset() * 0.2f;
					dz -= this.side.getZOffset() * 0.2f;

					if(hdsq < 0.1f) {
						jumpDir = new Vector3d(mainOffsetDir.getXOffset(), mainOffsetDir.getYOffset(), mainOffsetDir.getZOffset());
					}
				}
			}

			Orientation orientation = this.climber.getOrientation();

			Vector3d up = orientation.getGlobal(this.mob.rotationYaw, -90);

			Vector3d offset = new Vector3d(dx, dy, dz);

			Vector3d targetDir = offset.subtract(up.scale(offset.dotProduct(up)));
			double targetDist = targetDir.length();
			targetDir = targetDir.normalize();

			if(targetDist < 0.0001D) {
				this.mob.setMoveForward(0);
			} else {
				float rx = (float) orientation.localZ.dotProduct(targetDir);
				float ry = (float) orientation.localX.dotProduct(targetDir);

				this.mob.rotationYaw = this.limitAngle(this.mob.rotationYaw, 270.0f - (float) Math.toDegrees(MathHelper.atan2(rx, ry)), 90.0f);

				if(jumpDir == null && this.side != null && targetDist < 0.1D && groundDir == this.side.getOpposite()) {
					jumpDir = new Vector3d(this.side.getXOffset(), this.side.getYOffset(), this.side.getZOffset());
				}

				if(jumpDir != null) {
					this.mob.setAIMoveSpeed((float) speed * 0.5f);

					JumpController jumpController = this.mob.getJumpController();

					if(jumpController instanceof ClimberJumpController) {
						((ClimberJumpController) jumpController).setJumping(jumpDir);
					}
				} else {
					this.mob.setAIMoveSpeed((float) speed);
				}
			}
		} else if(this.action == MovementController.Action.JUMPING) {
			this.mob.setAIMoveSpeed((float) speed);

			if(this.mob.func_233570_aj_()) {
				this.action = MovementController.Action.WAIT;
			}
		} else {
			this.mob.setMoveForward(0);
		}
	}

	private boolean isWalkableAtOffset(double x, double y, double z) {
		PathNavigator navigator = this.mob.getNavigator();

		if(navigator != null) {
			NodeProcessor processor = navigator.getNodeProcessor();

			if(processor != null && processor.getPathNodeType(this.mob.world, MathHelper.floor(this.mob.getPosX() + x), MathHelper.floor(this.mob.getPosY() + this.mob.getHeight() * 0.5f + y), MathHelper.floor(this.mob.getPosZ() + z)) != PathNodeType.WALKABLE) {
				return false;
			}
		}

		return true;
	}
}