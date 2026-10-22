package dev.chaze.balance.registry;

import dev.chaze.balance.BalanceMod;
import dev.chaze.balance.block.entity.VigilTorchBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Both torch variants share a stateless, non-ticking lifecycle marker type. */
public final class ModBlockEntities {
    public static final BlockEntityType<VigilTorchBlockEntity> VIGIL_TORCH = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, BalanceMod.id("vigil_torch"),
            BlockEntityType.Builder.create(VigilTorchBlockEntity::new,
                    ModBlocks.VIGIL_TORCH, ModBlocks.WALL_VIGIL_TORCH).build(null));

    private ModBlockEntities() {
    }

    public static void initialize() {
        // Calling this method registers the marker before any world can load it.
    }
}
