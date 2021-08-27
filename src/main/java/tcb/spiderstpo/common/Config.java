package tcb.spiderstpo.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
	public static final ForgeConfigSpec COMMON;

	public static final ForgeConfigSpec.BooleanValue PREVENT_CLIMBING_IN_RAIN;

	public static final ForgeConfigSpec.BooleanValue PATH_FINDER_DEBUG_PREVIEW;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		PREVENT_CLIMBING_IN_RAIN = builder.comment("Whether spiders should be unable to climb when exposed to rain")
				.define("prevent_climbing_in_rain", true);

		PATH_FINDER_DEBUG_PREVIEW = builder
				.worldRestart()
				.comment("Whether the path finder debug preview should be enabled.")
				.define("path_finder_debug_preview", false);

		COMMON = builder.build();
	}
}
