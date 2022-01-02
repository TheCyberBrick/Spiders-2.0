package tcb.spiderstpo.common.entity.goal;

import java.util.EnumSet;

import org.apache.commons.lang3.tuple.Triple;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.Orientation;

public class BetterLeapAtTargetGoal<T extends MobEntity & IClimberEntity> extends Goal {
	private final T leaper;
	private final float leapMotionY;

	private LivingEntity leapTarget;
	private Vector3d forwardJumpDirection;
	private Vector3d upwardJumpDirection;

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
				Triple<Vector3d, Vector3d, Vector3d> projectedVector = this.getProjectedVector(this.leapTarget.position());

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
		Vector3d motion = this.leaper.getDeltaMovement();

		Vector3d jumpVector = this.forwardJumpDirection;

		if(jumpVector.lengthSqr() > 1.0E-7D) {
			jumpVector = jumpVector.normalize().scale(0.4D).add(motion.scale(0.2D));
		}

		jumpVector = jumpVector.add(this.upwardJumpDirection.scale(this.leapMotionY));
		jumpVector = new Vector3d(jumpVector.x * (1 - Math.abs(this.upwardJumpDirection.x)), jumpVector.y, jumpVector.z * (1 - Math.abs(this.upwardJumpDirection.z)));

		this.leaper.setDeltaMovement(jumpVector);

		Orientation orientation = this.leaper.getOrientation();

		float rx = (float) orientation.localZ.dot(jumpVector);
		float ry = (float) orientation.localX.dot(jumpVector);

		this.leaper.yRot = 270.0f - (float) Math.toDegrees(MathHelper.atan2(rx, ry));
	}

	protected Triple<Vector3d, Vector3d, Vector3d> getProjectedVector(Vector3d target) {
		Orientation orientation = this.leaper.getOrientation();
		Vector3d up = orientation.getGlobal(this.leaper.yRot, -90.0f);
		Vector3d diff = target.subtract(this.leaper.position());
		Vector3d dot = up.scale(up.dot(diff));
		return Triple.of(diff.subtract(dot), dot, up);
	}
}
