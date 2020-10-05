package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.util.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;

public class ClimberMoveController extends MovementController {
	protected final AbstractClimberEntity climber;
	protected int courseChangeCooldown;
	protected boolean blocked = false;

	public ClimberMoveController(AbstractClimberEntity entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	public void tick() {
		double speed = this.climber.getMovementSpeed() * this.speed;

		if(this.action == MovementController.Action.MOVE_TO) {
			this.action = MovementController.Action.WAIT;

			AbstractClimberEntity.Orientation orientation = this.climber.getOrientation(1);

			Vec3d up = orientation.getDirection(this.climber.rotationYaw, -90);

			int entitySizeX = MathHelper.floor(this.mob.getWidth() + 1.0F);
			int entitySizeY = MathHelper.floor(this.mob.getHeight() + 1.0F);
			int entitySizeZ = MathHelper.floor(this.mob.getWidth() + 1.0F);

			//TODO This is unreliable when moving from ground to wall
			Direction side = this.climber.getWalkingSide().getLeft();

			double dx = (this.posX + Math.max(0, side.getXOffset()) * (entitySizeX - 1) + side.getXOffset() * 0.5f) - this.mob.getPosX();
			double dy = (this.posY + Math.max(0, side.getYOffset()) * (entitySizeY - 1) + side.getYOffset() * 0.5f) - this.mob.getPosY();
			double dz = (this.posZ + Math.max(0, side.getZOffset()) * (entitySizeZ - 1) + side.getZOffset() * 0.5f) - this.mob.getPosZ();

			Vec3d offset = new Vec3d(dx, dy, dz);

			Vec3d targetDir = offset.subtract(up.scale(offset.dotProduct(up)));
			double targetDist = targetDir.length();
			targetDir = targetDir.normalize();

			if(targetDist < 0.0001D) {
				this.mob.setMoveForward(0);
			} else {
				float rx = (float) orientation.localZ.dotProduct(targetDir);
				float ry = (float) orientation.localX.dotProduct(targetDir);

				this.mob.rotationYaw = this.limitAngle(this.mob.rotationYaw, 270.0f - (float) Math.toDegrees(Math.atan2(rx, ry)), 90.0f);

				this.mob.setAIMoveSpeed((float) speed);
			}
		} else if(this.action == MovementController.Action.WAIT) {
			this.mob.setMoveForward(0);
		}
	}
}