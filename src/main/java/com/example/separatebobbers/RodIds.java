package com.example.separatebobbers;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

public final class RodIds {
    private static final String KEY = "separatebobbers_id";

    private RodIds() {}

    public static String getOrCreate(ItemStack stack) {
        if (stack.isEmpty()) return null;

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            String existing = nbt.getString(KEY).orElse(null);
            if (existing != null && !existing.isBlank()) return existing;
        }

        String id = UUID.randomUUID().toString();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString(KEY, id));
        return id;
    }
}
