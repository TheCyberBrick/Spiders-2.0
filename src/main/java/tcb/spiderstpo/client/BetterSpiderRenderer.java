package tcb.spiderstpo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelSpider;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

public class BetterSpiderRenderer extends AbstractClimberRenderer<BetterSpiderEntity> {
	private static class LayerSpiderEyes<T extends BetterSpiderEntity> implements LayerRenderer<T> {
		private static final ResourceLocation SPIDER_EYES = new ResourceLocation("textures/entity/spider_eyes.png");
		private final BetterSpiderRenderer spiderRenderer;

		public LayerSpiderEyes(BetterSpiderRenderer spiderRendererIn) {
			this.spiderRenderer = spiderRendererIn;
		}

		@Override
		public void doRenderLayer(T entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
			this.spiderRenderer.bindTexture(SPIDER_EYES);
			GlStateManager.enableBlend();
			GlStateManager.disableAlpha();
			GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

			if(entitylivingbaseIn.isInvisible()) {
				GlStateManager.depthMask(false);
			} else {
				GlStateManager.depthMask(true);
			}

			int i = 61680;
			int j = i % 65536;
			int k = i / 65536;
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)j, (float)k);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			Minecraft.getMinecraft().entityRenderer.setupFogColor(true);
			this.spiderRenderer.getMainModel().render(entitylivingbaseIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
			Minecraft.getMinecraft().entityRenderer.setupFogColor(false);
			i = entitylivingbaseIn.getBrightnessForRender();
			j = i % 65536;
			k = i / 65536;
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)j, (float)k);
			this.spiderRenderer.setLightmap(entitylivingbaseIn);
			GlStateManager.disableBlend();
			GlStateManager.enableAlpha();
		}

		@Override
		public boolean shouldCombineTextures() {
			return false;
		}
	}

	private static final ResourceLocation SPIDER_TEXTURES = new ResourceLocation("textures/entity/spider/spider.png");

	public BetterSpiderRenderer(RenderManager renderManagerIn) {
		super(renderManagerIn, new ModelSpider(), 0.8F);
		this.addLayer(new LayerSpiderEyes<>(this));
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