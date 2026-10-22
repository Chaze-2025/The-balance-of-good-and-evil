package dev.chaze.balance.block;

import com.mojang.serialization.MapCodec;
import dev.chaze.balance.block.entity.VigilTorchBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** A vanilla standing torch with a lifecycle marker for server-side ward indexing. */
public final class VigilTorchBlock extends TorchBlock implements BlockEntityProvider {
    public static final MapCodec<VigilTorchBlock> CODEC = createCodec(VigilTorchBlock::new);

    public VigilTorchBlock(AbstractBlock.Settings settings) {
        super(ParticleTypes.FLAME, settings);
    }

    @Override
    public MapCodec<VigilTorchBlock> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new VigilTorchBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        return VigilTorchFeedback.showWardStatus(world, player);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);
        VigilTorchFeedback.addGoldenGlimmer(world, random, pos.getX() + 0.5,
                pos.getY() + 0.7, pos.getZ() + 0.5);
    }
}
