package com.example.separatebobbers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class SeparateBobbers implements ModInitializer {
    @Override
    public void onInitialize() {
        // Clear only in-memory entity references on disconnect. Persistent world records stay saved.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BobberTracker.clear(handler.getPlayer().getUuid()));

        // Saved entity records and the PersistentState are written by Minecraft. Only transient
        // references should be discarded when a server stops.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BobberTracker.clearAll());
    }
}
