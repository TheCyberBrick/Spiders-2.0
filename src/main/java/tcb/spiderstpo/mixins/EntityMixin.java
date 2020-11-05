package tcb.spiderstpo.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.IEntityMovementHook;
import tcb.spiderstpo.common.entity.mob.IEntityReadWriteHook;
import tcb.spiderstpo.common.entity.mob.IEntityRegisterDataHook;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntityMovementHook, IEntityReadWriteHook, IEntityRegisterDataHook {
	@Inject(method = "move(Lnet/minecraft/entity/MoverType;Lnet/minecraft/util/math/vector/Vector3d;)V", at = @At("HEAD"), cancellable = true)
	private void onMovePre(MoverType type, Vector3d pos, CallbackInfo ci) {
		if(this.onMove(type, pos, true)) {
			ci.cancel();
		}
	}

	@Inject(method = "move(Lnet/minecraft/entity/MoverType;Lnet/minecraft/util/math/vector/Vector3d;)V", at = @At("RETURN"))
	private void onMovePost(MoverType type, Vector3d pos, CallbackInfo ci) {
		this.onMove(type, pos, false);
	}

	@Override
	public boolean onMove(MoverType type, Vector3d pos, boolean pre) {
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

	@Inject(method = "read(Lnet/minecraft/nbt/CompoundNBT;)V", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;readAdditional(Lnet/minecraft/nbt/CompoundNBT;)V",
			shift = At.Shift.AFTER
			))
	private void onRead(CompoundNBT nbt, CallbackInfo ci) {
		this.onRead(nbt);
	}

	@Override
	public void onRead(CompoundNBT nbt) { }

	@Inject(method = "writeWithoutTypeId(Lnet/minecraft/nbt/CompoundNBT;)Lnet/minecraft/nbt/CompoundNBT;", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;writeAdditional(Lnet/minecraft/nbt/CompoundNBT;)V",
			shift = At.Shift.AFTER
			))
	private void onWrite(CompoundNBT nbt, CallbackInfoReturnable<CompoundNBT> ci) {
		this.onWrite(nbt);
	}

	@Override
	public void onWrite(CompoundNBT nbt) { }

	@Shadow(prefix = "shadow$")
	private void shadow$registerData() { }

	@Redirect(method = "<init>*", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;registerData()V"
			))
	private void onRegisterData(Entity _this) {
		this.shadow$registerData();
		
		if(_this == (Object) this) {
			this.onRegisterData();
		}
	}

	@Override
	public void onRegisterData() { }
}
