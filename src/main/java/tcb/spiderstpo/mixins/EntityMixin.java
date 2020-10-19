package tcb.spiderstpo.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import tcb.spiderstpo.common.entity.mob.IEntityMovementHook;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntityMovementHook {
	@Inject(method = "move(Lnet/minecraft/entity/MoverType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), cancellable = true)
	private void onMovePre(MoverType type, Vec3d pos, CallbackInfo ci) {
		if(this.onMove(type, pos, true)) {
			ci.cancel();
		}
	}

	@Inject(method = "move(Lnet/minecraft/entity/MoverType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("RETURN"))
	private void onMovePost(MoverType type, Vec3d pos, CallbackInfo ci) {
		this.onMove(type, pos, false);
	}

	@Override
	public boolean onMove(MoverType type, Vec3d pos, boolean pre) {
		return false;
	}

	@Inject(method = "getOnPosition()Lnet/minecraft/util/math/BlockPos;", at = @At("RETURN"), cancellable = true)
	private void onGetOnPosition(CallbackInfoReturnable<BlockPos> ci) {
		BlockPos adjusted = this.getAdjustedOnPosition(ci.getReturnValue());
		if(adjusted != null) {
			ci.setReturnValue(adjusted);
		}
	}

	@Override
	public BlockPos getAdjustedOnPosition(BlockPos onPosition) {
		return null;
	}

	@Inject(method = "canTriggerWalking()Z", at = @At("RETURN"), cancellable = true)
	private void onCanTriggerWalking(CallbackInfoReturnable<Boolean> ci) {
		ci.setReturnValue(this.getAdjustedCanTriggerWalking(ci.getReturnValue()));
	}

	@Override
	public boolean getAdjustedCanTriggerWalking(boolean canTriggerWalking) {
		return canTriggerWalking;
	}
}
