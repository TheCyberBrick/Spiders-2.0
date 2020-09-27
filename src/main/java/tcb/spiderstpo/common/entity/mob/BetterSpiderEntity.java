package tcb.spiderstpo.common.entity.mob;

import net.minecraft.entity.EntityPredicate;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public class BetterSpiderEntity extends AbstractClimberEntity {

	public BetterSpiderEntity(EntityType<? extends AbstractClimberEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new SwimGoal(this));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true) {
			@Override
			public boolean shouldContinueExecuting() {
				LivingEntity livingentity = this.attacker.getAttackTarget();
				if (livingentity == null) {
					return false;
				} else if (!livingentity.isAlive()) {
					return false;
				} else if (!this.attacker.isWithinHomeDistanceFromPosition(livingentity.func_233580_cy_())) {
					return false;
				} else {
					return true;
				}
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomWalkingGoal(this, 0.8D));
		this.goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.addGoal(6, new LookRandomlyGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<PlayerEntity>(this, PlayerEntity.class, 1, false, false, p -> true) {
			@Override
			public boolean shouldExecute() {
				this.targetEntitySelector = new EntityPredicate() {
					@Override
					public boolean canTarget(LivingEntity attacker, LivingEntity target) {
						return target instanceof PlayerEntity;
					}
				}.setDistance(this.getTargetDistance()).setCustomPredicate(p -> true);
				return super.shouldExecute();
			}
		});
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
	}

	public static AttributeModifierMap.MutableAttribute func_234305_eI_() {
		return MonsterEntity.func_234295_eP_().func_233815_a_(Attributes.field_233818_a_, 16.0D).func_233815_a_(Attributes.field_233821_d_, (double)0.3F);
	}
}
