package dev.chaze.balance;

import dev.chaze.balance.nightfall.NightfallService;
import dev.chaze.balance.registry.ModBlockEntities;
import dev.chaze.balance.registry.ModBlocks;
import dev.chaze.balance.registry.ModItems;
import dev.chaze.balance.ward.VigilWards;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dedicated-server-safe common entry point. */
public final class BalanceMod implements ModInitializer {
    public static final String MOD_ID = "balance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        VigilWards.initialize();
        NightfallService.initialize();
        LOGGER.info("Balance development foundation initialized");
        LOGGER.info("Vigil Torch registries and server ward lifecycle initialized");
        LOGGER.info("Nightfall server lifecycle and administrative diagnostics initialized");
    }
}
