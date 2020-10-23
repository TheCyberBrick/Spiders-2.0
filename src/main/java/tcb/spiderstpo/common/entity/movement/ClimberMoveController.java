package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.util.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.Orientation;

public class ClimberMoveController<T extends MobEntity & IClimberEntity> extends MovementController {
	protected int courseChangeCooldown;
	protected boolean blocked = false;

	protected final IClimberEntity climber;

	public ClimberMoveController(T entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	public void tick() {
		double speed = this.climber.getMovementSpeed() * this.speed;

		//TODO Implement strafing
		
		if(this.action == MovementController.Action.MOVE_TO) {
			this.action = MovementController.Action.WAIT;

			double dx = this.posX - this.mob.getPosX();
			double dy = this.posY - this.mob.getPosY();
			double dz = this.posZ - this.mob.getPosZ();

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

				this.mob.setAIMoveSpeed((float) speed);

				if(this.posY >= this.mob.getPosY() + this.mob.getHeight() && this.climber.getGroundDirection().getLeft() == Direction.DOWN) {
					this.mob.getJumpController().setJumping();
					this.action = MovementController.Action.JUMPING;
				}
			}
		} else if(this.action == MovementController.Action.JUMPING) {
			this.mob.setAIMoveSpeed((float) speed);

			if(this.mob.func_233570_aj_()) {
				this.action = MovementController.Action.WAIT;
			}
		} else if(this.action == MovementController.Action.WAIT) {
			this.mob.setMoveForward(0);
		}
	}
}