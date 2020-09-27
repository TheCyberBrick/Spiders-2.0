package tcb.spiderstpo.client;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.layers.SpiderEyesLayer;
import net.minecraft.client.renderer.entity.model.SpiderModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

@OnlyIn(Dist.CLIENT)
public class BetterSpiderRenderer extends AbstractClimberRenderer<BetterSpiderEntity, SpiderModel<BetterSpiderEntity>> {
	private static final ResourceLocation SPIDER_TEXTURES = new ResourceLocation("textures/entity/spider/spider.png");

	public BetterSpiderRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn, new SpiderModel<>(), 0.8F);
		this.addLayer(new SpiderEyesLayer<>(this));
	}

	@Override
	protected float getDeathMaxRotation(BetterSpiderEntity entityLivingBaseIn) {
		return 180.0F;
	}

	@Override
	public ResourceLocation getEntityTexture(BetterSpiderEntity entity) {
		return SPIDER_TEXTURES;
	}
}