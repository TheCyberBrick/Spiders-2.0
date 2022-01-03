package tcb.spiderstpo.common.entity.goal;

import java.util.EnumSet;

import org.apache.commons.lang3.tuple.Triple;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.Orientation;

public class BetterLeapAtTargetGoal<T extends Mob & IClimberEntity> extends Goal {
	private final T leaper;
	private final float leapMotionY;

	private LivingEntity leapTarget;
	private Vec3 forwardJumpDirection;
	private Vec3 upwardJumpDirection;

	public BetterLeapAtTargetGoal(T leapingEntity, float leapMotionYIn) {
		this.leaper = leapingEntity;
		this.leapMotionY = leapMotionYIn;
		this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if(!this.leaper.isVehicle()) {
			this.leapTarget = this.leaper.getTarget();

			if(this.leapTarget != null && this.leaper.isOnGround()) {
				Triple<Vec3, Vec3, Vec3> projectedVector = this.getProjectedVector(this.leapTarget.position());

				double dstSq = projectedVector.getLeft().lengthSqr();
				double dstSqDot = projectedVector.getMiddle().lengthSqr();

				if(dstSq >= 4.0D && dstSq <= 16.0D && dstSqDot <= 1.2f && this.leaper.getRandom().nextInt(5) == 0) {
					this.forwardJumpDirection = projectedVector.getLeft().normalize();
					this.upwardJumpDirection = projectedVector.getRight().normalize();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return !this.leaper.isOnGround();
	}

	@Override
	public void start() {
		Vec3 motion = this.leaper.getDeltaMovement();

		Vec3 jumpVector = this.forwardJumpDirection;

		if(jumpVector.lengthSqr() > 1.0E-7D) {
			jumpVector = jumpVector.normalize().scale(0.4D).add(motion.scale(0.2D));
		}

		jumpVector = jumpVector.add(this.upwardJumpDirection.scale(this.leapMotionY));
		jumpVector = new Vec3(jumpVector.x * (1 - Math.abs(this.upwardJumpDirection.x)), jumpVector.y, jumpVector.z * (1 - Math.abs(this.upwardJumpDirection.z)));

		this.leaper.setDeltaMovement(jumpVector);

		Orientation orientation = this.leaper.getOrientation();

		float rx = (float) orientation.localZ.dot(jumpVector);
		float ry = (float) orientation.localX.dot(jumpVector);

		this.leaper.setYRot(270.0f - (float) Math.toDegrees(Mth.atan2(rx, ry)));
	}

	protected Triple<Vec3, Vec3, Vec3> getProjectedVector(Vec3 target) {
		Orientation orientation = this.leaper.getOrientation();
		Vec3 up = orientation.getGlobal(this.leaper.getYRot(), -90.0f);
		Vec3 diff = target.subtract(this.leaper.position());
		Vec3 dot = up.scale(up.dot(diff));
		return Triple.of(diff.subtract(dot), dot, up);
	}
}
