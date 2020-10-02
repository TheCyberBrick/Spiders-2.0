package tcb.spiderstpo.client;

import net.minecraftforge.fml.client.registry.RenderingRegistry;
import tcb.spiderstpo.common.entity.mob.BetterCaveSpiderEntity;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

public class EntityRenderers {
	public static void register() {
		RenderingRegistry.registerEntityRenderingHandler(BetterSpiderEntity.class, BetterSpiderRenderer::new);
		RenderingRegistry.registerEntityRenderingHandler(BetterCaveSpiderEntity.class, BetterCaveSpiderRenderer::new);
	}
}
