package dev.chaze.balance.block;

import com.mojang.serialization.MapCodec;
import dev.chaze.balance.block.entity.VigilTorchBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Wall placement and support checks remain the vanilla wall torch implementation. */
public final class VigilWallTorchBlock extends WallTorchBlock implements BlockEntityProvider {
    // WallTorchBlock fixes this return type rather than using a covariant wildcard.
    public static final MapCodec<WallTorchBlock> CODEC = createCodec(VigilWallTorchBlock::new);

    public VigilWallTorchBlock(AbstractBlock.Settings settings) {
        super(ParticleTypes.FLAME, settings);
    }

    @Override
    public MapCodec<WallTorchBlock> getCodec() {
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
        Direction supportDirection = state.get(FACING).getOpposite();
        VigilTorchFeedback.addGoldenGlimmer(world, random,
                pos.getX() + 0.5 + 0.27 * supportDirection.getOffsetX(),
                pos.getY() + 0.92,
                pos.getZ() + 0.5 + 0.27 * supportDirection.getOffsetZ());
    }
}
