package tcb.spiderstpo.client;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

public class BetterCaveSpiderRenderer extends BetterSpiderRenderer {
	private static final ResourceLocation CAVE_SPIDER_TEXTURES = new ResourceLocation("textures/entity/spider/cave_spider.png");

	public BetterCaveSpiderRenderer(EntityRendererManager renderManagerIn) {
		super(renderManagerIn);
		this.shadowSize *= 0.7F;
	}

	@Override
	protected void preRenderCallback(BetterSpiderEntity entitylivingbaseIn, MatrixStack matrixStackIn, float partialTickTime) {
		matrixStackIn.scale(0.7F, 0.7F, 0.7F);
	}

	@Override
	public ResourceLocation getEntityTexture(BetterSpiderEntity entity) {
		return CAVE_SPIDER_TEXTURES;
	}
}
