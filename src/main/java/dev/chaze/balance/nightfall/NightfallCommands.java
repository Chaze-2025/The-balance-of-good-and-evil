package dev.chaze.balance.nightfall;

import com.mojang.brigadier.CommandDispatcher;
import java.math.BigDecimal;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Read-only administrative diagnostics; no time changes, world mutation, or broadcasts. */
public final class NightfallCommands {
    private NightfallCommands() {}

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("balance")
                .then(CommandManager.literal("nightfall")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("status")
                                .executes(context -> status(context.getSource())))));
    }

    private static int status(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        NightfallState state = NightfallService.state(world);
        String phaseKey = state.phase().map(phase -> phase.name().toLowerCase(Locale.ROOT)).orElse("inactive");
        source.sendFeedback(() -> Text.translatable("command.balance.nightfall.status",
                world.getRegistryKey().getValue().toString(), state.timeOfDay(),
                Text.translatable("nightfall.balance.phase." + phaseKey),
                BigDecimal.valueOf(state.baseIntensity()).stripTrailingZeros().toPlainString(),
                Text.translatable(state.enabled() ? "nightfall.balance.enabled" : "nightfall.balance.disabled")), false);
        return 1;
    }
}
