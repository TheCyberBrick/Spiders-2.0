package tcb.spiderstpo.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
	public static final ForgeConfigSpec COMMON;

	public static final ForgeConfigSpec.BooleanValue REPLACE_NATURAL_SPAWNS;
	public static final ForgeConfigSpec.BooleanValue REPLACE_ANY_SPAWNS;
	public static final ForgeConfigSpec.BooleanValue PATH_FINDER_DEBUG_PREVIEW;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		REPLACE_NATURAL_SPAWNS = builder
				.comment("Whether natural spider spawns should be replaced. This includes e.g. mob spawners and spawn eggs, but not commands.")
				.define("replace_natural_spawns", true);

		REPLACE_ANY_SPAWNS = builder
				.comment("Whether any spider spawns should be replaced. This also applies retroactively to already existing spiders.")
				.define("replace_any_spawns", true);

		PATH_FINDER_DEBUG_PREVIEW = builder
				.worldRestart()
				.comment("Whether the path finder debug preview should be enabled.")
				.define("path_finder_debug_preview", false);

		COMMON = builder.build();
	}
}
