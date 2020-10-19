package tcb.spiderstpo.common;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import tcb.spiderstpo.client.ClientSetup;

@Mod("spiderstpo")
public class SpiderMod {
	public SpiderMod() {
		ModLoadingContext loadingContext = ModLoadingContext.get();

		loadingContext.registerConfig(ModConfig.Type.COMMON, Config.COMMON, "spiders-2.0.toml");

		FMLJavaModLoadingContext fmlContext = FMLJavaModLoadingContext.get();

		fmlContext.getModEventBus().addListener(this::onCommonSetup);
		fmlContext.getModEventBus().addListener(this::onClientSetup);
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		ClientSetup.run();
	}

	private void onCommonSetup(final FMLCommonSetupEvent event) {
		CommonSetup.run();
	}
}
