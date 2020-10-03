package tcb.spiderstpo.common;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import tcb.spiderstpo.common.entity.mob.BetterCaveSpiderEntity;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

public class Entities {
	public static void register() {
		registerEntity(BetterSpiderEntity.class, "better_spider");
		registerEntity(BetterCaveSpiderEntity.class, "better_cave_spider");
	}

	private static int id = 0;

	private static void registerEntity(Class<? extends Entity> entityClass, String name) {
		EntityRegistry.registerModEntity(new ResourceLocation("spiderstpo", name), entityClass, "spiderstpo." + name, id, SpiderMod.instance, 80, 3, true);
		id++;
	}
}
