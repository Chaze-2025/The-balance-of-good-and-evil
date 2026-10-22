package dev.chaze.balance.ward;

import dev.chaze.balance.BalanceMod;
import dev.chaze.balance.block.entity.VigilTorchBlockEntity;
import dev.chaze.balance.config.WardConfig;
import dev.chaze.balance.registry.ModBlocks;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

/** Server-thread adapter. Saved blocks are authoritative; the index is disposable. */
public final class VigilWards {
    private static final Map<ServerWorld, WardIndex> WORLDS = new IdentityHashMap<>();
    private static WardConfig config = new WardConfig(WardConfig.DEFAULT_RADIUS);

    private VigilWards() {}

    public static double radius() {
        return config.radius();
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            WORLDS.clear();
            Path path = FabricLoader.getInstance().getConfigDir().resolve("balance-wards.properties");
            try {
                config = WardConfig.load(path);
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Cannot load ward settings at " + path
                        + "; fix the file before starting the server", exception);
            }
            BalanceMod.LOGGER.info("Vigil wards configured with radius {} blocks", radius());
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof VigilTorchBlockEntity) {
                BlockPos pos = entity.getPos();
                WORLDS.computeIfAbsent(world, ignored -> new WardIndex())
                        .upsert(new WardSource(pos.getX(), pos.getY(), pos.getZ(), radius()));
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof VigilTorchBlockEntity) {
                WardIndex index = WORLDS.get(world);
                if (index != null) {
                    BlockPos pos = entity.getPos();
                    // Fabric can announce a replacement's load before the old
                    // marker's unload. Do not erase the new source at the same pos.
                    WorldChunk chunk = world.getChunkManager()
                            .getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk != null
                            && chunk.getBlockEntities().get(pos) instanceof VigilTorchBlockEntity replacement
                            && replacement != entity && !replacement.isRemoved()) {
                        return;
                    }
                    index.remove(pos.getX(), pos.getY(), pos.getZ());
                }
            }
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> WORLDS.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.clear());
        ServerTickEvents.END_WORLD_TICK.register(VigilWards::tick);
    }

    /** A loaded-area query for future server systems. Does not load or await chunks. */
    public static boolean isProtected(ServerWorld world, double x, double y, double z) {
        WardIndex index = WORLDS.get(world);
        if (index == null) {
            return false;
        }
        for (WardSource source : index.sources()) {
            if (isActive(world, index, source) && source.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static void tick(ServerWorld world) {
        // Server ticks continue across /tick freeze; a frozen world-time value
        // divisible by 20 must not accidentally turn this into an every-tick scan.
        if (world.getServer().getTicks() % WardEffectPolicy.REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        WardIndex index = WORLDS.get(world);
        if (index == null) {
            return;
        }
        Set<Integer> affected = new HashSet<>();
        for (WardSource source : index.sources()) {
            if (!isActive(world, index, source)) {
                continue;
            }
            double x = source.x() + 0.5;
            double y = source.y() + 0.5;
            double z = source.z() + 0.5;
            double radius = source.radius();
            // Box intersections are strict; padding admits feet exactly on the top boundary.
            // The sphere predicate below still uses the precise inclusive radius.
            Box bounds = new Box(x - radius, y - radius, z - radius,
                    x + radius, y + radius, z + radius).expand(1.0e-7);
            for (ZombieEntity zombie : world.getEntitiesByClass(ZombieEntity.class, bounds,
                    entity -> entity.isAlive() && entity.getType() == EntityType.ZOMBIE
                            && !affected.contains(entity.getId())
                            && source.contains(entity.getX(), entity.getY(), entity.getZ()))) {
                if (affected.add(zombie.getId())) {
                    applyWeakening(zombie);
                }
            }
        }
    }

    private static boolean isActive(ServerWorld world, WardIndex index, WardSource source) {
        // getWorldChunk is a non-blocking lookup; unlike world.getBlockState it cannot
        // request an absent chunk. Unload events remove the source. A transiently absent
        // chunk is skipped rather than losing a source before its load has completed.
        WorldChunk chunk = world.getChunkManager().getWorldChunk(source.x() >> 4, source.z() >> 4);
        if (chunk == null) {
            return false;
        }
        BlockState state = chunk.getBlockState(new BlockPos(source.x(), source.y(), source.z()));
        if (state.isOf(ModBlocks.VIGIL_TORCH) || state.isOf(ModBlocks.WALL_VIGIL_TORCH)) {
            return true;
        }
        index.remove(source.x(), source.y(), source.z());
        return false;
    }

    private static void applyWeakening(ZombieEntity zombie) {
        StatusEffectInstance existing = zombie.getStatusEffect(StatusEffects.SLOWNESS);
        if (WardEffectPolicy.shouldRefresh(zombie.getType() == EntityType.ZOMBIE,
                existing == null ? -1 : existing.getAmplifier(),
                existing == null ? 0 : existing.getDuration())) {
            zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    WardEffectPolicy.EFFECT_DURATION_TICKS, WardEffectPolicy.AMPLIFIER,
                    true, false, true));
        }
    }
}
