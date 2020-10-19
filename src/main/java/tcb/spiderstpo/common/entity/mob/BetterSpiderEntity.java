package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootTableList;

public class BetterSpiderEntity extends AbstractClimberEntity implements IMob {
	public BetterSpiderEntity(World world) {
		super(world);
		this.experienceValue = 5;
		this.setSize(0.95f, 0.85f);
	}

	@Override
	protected void initEntityAI() {
		this.tasks.addTask(1, new EntityAISwimming(this));
		this.tasks.addTask(3, new EntityAILeapAtTarget(this, 0.4F));
		this.tasks.addTask(4, new AISpiderAttack(this));
		this.tasks.addTask(5, new EntityAIWanderAvoidWater(this, 0.8D));
		this.tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
		this.tasks.addTask(6, new EntityAILookIdle(this));
		this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false, new Class[0]));
		this.targetTasks.addTask(2, new AISpiderTarget<>(this, EntityPlayer.class));
		this.targetTasks.addTask(3, new AISpiderTarget<>(this, EntityIronGolem.class));
	}
	
	@Override
	public String getName() {
		if(this.hasCustomName()) {
            return this.getCustomNameTag();
        } else {
            return I18n.translateToLocal("entity.Spider.name");
        }
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(16.0D);
		this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
	}
	
	@Override
	public SoundCategory getSoundCategory() {
		return SoundCategory.HOSTILE;
	}

	@Override
	public void onLivingUpdate() {
		this.updateArmSwingProgress();
		float f = this.getBrightness();

		if(f > 0.5F) {
			this.idleTime += 2;
		}

		super.onLivingUpdate();
	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		if(!this.world.isRemote && this.world.getDifficulty() == EnumDifficulty.PEACEFUL) {
			this.setDead();
		}
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
		return this.isEntityInvulnerable(source) ? false : super.attackEntityFrom(source, amount);
	}

	@Override
	public boolean attackEntityAsMob(Entity entityIn) {
		float attackDamage = (float)this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
		int kbModifier = 0;

		if(entityIn instanceof EntityLivingBase) {
			attackDamage += EnchantmentHelper.getModifierForCreature(this.getHeldItemMainhand(), ((EntityLivingBase)entityIn).getCreatureAttribute());
			kbModifier += EnchantmentHelper.getKnockbackModifier(this);
		}

		boolean success = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), attackDamage);

		if(success) {
			if(kbModifier > 0 && entityIn instanceof EntityLivingBase) {
				((EntityLivingBase)entityIn).knockBack(this, (float)kbModifier * 0.5F, (double)MathHelper.sin(this.rotationYaw * 0.017453292F), (double)(-MathHelper.cos(this.rotationYaw * 0.017453292F)));
				this.motionX *= 0.6D;
				this.motionZ *= 0.6D;
			}

			int fireAspect = EnchantmentHelper.getFireAspectModifier(this);

			if(fireAspect > 0) {
				entityIn.setFire(fireAspect * 4);
			}

			if(entityIn instanceof EntityPlayer) {
				EntityPlayer player = (EntityPlayer)entityIn;
				ItemStack heldItem = this.getHeldItemMainhand();
				ItemStack usingItem = player.isHandActive() ? player.getActiveItemStack() : ItemStack.EMPTY;

				if(!heldItem.isEmpty() && !usingItem.isEmpty() && heldItem.getItem().canDisableShield(heldItem, usingItem, player, this) && usingItem.getItem().isShield(usingItem, player)) {
					float disableChance = 0.25F + (float)EnchantmentHelper.getEfficiencyModifier(this) * 0.05F;

					if(this.rand.nextFloat() < disableChance) {
						player.getCooldownTracker().setCooldown(usingItem.getItem(), 100);
						this.world.setEntityState(player, (byte)30);
					}
				}
			}

			this.applyEnchantments(this, entityIn);
		}

		return success;
	}

	@Override
	public float getBlockPathWeight(BlockPos pos) {
		return 0.5F - this.world.getLightBrightness(pos);
	}

	@Override
	public boolean getCanSpawnHere() {
		return this.world.getDifficulty() != EnumDifficulty.PEACEFUL && this.isValidLightLevel() && super.getCanSpawnHere();
	}

	protected boolean isValidLightLevel() {
		BlockPos blockpos = new BlockPos(this.posX, this.getEntityBoundingBox().minY, this.posZ);

		if(this.world.getLightFor(EnumSkyBlock.SKY, blockpos) > this.rand.nextInt(32)) {
			return false;
		} else {
			int lightLevel = this.world.getLightFromNeighbors(blockpos);

			if(this.world.isThundering()) {
				int skyLight = this.world.getSkylightSubtracted();
				this.world.setSkylightSubtracted(10);
				lightLevel = this.world.getLightFromNeighbors(blockpos);
				this.world.setSkylightSubtracted(skyLight);
			}

			return lightLevel <= this.rand.nextInt(8);
		}
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
	public double getMountedYOffset() {
		return (double)(this.height * 0.5F);
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
	protected void playStepSound(BlockPos pos, Block blockIn) {
		this.playSound(SoundEvents.ENTITY_SPIDER_STEP, 0.15F, 1.0F);
	}

	@Override
	@Nullable
	protected ResourceLocation getLootTable() {
		return LootTableList.ENTITIES_SPIDER;
	}

	@Override
	public void setInWeb() {
	}

	@Override
	public EnumCreatureAttribute getCreatureAttribute() {
		return EnumCreatureAttribute.ARTHROPOD;
	}

	@Override
	public boolean isPotionApplicable(PotionEffect potioneffectIn) {
		return potioneffectIn.getPotion() == MobEffects.POISON ? false : super.isPotionApplicable(potioneffectIn);
	}

	@Override
	@Nullable
	public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
		data = super.onInitialSpawn(difficulty, data);

		if(this.world.rand.nextInt(100) == 0) {
			EntitySkeleton skeleton = new EntitySkeleton(this.world);
			skeleton.setLocationAndAngles(this.posX, this.posY, this.posZ, this.rotationYaw, 0.0F);
			skeleton.onInitialSpawn(difficulty, (IEntityLivingData)null);
			this.world.spawnEntity(skeleton);
			skeleton.startRiding(this);
		}

		if(data == null) {
			data = new EntitySpider.GroupData();

			if(this.world.getDifficulty() == EnumDifficulty.HARD && this.world.rand.nextFloat() < 0.1F * difficulty.getClampedAdditionalDifficulty()) {
				((EntitySpider.GroupData)data).setRandomEffect(this.world.rand);
			}
		}

		if(data instanceof EntitySpider.GroupData) {
			Potion potion = ((EntitySpider.GroupData)data).effect;

			if(potion != null) {
				this.addPotionEffect(new PotionEffect(potion, Integer.MAX_VALUE));
			}
		}

		return data;
	}

	@Override
	public float getEyeHeight() {
		return 0.65F;
	}

	static class AISpiderAttack extends EntityAIAttackMelee {
		public AISpiderAttack(BetterSpiderEntity spider) {
			super(spider, 1.0D, true);
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

			float brightness = this.attacker.getBrightness();

			if(brightness >= 0.5F && this.attacker.getRNG().nextInt(100) == 0) {
				this.attacker.setAttackTarget((EntityLivingBase)null);
				return false;
			} else {
				return super.shouldContinueExecuting();
			}
		}

		@Override
		protected double getAttackReachSqr(EntityLivingBase attackTarget) {
			return (double)(4.0F + attackTarget.width);
		}
	}

	static class AISpiderTarget<T extends EntityLivingBase> extends EntityAINearestAttackableTarget<T> {
		public AISpiderTarget(BetterSpiderEntity spider, Class<T> classTarget) {
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

			float brightness = this.taskOwner.getBrightness();
			return brightness >= 0.5F ? false : super.shouldExecute();
		}
	}
}
