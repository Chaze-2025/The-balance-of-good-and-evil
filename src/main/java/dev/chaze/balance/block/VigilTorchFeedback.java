package dev.chaze.balance.block;

import dev.chaze.balance.ward.VigilWards;
import java.math.BigDecimal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Presentation only: particles and messages never determine whether a ward protects. */
final class VigilTorchFeedback {
    private static final DustParticleEffect GOLDEN_GLIMMER =
            new DustParticleEffect(new Vector3f(1.0F, 0.72F, 0.18F), 0.45F);

    private VigilTorchFeedback() {
    }

    static ActionResult showWardStatus(World world, PlayerEntity player) {
        if (!world.isClient) {
            String radius = BigDecimal.valueOf(VigilWards.radius()).stripTrailingZeros().toPlainString();
            player.sendMessage(Text.translatable("message.balance.vigil_ward_active", radius), true);
        }
        return ActionResult.success(world.isClient);
    }

    static void addGoldenGlimmer(World world, Random random, double x, double y, double z) {
        // At most one small extra particle, on one quarter of vanilla display callbacks.
        if (world.isClient && random.nextInt(4) == 0) {
            world.addParticle(GOLDEN_GLIMMER, x, y, z, 0.0, 0.005, 0.0);
        }
    }
}
