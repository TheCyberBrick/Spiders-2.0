package tcb.spiderstpo.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import tcb.spiderstpo.common.entity.mob.BetterCaveSpiderEntity;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;
import tcb.spiderstpo.compat.mobends.MoBendsCompat;

@Mod(modid = "spiderstpo", name = "Spiders 2.0", acceptedMinecraftVersions = "[1.12.2]", useMetadata = true)
public class SpiderMod {
	public static final boolean DEBUG = false;

	@Instance("spiderstpo")
	public static SpiderMod instance;

	@SidedProxy(modId = "spiderstpo", clientSide = "tcb.spiderstpo.client.ClientProxy", serverSide = "tcb.spiderstpo.common.CommonProxy")
	public static CommonProxy proxy;

	@EventHandler
	public static void preInit(FMLPreInitializationEvent event) {
		ConfigManager.sync("spiderstpo", net.minecraftforge.common.config.Config.Type.INSTANCE);

		MinecraftForge.EVENT_BUS.register(SpiderMod.class);
		MinecraftForge.EVENT_BUS.register(Config.class);

		Entities.register();

		proxy.preInit();
	}

	@EventHandler
	public static void init(FMLInitializationEvent event) {
		MoBendsCompat.init();
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onSpawnEntity(final LivingSpawnEvent.SpecialSpawn event) {
		if(Config.replaceAnySpawns || Config.replaceNaturalSpawns) {
			Entity entity = event.getEntity();

			if(!entity.getEntityWorld().isRemote && replaceSpawn(entity, true)) {
				event.setCanceled(true);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onAddEntity(final EntityJoinWorldEvent event) {
		if(Config.replaceAnySpawns) {
			Entity entity = event.getEntity();

			if(!entity.getEntityWorld().isRemote && replaceSpawn(entity, false)) {
				event.setCanceled(true);
			}
		}
	}

	private static boolean replaceSpawn(Entity entity, boolean newSpawn) {
		World world = entity.getEntityWorld();

		Entity replacement = null;

		if(entity.getClass().equals(EntitySpider.class)) {
			replacement = new BetterSpiderEntity(world);
		} else if(entity.getClass().equals(EntityCaveSpider.class)) {
			replacement = new BetterCaveSpiderEntity(world);
		}

		if(replacement != null) {
			replacement.readFromNBT(entity.writeToNBT(new NBTTagCompound()));
			replacement.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, entity.rotationPitch);

			if(newSpawn && replacement instanceof EntityLiving) {
				((EntityLiving) replacement).onInitialSpawn(world.getDifficultyForLocation(entity.getPosition()), null);
			}

			replacement.forceSpawn = entity.forceSpawn;

			world.spawnEntity(replacement);
			return true;
		}

		return false;
	}
}
