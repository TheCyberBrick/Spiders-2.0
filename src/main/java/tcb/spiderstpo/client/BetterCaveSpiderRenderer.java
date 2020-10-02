package tcb.spiderstpo.client;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

public class BetterCaveSpiderRenderer extends BetterSpiderRenderer {
	private static final ResourceLocation CAVE_SPIDER_TEXTURES = new ResourceLocation("textures/entity/spider/cave_spider.png");

	public BetterCaveSpiderRenderer(RenderManager renderManagerIn) {
		super(renderManagerIn);
		this.shadowSize *= 0.7F;
	}

	@Override
	protected void preRenderCallback(BetterSpiderEntity entitylivingbaseIn, float partialTickTime) {
		GlStateManager.scale(0.7F, 0.7F, 0.7F);
	}

	@Override
	public ResourceLocation getEntityTexture(BetterSpiderEntity entity) {
		return CAVE_SPIDER_TEXTURES;
	}
}
