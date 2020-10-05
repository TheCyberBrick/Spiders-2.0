package tcb.spiderstpo.common;

import net.minecraft.entity.*;
import net.minecraft.entity.monster.CaveSpiderEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import tcb.spiderstpo.client.EntityRenderers;
import tcb.spiderstpo.common.entity.mob.BetterCaveSpiderEntity;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Mod("spiderstpo")
public class SpiderMod {
	private static final DeferredRegister<EntityType<?>> ENTITIES = new DeferredRegister<>(ForgeRegistries.ENTITIES, "spiderstpo");

	public static final RegistryObject<EntityType<BetterSpiderEntity>> BETTER_SPIDER = ENTITIES.register("better_spider", () -> {
		EntityType<BetterSpiderEntity> type = EntityType.Builder.<BetterSpiderEntity>create(BetterSpiderEntity::new, EntityClassification.MONSTER).size(0.95f, 0.85f).build("spiderstpo:better_spider");
		return type;
	});

	public static final RegistryObject<EntityType<BetterCaveSpiderEntity>> BETTER_CAVE_SPIDER = ENTITIES.register("better_cave_spider", () -> {
		EntityType<BetterCaveSpiderEntity> type = EntityType.Builder.<BetterCaveSpiderEntity>create(BetterCaveSpiderEntity::new, EntityClassification.MONSTER).size(0.7F, 0.5F).build("spiderstpo:better_cave_spider");
		return type;
	});
	private final Map<IWorld, Set<ChunkPos>> loadedChunks = new WeakHashMap<>();

	public SpiderMod() {
		ModLoadingContext loadingContext = ModLoadingContext.get();

		loadingContext.registerConfig(ModConfig.Type.COMMON, Config.COMMON, "spiders-2.0.toml");

		FMLJavaModLoadingContext fmlContext = FMLJavaModLoadingContext.get();

		fmlContext.getModEventBus().addListener(this::onClientSetup);
		ENTITIES.register(fmlContext.getModEventBus());
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		EntityRenderers.register();
	}

	@SubscribeEvent
	public void onChunkLoad(final ChunkEvent.Load event) {
		if(!event.getWorld().isRemote()) {
			this.loadedChunks.computeIfAbsent(event.getWorld(), w -> new HashSet<ChunkPos>()).add(event.getChunk().getPos());
		}
	}

	@SubscribeEvent
	public void onChunkUnload(final ChunkEvent.Unload event) {
		if(!event.getWorld().isRemote()) {
			Set<ChunkPos> chunks = this.loadedChunks.get(event.getWorld());
			if(chunks != null) {
				chunks.remove(event.getChunk().getPos());
			}
		}
	}

	@SubscribeEvent
	public void onWorldUnload(final WorldEvent.Unload event) {
		this.loadedChunks.remove(event.getWorld());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onSpawnEntity(final LivingSpawnEvent.SpecialSpawn event) {
		SpawnReason reason = event.getSpawnReason();

		if(Config.REPLACE_ANY_SPAWNS.get() || (Config.REPLACE_NATURAL_SPAWNS.get() && reason != SpawnReason.COMMAND)) {
			Entity entity = event.getEntity();

			if(!entity.getEntityWorld().isRemote && this.replaceSpawn(entity, reason)) {
				event.setCanceled(true);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onAddEntity(final EntityJoinWorldEvent event) {
		if(Config.REPLACE_ANY_SPAWNS.get()) {
			Entity entity = event.getEntity();

			if(!entity.getEntityWorld().isRemote && this.replaceSpawn(entity, null)) {
				event.setCanceled(true);
			}
		}
	}

	private boolean replaceSpawn(Entity entity, @Nullable SpawnReason reason) {
		World world = entity.getEntityWorld();

		Entity replacement = null;

		if(entity.getClass().equals(SpiderEntity.class)) {
			replacement = new BetterSpiderEntity(world);
		} else if(entity.getClass().equals(CaveSpiderEntity.class)) {
			replacement = new BetterCaveSpiderEntity(world);
		}

		if(replacement != null) {
			replacement.copyDataFromOld(entity);
			replacement.setLocationAndAngles(entity.getPosX(), entity.getPosY(), entity.getPosZ(), entity.rotationYaw, entity.rotationPitch);

			if(reason != null) {
				((MobEntity) replacement).onInitialSpawn(world, world.getDifficultyForLocation(entity.getPosition()), reason, null, null);
			}

			replacement.forceSpawn = entity.forceSpawn;

			//Adding an entity while loading to an unloaded chunk causes a deadlock
			Set<ChunkPos> loadedChunks;
			if(reason != null || (loadedChunks = this.loadedChunks.get(world)) != null && loadedChunks.contains(new ChunkPos(MathHelper.floor(replacement.getPosX() / 16.0D), MathHelper.floor(replacement.getPosZ() / 16.0D)))) {
				entity.remove();
				world.addEntity(replacement);
				return true;
			} else if(world instanceof ServerWorld) {
				entity.remove();
				((ServerWorld) world).addEntityIfNotDuplicate(replacement);
				return true;
			}
		}

		return false;
	}
}
