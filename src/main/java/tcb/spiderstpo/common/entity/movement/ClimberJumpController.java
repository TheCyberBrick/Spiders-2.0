package tcb.spiderstpo.common.entity.movement;

import javax.annotation.Nullable;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.JumpController;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;

public class ClimberJumpController<T extends MobEntity & IClimberEntity> extends JumpController {
	protected final T climber;

	@Nullable
	protected Vector3d dir;

	public ClimberJumpController(T mob) {
		super(mob);
		this.climber = mob;
	}

	@Override
	public void setJumping() {
		this.setJumping(null);
	}

	public void setJumping(Vector3d dir) {
		super.setJumping();
		this.dir = dir;
	}

	@Override
	public void tick() {
		this.climber.setJumping(this.isJumping);
		if(this.isJumping) {
			this.climber.setJumpDirection(this.dir);
		} else if(this.dir == null) {
			this.climber.setJumpDirection(null);
		}
		this.isJumping = false;
	}
}
