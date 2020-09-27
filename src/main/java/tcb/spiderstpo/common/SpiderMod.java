package tcb.spiderstpo.common;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import tcb.spiderstpo.client.BetterSpiderRenderer;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

@Mod("spiderstpo")
public class SpiderMod {
	private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, "spiderstpo");

	public static final RegistryObject<EntityType<BetterSpiderEntity>> BETTER_SPIDER = ENTITIES.register("better_spider", () -> {
		EntityType<BetterSpiderEntity> type = EntityType.Builder.create(BetterSpiderEntity::new, EntityClassification.MONSTER).size(0.95f, 0.85f).build("spiderstpo:better_spider");
		GlobalEntityTypeAttributes.put(type, BetterSpiderEntity.func_234305_eI_().func_233813_a_());
		return type;
	});

	public SpiderMod() {
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
		ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		EntityRendererManager renderManager = event.getMinecraftSupplier().get().getRenderManager();

		renderManager.register(BETTER_SPIDER.get(), new BetterSpiderRenderer(renderManager));
	}
}
