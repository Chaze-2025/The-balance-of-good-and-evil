package dev.chaze.balance.client;

import net.fabricmc.api.ClientModInitializer;

/** Client-only entry point for future renderers, animation, and sound handlers. */
public final class BalanceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Deliberately empty: no gameplay or rendering features yet.
    }
}
