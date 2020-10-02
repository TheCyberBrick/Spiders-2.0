package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;

public class ClimberMoveController extends EntityMoveHelper {
	protected int courseChangeCooldown;
	protected boolean blocked = false;

	protected final AbstractClimberEntity climber;

	public ClimberMoveController(AbstractClimberEntity entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	public void onUpdateMoveHelper() {
		double speed = this.climber.getMovementSpeed() * this.speed;

		if(this.action == EntityMoveHelper.Action.MOVE_TO) {
			this.action = EntityMoveHelper.Action.WAIT;

			AbstractClimberEntity.Orientation orientation = this.climber.getOrientation(1);

			Vec3d up = orientation.getDirection(this.climber.rotationYaw, -90);

			int entitySizeX = MathHelper.floor(this.entity.width + 1.0F);
			int entitySizeY = MathHelper.floor(this.entity.height + 1.0F);
			int entitySizeZ = MathHelper.floor(this.entity.width + 1.0F);

			//TODO This is unreliable when moving from ground to wall
			EnumFacing side = this.climber.getWalkingSide().getLeft();
			
			double dx = (this.posX + Math.max(0, side.getFrontOffsetX()) * (entitySizeX - 1) + side.getFrontOffsetX() * 0.5f) - this.entity.posX;
			double dy = (this.posY + Math.max(0, side.getFrontOffsetY()) * (entitySizeY - 1) + side.getFrontOffsetY() * 0.5f) - this.entity.posY;
			double dz = (this.posZ + Math.max(0, side.getFrontOffsetZ()) * (entitySizeZ - 1) + side.getFrontOffsetZ() * 0.5f) - this.entity.posZ;

			Vec3d offset = new Vec3d(dx, dy, dz);

			Vec3d targetDir = offset.subtract(up.scale(offset.dotProduct(up)));
			double targetDist = targetDir.lengthVector();
			targetDir = targetDir.normalize();

			if(targetDist < 0.0001D) {
				this.entity.setMoveForward(0);
			} else {
				float rx = (float)orientation.localZ.dotProduct(targetDir);
				float ry = (float)orientation.localX.dotProduct(targetDir);

				this.entity.rotationYaw = this.limitAngle(this.entity.rotationYaw, 270.0f - (float)Math.toDegrees(Math.atan2(rx, ry)), 90.0f);

				this.entity.setAIMoveSpeed((float)speed);
			}
		} else if(this.action == EntityMoveHelper.Action.WAIT) {
			this.entity.setMoveForward(0);
		}
	}
}