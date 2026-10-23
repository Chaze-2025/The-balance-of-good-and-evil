package dev.chaze.balance.nightfall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

/** Real command-tree and permission checks; no handler, server, or world is executed. */
class NightfallCommandsTest {
    @Test
    void registersTheStatusPathWithOnlyTheLeafExecutable() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        NightfallCommands.register(dispatcher);

        var balance = dispatcher.getRoot().getChild("balance");
        assertNotNull(balance);
        var nightfall = balance.getChild("nightfall");
        assertNotNull(nightfall);
        var status = nightfall.getChild("status");
        assertNotNull(status);
        assertEquals(List.of("balance", "nightfall", "status"), dispatcher.getPath(status));
        assertNull(balance.getCommand());
        assertNull(nightfall.getCommand());
        assertNotNull(status.getCommand());
    }

    @Test
    void levelTwoCanDiscoverAndParseStatusWhileLevelOneCannot() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        NightfallCommands.register(dispatcher);
        var nightfall = dispatcher.getRoot().getChild("balance").getChild("nightfall");
        ServerCommandSource ordinary = permissionSource(1);
        ServerCommandSource administrator = permissionSource(2);

        assertFalse(nightfall.canUse(ordinary));
        assertTrue(nightfall.canUse(administrator));
        assertEquals(List.of(), List.of(dispatcher.getAllUsage(dispatcher.getRoot(), ordinary, true)));
        assertEquals(List.of("balance nightfall status"),
                List.of(dispatcher.getAllUsage(dispatcher.getRoot(), administrator, true)));
        assertTrue(dispatcher.parse("balance nightfall status", ordinary).getReader().canRead());
        assertFalse(dispatcher.parse("balance nightfall status", administrator).getReader().canRead());
    }

    private static ServerCommandSource permissionSource(int level) {
        // The vanilla constructor stores these references without accessing the world
        // or executor. This fixture tests permission gating only; executing status
        // would require a real ServerWorld and is deliberately outside this test.
        return new ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO,
                null, level, "nightfall-permission-test", Text.literal("Nightfall test"), null, null);
    }
}
