package tcb.spiderstpo.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import tcb.spiderstpo.common.entity.mob.IMobEntityLivingTickHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityNavigatorHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityRegisterGoalsHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityTickHook;

@Mixin(Mob.class)
public abstract class MobEntityMixin implements IMobEntityLivingTickHook, IMobEntityTickHook, IMobEntityRegisterGoalsHook, IMobEntityNavigatorHook {
	@Inject(method = "aiStep()V", at = @At("HEAD"))
	private void onLivingTick(CallbackInfo ci) {
		this.onLivingTick();
	}

	@Override
	public void onLivingTick() { }

	@Inject(method = "tick()V", at = @At("RETURN"))
	private void onTick(CallbackInfo ci) {
		this.onTick();
	}

	@Override
	public void onTick() { }

	@Shadow(prefix = "shadow$")
	private void shadow$registerGoals() { }

	@Redirect(method = "<init>*", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Mob;registerGoals()V"
			))
	private void onRegisterGoals(Mob _this) {
		this.shadow$registerGoals();

		if(_this == (Object) this) {
			this.onRegisterGoals();
		}
	}

	@Override
	public void onRegisterGoals() { }

	@Inject(method = "createNavigation", at = @At("HEAD"), cancellable = true)
	private void onCreateNavigator(Level world, CallbackInfoReturnable<PathNavigation> ci) {
		PathNavigation navigator = this.onCreateNavigator(world);
		if(navigator != null) {
			ci.setReturnValue(navigator);
		}
	}

	@Override
	public PathNavigation onCreateNavigator(Level world) {
		return null;
	}
}
