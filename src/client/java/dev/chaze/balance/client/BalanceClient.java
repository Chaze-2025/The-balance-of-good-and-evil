package dev.chaze.balance.client;

import dev.chaze.balance.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** Client-only rendering integration; protection decisions remain on the server. */
public final class BalanceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),
                ModBlocks.VIGIL_TORCH, ModBlocks.WALL_VIGIL_TORCH);
    }
}
