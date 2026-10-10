package com.example.separatebobbers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class BobberTracker {
    private static final Map<UUID, Map<String, FishingBobberEntity>> HOOKS = new HashMap<>();
    private static String pendingId;

    private BobberTracker() {}

    public static void begin(PlayerEntity player, String rodId) {
        pendingId = rodId;
        FishingBobberEntity bobber = null;

        Map<String, FishingBobberEntity> rods = HOOKS.get(player.getUuid());
        if (rods != null) bobber = rods.get(rodId);

        // A runtime reference may have become invalid when its chunk unloaded. Try the world save.
        if (bobber == null || bobber.isRemoved()) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                bobber = PersistentBobberState.restore(serverPlayer, rodId);
                if (bobber != null) {
                    HOOKS.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>()).put(rodId, bobber);
                } else if (rods != null) {
                    rods.remove(rodId);
                }
            } else {
                bobber = null;
            }
        }

        if (bobber != null && !bobber.isRemoved()) {
            // Ensure the entity points to the current player instance, especially after reconnecting.
            bobber.setOwner(player);
        } else {
            bobber = null;
        }

        player.fishHook = bobber;
    }

    public static void end(PlayerEntity player) {
        if (pendingId == null) return;

        Map<String, FishingBobberEntity> rods = HOOKS.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>());
        FishingBobberEntity bobber = player.fishHook;

        if (bobber != null && !bobber.isRemoved()) {
            rods.put(pendingId, bobber);
            if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.getServer() != null) {
                PersistentBobberState.get(serverPlayer.getServer()).put(serverPlayer, pendingId, bobber);
            }
        } else {
            rods.remove(pendingId);
            if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.getServer() != null) {
                PersistentBobberState.get(serverPlayer.getServer()).remove(player.getUuid(), pendingId);
            }
        }

        // Remove stale in-memory references only. An entity may report removed after its chunk
        // unloads even though Minecraft has serialized it to disk; its persistent record must stay.
        // If a user later tries to restore a genuinely missing entity, restore() removes that record.
        Iterator<Map.Entry<String, FishingBobberEntity>> iterator = rods.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, FishingBobberEntity> entry = iterator.next();
            if (entry.getValue() == null || entry.getValue().isRemoved()) {
                iterator.remove();
            }
        }

        if (rods.isEmpty()) HOOKS.remove(player.getUuid());
        pendingId = null;
    }

    /** Removes session-local references only; saved bobber records must survive logout. */
    public static void clear(UUID playerUuid) {
        HOOKS.remove(playerUuid);
    }

    public static void clearAll() {
        HOOKS.clear();
        pendingId = null;
    }
}
