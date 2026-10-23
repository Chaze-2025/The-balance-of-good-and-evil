package dev.chaze.balance.nightfall;

import dev.chaze.balance.BalanceMod;
import dev.chaze.balance.config.NightfallConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Server-thread queries use live world time; only notification baselines are cached. */
public final class NightfallService {
    private static final Map<ServerWorld, NightfallTracker> OBSERVATIONS = new IdentityHashMap<>();
    private static NightfallConfig config = NightfallConfig.defaults();

    /** Fired synchronously on the server thread, once for each observed direct phase change. */
    public static final Event<PhaseChanged> PHASE_CHANGED = EventFactory.createArrayBacked(
            PhaseChanged.class, callbacks -> (world, previous, current) -> {
                for (PhaseChanged callback : callbacks) {
                    callback.onPhaseChanged(world, previous, current);
                }
            });

    private NightfallService() {}

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            OBSERVATIONS.clear();
            Path path = FabricLoader.getInstance().getConfigDir().resolve("balance-nightfall.properties");
            try {
                config = NightfallConfig.load(path);
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Cannot load Nightfall settings at " + path
                        + "; fix the file before starting the server", exception);
            }
            BalanceMod.LOGGER.info("Nightfall enabled={}, supported dimension=minecraft:overworld, observation interval={} ticks",
                    config.enabled(), config.updateIntervalTicks());
        });
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (isEnabled(world)) {
                NightfallTracker tracker = new NightfallTracker();
                tracker.observe(state(world)); // Initialization establishes a baseline, never a transition.
                OBSERVATIONS.put(world, tracker);
            }
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> OBSERVATIONS.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> OBSERVATIONS.clear());
        ServerTickEvents.END_WORLD_TICK.register(NightfallService::observe);
        NightfallCommands.initialize();
    }

    /** Effective enablement: global setting plus the supported dimension policy. */
    public static boolean isEnabled(ServerWorld world) {
        requireServerThread(world);
        return config.enabled() && supports(world);
    }

    /** A fresh snapshot, including normalized time even in an inactive dimension. */
    public static NightfallState state(ServerWorld world) {
        requireServerThread(world);
        return NightfallState.from(world.getTimeOfDay(), config, supports(world));
    }

    /** An inactive world has no Nightfall phase; it is not treated as Overworld daylight. */
    public static Optional<NightfallPhase> phase(ServerWorld world) {
        return isEnabled(world)
                ? Optional.of(NightfallClock.phaseAt(world.getTimeOfDay(), config)) : Optional.empty();
    }

    /** Base threat potential only. This never overrides ward or sanctuary rules. */
    public static double baseIntensity(ServerWorld world) {
        return isEnabled(world)
                ? NightfallClock.intensity(NightfallClock.phaseAt(world.getTimeOfDay(), config), config) : 0.0;
    }

    /** Phase classification; custom intensity ordering does not redefine nighttime. */
    public static boolean isElevatedNighttimeDanger(ServerWorld world) {
        return isEnabled(world) && NightfallClock.phaseAt(world.getTimeOfDay(), config).isNighttime();
    }

    /** Read-only comparison with the last scheduled observation; never consumes an event. */
    public static boolean hasPhaseChangedSinceLastObservation(ServerWorld world) {
        requireServerThread(world);
        NightfallTracker tracker = OBSERVATIONS.get(world);
        return tracker != null && tracker.hasPhaseChangedSinceLastObservation(state(world));
    }

    private static void observe(ServerWorld world) {
        if (!isEnabled(world) || Math.floorMod(world.getServer().getTicks(), config.updateIntervalTicks()) != 0) {
            return;
        }
        NightfallState current = state(world);
        NightfallTracker tracker = OBSERVATIONS.computeIfAbsent(world, ignored -> new NightfallTracker());
        tracker.observe(current).ifPresent(transition -> {
            BalanceMod.LOGGER.debug("Nightfall {}: {} -> {} at {}", world.getRegistryKey().getValue(),
                    transition.previous(), transition.current(), current.timeOfDay());
            PHASE_CHANGED.invoker().onPhaseChanged(world, transition.previous(), current);
        });
    }

    private static boolean supports(ServerWorld world) {
        // Add concrete dimension-specific rules here when a future feature requires them.
        return world.getRegistryKey().equals(World.OVERWORLD);
    }

    private static void requireServerThread(ServerWorld world) {
        Objects.requireNonNull(world, "world");
        if (!world.getServer().isOnThread()) {
            throw new IllegalStateException("Nightfall world queries must run on the server thread");
        }
    }

    @FunctionalInterface
    public interface PhaseChanged {
        void onPhaseChanged(ServerWorld world, NightfallPhase previous, NightfallState current);
    }
}
