package dev.chaze.balance.block.entity;

import dev.chaze.balance.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * A non-ticking lifecycle marker. The block is the source of truth; this entity
 * stores no custom state and relies on vanilla chunk save/load identification.
 */
public final class VigilTorchBlockEntity extends BlockEntity {
    public VigilTorchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VIGIL_TORCH, pos, state);
    }
}
