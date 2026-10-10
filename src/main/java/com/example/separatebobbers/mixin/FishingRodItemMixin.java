package com.example.separatebobbers.mixin;

import com.example.separatebobbers.BobberTracker;
import com.example.separatebobbers.RodIds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingRodItem.class)
public abstract class FishingRodItemMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void separatebobbers$before(World world, PlayerEntity user, Hand hand,
                                        CallbackInfoReturnable<ActionResult> cir) {
        if (!(world instanceof ServerWorld)) return;
        String id = RodIds.getOrCreate(user.getStackInHand(hand));
        if (id != null) BobberTracker.begin(user, id);
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void separatebobbers$after(World world, PlayerEntity user, Hand hand,
                                       CallbackInfoReturnable<ActionResult> cir) {
        if (!(world instanceof ServerWorld)) return;
        BobberTracker.end(user);
    }
}
