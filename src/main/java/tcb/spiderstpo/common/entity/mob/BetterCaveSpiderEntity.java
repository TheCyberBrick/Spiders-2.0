package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import tcb.spiderstpo.common.SpiderMod;

public class BetterCaveSpiderEntity extends BetterSpiderEntity {
	public BetterCaveSpiderEntity(EntityType<? extends AbstractClimberEntity> type, World world) {
		super(type, world);
	}

	public BetterCaveSpiderEntity(World world) {
		super(SpiderMod.BETTER_CAVE_SPIDER.get(), world);
	}

	public static AttributeModifierMap.MutableAttribute getAttributeMap() {
		return BetterSpiderEntity.getAttributeMap().func_233815_a_(Attributes.field_233818_a_, 12.0D);
	}

	@Override
	public float getVerticalOffset(float partialTicks) {
		return 0.225f;
	}
	
	@Override
	protected ResourceLocation getLootTable() {
		return EntityType.CAVE_SPIDER.getLootTable();
	}

	@Override
	public boolean attackEntityAsMob(Entity entityIn) {
		if(super.attackEntityAsMob(entityIn)) {
			if(entityIn instanceof LivingEntity) {
				int i = 0;
				if(this.world.getDifficulty() == Difficulty.NORMAL) {
					i = 7;
				} else if (this.world.getDifficulty() == Difficulty.HARD) {
					i = 15;
				}

				if(i > 0) {
					((LivingEntity)entityIn).addPotionEffect(new EffectInstance(Effects.POISON, i * 20, 0));
				}
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	@Nullable
	public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
		return spawnDataIn;
	}

	@Override
	protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
		return 0.45F;
	}
}
