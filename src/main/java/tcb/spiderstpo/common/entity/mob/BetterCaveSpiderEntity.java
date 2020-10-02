package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootTableList;

public class BetterCaveSpiderEntity extends BetterSpiderEntity {
	public BetterCaveSpiderEntity(World world) {
		super(world);
		this.setSize(0.7f, 0.5f);
	}

	@Override
	public String getName() {
		if(this.hasCustomName()) {
            return this.getCustomNameTag();
        } else {
            return I18n.translateToLocal("entity.CaveSpider.name");
        }
	}
	
	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(12.0D);
	}

	@Override
	public float getVerticalOffset(float partialTicks) {
		return 0.225f;
	}

	@Override
	@Nullable
	protected ResourceLocation getLootTable() {
		return LootTableList.ENTITIES_CAVE_SPIDER;
	}

	@Override
	public boolean attackEntityAsMob(Entity entityIn) {
		if(super.attackEntityAsMob(entityIn)) {
			if(entityIn instanceof EntityLivingBase) {
				int poisonStrength = 0;

				if(this.world.getDifficulty() == EnumDifficulty.NORMAL) {
					poisonStrength = 7;
				} else if (this.world.getDifficulty() == EnumDifficulty.HARD) {
					poisonStrength = 15;
				}

				if(poisonStrength > 0) {
					((EntityLivingBase)entityIn).addPotionEffect(new PotionEffect(MobEffects.POISON, poisonStrength * 20, 0));
				}
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	@Nullable
	public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
		return data;
	}

	@Override
	public float getEyeHeight() {
		return 0.45F;
	}
}
