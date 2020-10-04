package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.util.math.Vec3d;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;

public class ClimberLookController extends LookController {
	protected final AbstractClimberEntity climber;

	public ClimberLookController(AbstractClimberEntity entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	protected float getTargetPitch() {
		Vec3d dir = new Vec3d(this.posX - this.mob.getPosX(), this.posY - this.mob.getPosYEye(), this.posZ - this.mob.getPosZ());
		return this.climber.getOrientation(1).getRotation(dir).getRight();
	}

	@Override
	protected float getTargetYaw() {
		Vec3d dir = new Vec3d(this.posX - this.mob.getPosX(), this.posY - this.mob.getPosYEye(), this.posZ - this.mob.getPosZ());
		return this.climber.getOrientation(1).getRotation(dir).getLeft();
	}
}
