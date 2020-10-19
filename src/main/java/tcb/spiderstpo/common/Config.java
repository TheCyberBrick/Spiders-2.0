package tcb.spiderstpo.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
	public static final ForgeConfigSpec COMMON;

	public static final ForgeConfigSpec.BooleanValue PATH_FINDER_DEBUG_PREVIEW;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		PATH_FINDER_DEBUG_PREVIEW = builder
				.worldRestart()
				.comment("Whether the path finder debug preview should be enabled.")
				.define("path_finder_debug_preview", false);

		COMMON = builder.build();
	}
}
