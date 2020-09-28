package tcb.spiderstpo.common;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.entity.monster.CaveSpiderEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import tcb.spiderstpo.client.EntityRenderers;
import tcb.spiderstpo.common.entity.mob.BetterCaveSpiderEntity;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

@Mod("spiderstpo")
public class SpiderMod {
	public static final boolean DEBUG = false;

	private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, "spiderstpo");

	public static final RegistryObject<EntityType<BetterSpiderEntity>> BETTER_SPIDER = ENTITIES.register("better_spider", () -> {
		EntityType<BetterSpiderEntity> type = EntityType.Builder.<BetterSpiderEntity>create(BetterSpiderEntity::new, EntityClassification.MONSTER).size(0.95f, 0.85f).func_233606_a_(8).build("spiderstpo:better_spider");
		GlobalEntityTypeAttributes.put(type, BetterSpiderEntity.getAttributeMap().func_233813_a_());
		return type;
	});

	public static final RegistryObject<EntityType<BetterCaveSpiderEntity>> BETTER_CAVE_SPIDER = ENTITIES.register("better_cave_spider", () -> {
		EntityType<BetterCaveSpiderEntity> type = EntityType.Builder.<BetterCaveSpiderEntity>create(BetterCaveSpiderEntity::new, EntityClassification.MONSTER).size(0.7F, 0.5F).func_233606_a_(8).build("spiderstpo:better_cave_spider");
		GlobalEntityTypeAttributes.put(type, BetterCaveSpiderEntity.getAttributeMap().func_233813_a_());
		return type;
	});

	public SpiderMod() {
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
		ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		EntityRenderers.register();
	}

	private final Map<IWorld, Set<ChunkPos>> loadedChunks = new WeakHashMap<>();

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
	public void onSpawnEntity(final EntityJoinWorldEvent event) {
		World world = event.getWorld();

		if(!world.isRemote) {
			Entity entity = event.getEntity();

			Entity replacement = null;

			if(entity.getClass().equals(SpiderEntity.class)) {
				replacement = new BetterSpiderEntity(world);
			} else if(entity.getClass().equals(CaveSpiderEntity.class)) {
				replacement = new BetterCaveSpiderEntity(world);
			}

			if(replacement != null) {
				replacement.copyDataFromOld(entity);
				replacement.setLocationAndAngles(entity.getPosX(), entity.getPosY(), entity.getPosZ(), entity.rotationYaw, entity.rotationPitch);

				//TODO onInitialSpawn?

				replacement.forceSpawn = entity.forceSpawn;

				entity.remove();

				event.setCanceled(true);

				//Adding an entity while loading to an unloaded chunk causes a deadlock
				Set<ChunkPos> loadedChunks = this.loadedChunks.get(event.getWorld());
				if(loadedChunks != null && loadedChunks.contains(new ChunkPos(MathHelper.floor(replacement.getPosX() / 16.0D), MathHelper.floor(replacement.getPosZ() / 16.0D)))) {
					world.addEntity(replacement);
				} else if(world instanceof ServerWorld) {
					((ServerWorld) world).addEntityIfNotDuplicate(replacement);
				}
			}
		}
	}
}
