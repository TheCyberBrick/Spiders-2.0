package tcb.spiderstpo.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.command.arguments.EntityAnchorArgument;
import net.minecraft.command.arguments.EntityAnchorArgument.Type;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.mob.ILivingEntityDataManagerHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityLookAtHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityTravelHook;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements ILivingEntityLookAtHook, ILivingEntityDataManagerHook, ILivingEntityTravelHook {
	@ModifyVariable(method = "lookAt(Lnet/minecraft/command/arguments/EntityAnchorArgument$Type;Lnet/minecraft/util/math/vector/Vector3d;)V", at = @At("HEAD"), ordinal = 0)
	private Vector3d onLookAtModify(Vector3d vec, EntityAnchorArgument.Type anchor, Vector3d vec2) {
		return this.onLookAt(anchor, vec);
	}

	@Override
	public Vector3d onLookAt(Type anchor, Vector3d vec) {
		return vec;
	}

	@Inject(method = "notifyDataManagerChange(Lnet/minecraft/network/datasync/DataParameter;)V", at = @At("HEAD"))
	private void onNotifyDataManagerChange(DataParameter<?> key, CallbackInfo ci) {
		this.onNotifyDataManagerChange(key);
	}

	@Override
	public void onNotifyDataManagerChange(DataParameter<?> key) { }

	@Inject(method = "travel(Lnet/minecraft/util/math/vector/Vector3d;)V", at = @At("HEAD"), cancellable = true)
	private void onTravelPre(Vector3d relative, CallbackInfo ci) {
		if(this.onTravel(relative, true)) {
			ci.cancel();
		}
	}

	@Inject(method = "travel(Lnet/minecraft/util/math/vector/Vector3d;)V", at = @At("RETURN"))
	private void onTravelPost(Vector3d relative, CallbackInfo ci) {
		this.onTravel(relative, false);
	}

	@Override
	public boolean onTravel(Vector3d relative, boolean pre) {
		return false;
	}
}
