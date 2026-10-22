package dev.chaze.balance.registry;

import dev.chaze.balance.BalanceMod;
import dev.chaze.balance.block.VigilTorchBlock;
import dev.chaze.balance.block.VigilWallTorchBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/** Block registration is kept separate from item and block entity registration. */
public final class ModBlocks {
    public static final Block VIGIL_TORCH = Registry.register(Registries.BLOCK,
            BalanceMod.id("vigil_torch"), new VigilTorchBlock(torchSettings()));
    public static final Block WALL_VIGIL_TORCH = Registry.register(Registries.BLOCK,
            BalanceMod.id("wall_vigil_torch"), new VigilWallTorchBlock(torchSettings()));

    private ModBlocks() {
    }

    private static AbstractBlock.Settings torchSettings() {
        return AbstractBlock.Settings.create().noCollision().breakInstantly()
                .luminance(state -> 14).sounds(BlockSoundGroup.WOOD)
                .pistonBehavior(PistonBehavior.DESTROY);
    }

    public static void initialize() {
        // Calling this method initializes the registered constants in a known order.
    }
}
