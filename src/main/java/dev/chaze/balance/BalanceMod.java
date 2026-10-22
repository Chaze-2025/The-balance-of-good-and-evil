package dev.chaze.balance;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point. Gameplay systems will be registered here in later work. */
public final class BalanceMod implements ModInitializer {
    public static final String MOD_ID = "balance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Balance development foundation initialized");
    }
}
