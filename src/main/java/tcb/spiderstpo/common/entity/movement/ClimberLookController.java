package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.util.math.Vec3d;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;

public class ClimberLookController<T extends MobEntity & IClimberEntity> extends LookController {
	protected final IClimberEntity climber;

	public ClimberLookController(T entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	protected float getTargetPitch() {
		Vec3d dir = new Vec3d(this.posX - this.mob.getPosX(), this.posY - this.mob.getPosYEye(), this.posZ - this.mob.getPosZ());
		return this.climber.getOrientation().getLocalRotation(dir).getRight();
	}

	@Override
	protected float getTargetYaw() {
		Vec3d dir = new Vec3d(this.posX - this.mob.getPosX(), this.posY - this.mob.getPosYEye(), this.posZ - this.mob.getPosZ());
		return this.climber.getOrientation().getLocalRotation(dir).getLeft();
	}
}
