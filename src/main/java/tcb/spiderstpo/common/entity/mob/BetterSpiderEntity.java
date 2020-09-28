package tcb.spiderstpo.common.entity.mob;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.CreatureAttribute;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.SkeletonEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShootableItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import tcb.spiderstpo.common.SpiderMod;

public class BetterSpiderEntity extends AbstractClimberEntity implements IMob {
	public BetterSpiderEntity(EntityType<? extends AbstractClimberEntity> type, World world) {
		super(type, world);
	}

	public BetterSpiderEntity(World world) {
		super(SpiderMod.BETTER_SPIDER.get(), world);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new SwimGoal(this));
		this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
		this.goalSelector.addGoal(4, new BetterSpiderEntity.AttackGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomWalkingGoal(this, 0.8D));
		this.goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.addGoal(6, new LookRandomlyGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new BetterSpiderEntity.TargetGoal<>(this, PlayerEntity.class));
		this.targetSelector.addGoal(3, new BetterSpiderEntity.TargetGoal<>(this, IronGolemEntity.class));
	}

	public static AttributeModifierMap.MutableAttribute getAttributeMap() {
		return MonsterEntity.func_234295_eP_().func_233815_a_(Attributes.field_233818_a_, 24.0D).func_233815_a_(Attributes.field_233821_d_, (double)0.3F);
	}

	@Override
	protected ResourceLocation getLootTable() {
		return EntityType.SPIDER.getLootTable();
	}

	@Override
	public SoundCategory getSoundCategory() {
		return SoundCategory.HOSTILE;
	}

	@Override
	public void livingTick() {
		this.updateArmSwingProgress();
		this.updateIdletime();
		super.livingTick();
	}

	protected void updateIdletime() {
		float f = this.getBrightness();
		if(f > 0.5F) {
			this.idleTime += 2;
		}
	}

	@Override
	protected boolean isDespawnPeaceful() {
		return true;
	}

	@Override
	protected SoundEvent getSwimSound() {
		return SoundEvents.ENTITY_HOSTILE_SWIM;
	}

	@Override
	protected SoundEvent getSplashSound() {
		return SoundEvents.ENTITY_HOSTILE_SPLASH;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		return this.isInvulnerableTo(source) ? false : super.attackEntityFrom(source, amount);
	}

	@Override
	protected SoundEvent getFallSound(int heightIn) {
		return heightIn > 4 ? SoundEvents.ENTITY_HOSTILE_BIG_FALL : SoundEvents.ENTITY_HOSTILE_SMALL_FALL;
	}

	@Override
	protected boolean canDropLoot() {
		return true;
	}

	@Override
	protected boolean func_230282_cS_() {
		return true;
	}

	@Override
	public ItemStack findAmmo(ItemStack shootable) {
		if (shootable.getItem() instanceof ShootableItem) {
			Predicate<ItemStack> predicate = ((ShootableItem)shootable.getItem()).getAmmoPredicate();
			ItemStack itemstack = ShootableItem.getHeldAmmo(this, predicate);
			return itemstack.isEmpty() ? new ItemStack(Items.ARROW) : itemstack;
		} else {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public double getMountedYOffset() {
		return (double)(this.getHeight() * 0.5F);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return SoundEvents.ENTITY_SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_SPIDER_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState blockIn) {
		this.playSound(SoundEvents.ENTITY_SPIDER_STEP, 0.15F, 1.0F);
	}

	@Override
	public void setMotionMultiplier(BlockState state, Vector3d motionMultiplierIn) {
		if(!state.isIn(Blocks.COBWEB)) {
			super.setMotionMultiplier(state, motionMultiplierIn);
		}
	}

	@Override
	public CreatureAttribute getCreatureAttribute() {
		return CreatureAttribute.ARTHROPOD;
	}

	@Override
	public boolean isPotionApplicable(EffectInstance potioneffectIn) {
		if(potioneffectIn.getPotion() == Effects.POISON) {
			net.minecraftforge.event.entity.living.PotionEvent.PotionApplicableEvent event = new net.minecraftforge.event.entity.living.PotionEvent.PotionApplicableEvent(this, potioneffectIn);
			net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
			return event.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW;
		}
		return super.isPotionApplicable(potioneffectIn);
	}

	@Override
	@Nullable
	public ILivingEntityData onInitialSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
		spawnDataIn = super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
		if(worldIn.getRandom().nextInt(100) == 0) {
			SkeletonEntity skeletonentity = EntityType.SKELETON.create(this.world);
			skeletonentity.setLocationAndAngles(this.getPosX(), this.getPosY(), this.getPosZ(), this.rotationYaw, 0.0F);
			skeletonentity.onInitialSpawn(worldIn, difficultyIn, reason, (ILivingEntityData)null, (CompoundNBT)null);
			skeletonentity.startRiding(this);
		}

		if(spawnDataIn == null) {
			spawnDataIn = new SpiderEntity.GroupData();
			if(worldIn.getDifficulty() == Difficulty.HARD && worldIn.getRandom().nextFloat() < 0.1F * difficultyIn.getClampedAdditionalDifficulty()) {
				((SpiderEntity.GroupData)spawnDataIn).setRandomEffect(worldIn.getRandom());
			}
		}

		if(spawnDataIn instanceof SpiderEntity.GroupData) {
			Effect effect = ((SpiderEntity.GroupData)spawnDataIn).effect;
			if(effect != null) {
				this.addPotionEffect(new EffectInstance(effect, Integer.MAX_VALUE));
			}
		}

		return spawnDataIn;
	}

	@Override
	protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
		return 0.65F;
	}

	static class AttackGoal extends MeleeAttackGoal {
		public AttackGoal(BetterSpiderEntity spider) {
			super(spider, 1.0D, true);
		}

		@Override
		public boolean shouldExecute() {
			return super.shouldExecute() && !this.attacker.isBeingRidden();
		}

		@Override
		public boolean shouldContinueExecuting() {
			/*LivingEntity livingentity = this.attacker.getAttackTarget();
			if (livingentity == null) {
				return false;
			} else if (!livingentity.isAlive()) {
				return false;
			} else if (!this.attacker.isWithinHomeDistanceFromPosition(livingentity.func_233580_cy_())) {
				return false;
			} else {
				return true;
			}*/

			float f = this.attacker.getBrightness();
			if (f >= 0.5F && this.attacker.getRNG().nextInt(100) == 0) {
				this.attacker.setAttackTarget((LivingEntity)null);
				return false;
			} else {
				return super.shouldContinueExecuting();
			}
		}

		@Override
		protected double getAttackReachSqr(LivingEntity attackTarget) {
			return (double)(4.0F + attackTarget.getWidth());
		}
	}

	static class TargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
		public TargetGoal(BetterSpiderEntity spider, Class<T> classTarget) {
			super(spider, classTarget, true);
		}

		@Override
		public boolean shouldExecute() {
			/*this.targetEntitySelector = new EntityPredicate() {
				@Override
				public boolean canTarget(LivingEntity attacker, LivingEntity target) {
					return target instanceof PlayerEntity;
				}
			}.setDistance(this.getTargetDistance()).setCustomPredicate(p -> true);
			return super.shouldExecute();*/

			float f = this.goalOwner.getBrightness();
			return f >= 0.5F ? false : super.shouldExecute();
		}
	}
}
