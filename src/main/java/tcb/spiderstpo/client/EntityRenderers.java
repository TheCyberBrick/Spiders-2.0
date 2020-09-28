package tcb.spiderstpo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import tcb.spiderstpo.common.SpiderMod;

public class EntityRenderers {
	public static void register() {
		EntityRendererManager renderManager = Minecraft.getInstance().getRenderManager();

		renderManager.register(SpiderMod.BETTER_SPIDER.get(), new BetterSpiderRenderer(renderManager));
		renderManager.register(SpiderMod.BETTER_CAVE_SPIDER.get(), new BetterCaveSpiderRenderer(renderManager));
	}
}
