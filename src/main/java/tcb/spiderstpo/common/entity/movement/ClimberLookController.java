package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;

public class ClimberLookController<T extends MobEntity & IClimberEntity> extends LookController {
	protected final IClimberEntity climber;

	public ClimberLookController(T entity) {
		super(entity);
		this.climber = entity;
	}

	@Override
	protected float getXRotD() {
		Vector3d dir = new Vector3d(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getEyeY(), this.wantedZ - this.mob.getZ());
		return this.climber.getOrientation().getLocalRotation(dir).getRight();
	}

	@Override
	protected float getYRotD() {
		Vector3d dir = new Vector3d(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getEyeY(), this.wantedZ - this.mob.getZ());
		return this.climber.getOrientation().getLocalRotation(dir).getLeft();
	}
}
