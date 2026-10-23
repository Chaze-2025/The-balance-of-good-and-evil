package dev.chaze.balance.gametest;

import dev.chaze.balance.nightfall.NightfallClock;
import dev.chaze.balance.nightfall.NightfallPhase;
import dev.chaze.balance.nightfall.NightfallService;
import dev.chaze.balance.nightfall.NightfallState;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Real-world tests prepared for an owner-authorized GameTest run. Clock-mutating
 * checks share one test in a separate batch, never parallel tests in one world.
 * The sleeping fixture uses Minecraft's in-memory server player, not a real client.
 */
public final class NightfallGameTests implements FabricGameTest {
    private static final String BATCH = "balance-nightfall-runtime";
    private static final List<ObservedTransition> TRANSITIONS = new ArrayList<>();
    private static final Map<ServerWorld, Long> ORIGINAL_TIMES = new IdentityHashMap<>();
    private static ServerWorld observedWorld;
    private static ServerPlayerEntity sleeper;
    private static boolean originalDaylightCycle;
    private static boolean originalWeatherCycle;
    private static int originalSleepingPercentage;

    static {
        NightfallService.PHASE_CHANGED.register((world, previous, current) -> {
            if (world == observedWorld) {
                TRANSITIONS.add(new ObservedTransition(previous, current.phase().orElseThrow()));
            }
        });
    }

    @BeforeBatch(batchId = BATCH)
    public void saveClockAndRules(ServerWorld world) {
        ORIGINAL_TIMES.clear();
        // Vanilla /time commands update every loaded dimension, not only their source world.
        for (ServerWorld dimension : world.getServer().getWorlds()) {
            ORIGINAL_TIMES.put(dimension, dimension.getTimeOfDay());
        }
        originalDaylightCycle = world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
        originalWeatherCycle = world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE);
        originalSleepingPercentage = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        observedWorld = world;
        TRANSITIONS.clear();
    }

    @AfterBatch(batchId = BATCH)
    public void restoreClockRulesAndPlayer(ServerWorld world) {
        try {
            if (sleeper != null) {
                ServerPlayerEntity player = sleeper;
                sleeper = null;
                try {
                    // Standard disconnect cleans the embedded connection and PlayerManager.
                    player.networkHandler.disconnect(Text.literal("Nightfall GameTest fixture finished"));
                } finally {
                    if (world.getServer().getPlayerManager().getPlayer(player.getUuid()) == player) {
                        world.getServer().getPlayerManager().remove(player);
                    }
                }
            }
        } finally {
            ORIGINAL_TIMES.forEach(ServerWorld::setTimeOfDay);
            ORIGINAL_TIMES.clear();
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(originalDaylightCycle, world.getServer());
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(originalWeatherCycle, world.getServer());
            world.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE)
                    .set(originalSleepingPercentage, world.getServer());
            observedWorld = null;
            TRANSITIONS.clear();
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 1200)
    public void authoritativeTimeCommandsTransitionsFreezeAndSleep(TestContext context) {
        ServerWorld world = context.getWorld();
        context.assertTrue(world.getRegistryKey().equals(World.OVERWORLD), "Nightfall fixture must use the Overworld");
        context.assertTrue(NightfallService.isEnabled(world), "Nightfall fixture requires the default enabled configuration");
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, world.getServer());
        world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, world.getServer());
        world.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE).set(100, world.getServer());

        checkLiveBoundaries(context);
        checkCommandsAndDiagnosticFeedback(context);
        checkInactiveDimensions(context);
        world.setTimeOfDay(1000);

        // The default observation period is 20 ticks. Leave two full periods for each
        // baseline/change so the test does not depend on the server's modulo offset.
        context.runAtTick(40, () -> {
            TRANSITIONS.clear();
            command(world, "time set 18000");
            expectPhase(context, NightfallPhase.DEEP_NIGHT, 1.0);
            context.assertTrue(NightfallService.hasPhaseChangedSinceLastObservation(world),
                    "Fresh time jump must differ from the daylight observation baseline");
            NightfallService.state(world);
            NightfallService.phase(world);
            context.assertTrue(TRANSITIONS.isEmpty(), "Read-only queries must not publish phase events");
            context.assertTrue(NightfallService.hasPhaseChangedSinceLastObservation(world),
                    "Read-only queries must not consume the pending transition");
        });
        context.runAtTick(80, () -> {
            context.assertEquals(List.copyOf(TRANSITIONS),
                    List.of(new ObservedTransition(NightfallPhase.DAYLIGHT, NightfallPhase.DEEP_NIGHT)),
                    "one direct transition for a jump across dusk and night");
            context.assertFalse(NightfallService.hasPhaseChangedSinceLastObservation(world),
                    "Scheduled observation must advance the baseline");
        });
        context.runAtTick(120, () -> {
            context.assertEquals(TRANSITIONS.size(), 1, "no duplicate transitions while daylight cycle is frozen");
            context.assertEquals(world.getTimeOfDay(), 18000L, "frozen authoritative world clock");
            command(world, "time set 23999");
            expectPhase(context, NightfallPhase.DAWN, 0.25);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true, world.getServer());
        });
        context.runAtTick(160, () -> {
            context.assertTrue(NightfallClock.timeOfDay(world.getTimeOfDay()) < 100,
                    "Reenabled daylight cycle must advance through the 24000-tick wrap");
            expectPhase(context, NightfallPhase.DAYLIGHT, 0.15);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, world.getServer());
            command(world, "time set 18000");
        });
        context.runAtTick(200, () -> {
            expectPhase(context, NightfallPhase.DEEP_NIGHT, 1.0);
            TRANSITIONS.clear();
            startSleepingPlayer(context);
        });
        context.runAtEveryTick(() -> {
            if (sleeper != null && sleeper.isSleeping()) {
                // Embedded connections are not real network clients. In vanilla a network
                // handler drives playerTick; drive that exact player method once per test
                // tick so the real sleep timer and ServerWorld sleep manager can run.
                sleeper.playerTick();
            }
        });
        context.runAtTick(360, () -> {
            context.assertTrue(NightfallClock.timeOfDay(world.getTimeOfDay()) < 200,
                    "Vanilla sleeping must advance the real world clock to the next day");
            expectPhase(context, NightfallPhase.DAYLIGHT, 0.15);
            context.assertFalse(sleeper.isSleeping(), "Vanilla sleeping must wake the in-memory player");
            context.assertEquals(List.copyOf(TRANSITIONS),
                    List.of(new ObservedTransition(NightfallPhase.DEEP_NIGHT, NightfallPhase.DAYLIGHT)),
                    "one direct transition for the vanilla sleep skip");
            context.complete();
        });
    }

    private static void checkLiveBoundaries(TestContext context) {
        long[] times = {0, 11499, 11500, 12999, 13000, 16999, 17000, 22499, 22500, 23999, 24000};
        NightfallPhase[] phases = {NightfallPhase.DAYLIGHT, NightfallPhase.DAYLIGHT,
                NightfallPhase.DUSK, NightfallPhase.DUSK, NightfallPhase.NIGHT, NightfallPhase.NIGHT,
                NightfallPhase.DEEP_NIGHT, NightfallPhase.DEEP_NIGHT, NightfallPhase.DAWN,
                NightfallPhase.DAWN, NightfallPhase.DAYLIGHT};
        double[] intensities = {0.15, 0.15, 0.4, 0.4, 0.75, 0.75, 1.0, 1.0, 0.25, 0.25, 0.15};
        for (int i = 0; i < times.length; i++) {
            context.getWorld().setTimeOfDay(times[i]);
            expectPhase(context, phases[i], intensities[i]);
            context.assertEquals(NightfallService.state(context.getWorld()).timeOfDay(),
                    (int) Math.floorMod(times[i], 24000L), "live normalized time at " + times[i]);
        }
    }

    private static void checkCommandsAndDiagnosticFeedback(TestContext context) {
        ServerWorld world = context.getWorld();
        command(world, "time set 22500");
        expectPhase(context, NightfallPhase.DAWN, 0.25);
        command(world, "time add 1500");
        expectPhase(context, NightfallPhase.DAYLIGHT, 0.15);

        CapturedOutput output = new CapturedOutput();
        ServerCommandSource source = world.getServer().getCommandSource().withWorld(world).withOutput(output).withLevel(2);
        long before = world.getTimeOfDay();
        world.getServer().getCommandManager().executeWithPrefix(source, "balance nightfall status");
        context.assertEquals(output.messages.size(), 1, "one live diagnostic feedback message");
        context.assertTrue(output.messages.getFirst().getContent() instanceof TranslatableTextContent,
                "Diagnostic feedback must use localization");
        TranslatableTextContent content = (TranslatableTextContent) output.messages.getFirst().getContent();
        context.assertEquals(content.getKey(), "command.balance.nightfall.status", "diagnostic translation key");
        Object[] arguments = content.getArgs();
        context.assertEquals(arguments.length, 5, "all diagnostic fields");
        context.assertEquals(arguments[0], "minecraft:overworld", "diagnostic dimension");
        context.assertEquals(arguments[1], 0, "diagnostic normalized time");
        context.assertEquals(arguments[3], "0.15", "diagnostic base intensity");
        context.assertEquals(world.getTimeOfDay(), before, "diagnostics must not mutate time");

        output.messages.clear();
        world.getServer().getCommandManager().executeWithPrefix(source.withLevel(1), "balance nightfall status");
        context.assertFalse(output.messages.stream().anyMatch(message ->
                        message.getContent() instanceof TranslatableTextContent translated
                                && translated.getKey().equals("command.balance.nightfall.status")),
                "Permission level 1 must not execute Nightfall diagnostics");
    }

    private static void checkInactiveDimensions(TestContext context) {
        for (var key : List.of(World.NETHER, World.END)) {
            ServerWorld dimension = context.getWorld().getServer().getWorld(key);
            context.assertTrue(dimension != null, "Default GameTest server must provide " + key.getValue());
            NightfallState state = NightfallService.state(dimension);
            context.assertFalse(state.enabled(), "Nightfall must be inactive in " + key.getValue());
            context.assertTrue(state.phase().isEmpty(), "Inactive dimension must not borrow an Overworld phase");
            context.assertEquals(state.baseIntensity(), 0.0, "inactive dimension intensity");
            context.assertFalse(NightfallService.hasPhaseChangedSinceLastObservation(dimension),
                    "Inactive dimensions must not share the Overworld transition baseline");
        }
    }

    @SuppressWarnings("removal") // Pinned Minecraft 1.21.1 exposes this supported in-memory fixture helper.
    private static void startSleepingPlayer(TestContext context) {
        BlockPos foot = new BlockPos(2, 1, 2);
        Direction facing = Direction.SOUTH;
        context.setBlockState(foot.down(), Blocks.STONE);
        context.setBlockState(foot.offset(facing).down(), Blocks.STONE);
        context.setBlockState(foot, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, facing).with(BedBlock.PART, BedPart.FOOT));
        context.setBlockState(foot.offset(facing),
                Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, facing).with(BedBlock.PART, BedPart.HEAD));
        sleeper = context.createMockCreativeServerPlayerInWorld();
        BlockPos absolute = context.getAbsolutePos(foot);
        sleeper.refreshPositionAndAngles(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        context.getWorld().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(true, context.getWorld().getServer());
        context.assertTrue(sleeper.trySleep(absolute).right().isPresent(),
                "In-memory server player must enter a real bed through vanilla sleep checks");
    }

    private static void command(ServerWorld world, String command) {
        world.getServer().getCommandManager().executeWithPrefix(
                world.getServer().getCommandSource().withWorld(world).withLevel(4).withSilent(), command);
    }

    private static void expectPhase(TestContext context, NightfallPhase phase, double intensity) {
        ServerWorld world = context.getWorld();
        context.assertEquals(NightfallService.phase(world).orElseThrow(), phase, "fresh world phase");
        context.assertEquals(NightfallService.baseIntensity(world), intensity, "fresh world intensity");
        context.assertEquals(NightfallService.state(world).phase().orElseThrow(), phase, "fresh world snapshot phase");
        context.assertEquals(NightfallService.isElevatedNighttimeDanger(world), phase.isNighttime(), "nighttime classification");
    }

    private record ObservedTransition(NightfallPhase previous, NightfallPhase current) {}

    private static final class CapturedOutput implements CommandOutput {
        private final List<Text> messages = new ArrayList<>();

        @Override
        public void sendMessage(Text message) {
            messages.add(message);
        }

        @Override
        public boolean shouldReceiveFeedback() {
            return true;
        }

        @Override
        public boolean shouldTrackOutput() {
            return true;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps() {
            return false;
        }
    }
}
