package com.example.separatebobbers.mixin;

import com.example.separatebobbers.PersistentBobberState;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Vanilla's fishing-bobber tick validates its owner and can discard a bobber when the owner is
 * offline, in another dimension, or using another rod. For bobbers tracked by this mod, those
 * conditions mean "park this bobber" rather than "delete it". Pausing the tick keeps the actual
 * entity present on its pressure plate; the chunk can then save it normally.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberPersistenceMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void separatebobbers$keepInactiveBobbers(CallbackInfo ci) {
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        if (!(bobber.getWorld() instanceof ServerWorld bobberWorld)) return;

        MinecraftServer server = bobberWorld.getServer();
        PersistentBobberState state = PersistentBobberState.get(server);
        state.updateChunkIfNeeded(bobber);
        PersistentBobberState.BobberRecord record = state.findByBobber(bobber.getUuid());
        if (record == null) return;

        final UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(record.ownerUuid());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
        if (owner == null || owner.getWorld() != bobberWorld || owner.fishHook != bobber) {
            // Do not call vanilla tick: its validation may discard an inactive tracked bobber.
            ci.cancel();
        }
    }
}
