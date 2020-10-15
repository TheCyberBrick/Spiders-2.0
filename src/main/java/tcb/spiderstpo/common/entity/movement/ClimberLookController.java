package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;

public class ClimberLookController extends LookController {
	protected final AbstractClimberEntity climber;

	public ClimberLookController(AbstractClimberEntity entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	protected float getTargetPitch() {
		Vector3d dir = new Vector3d(this.posX - this.mob.getPosX(), this.posY - this.mob.getPosYEye(), this.posZ - this.mob.getPosZ());
		return this.climber.getOrientation().getRotation(dir).getRight();
	}

	@Override
	protected float getTargetYaw() {
		Vector3d dir = new Vector3d(this.posX - this.mob.getPosX(), this.posY - this.mob.getPosYEye(), this.posZ - this.mob.getPosZ());
		return this.climber.getOrientation().getRotation(dir).getLeft();
	}
}
