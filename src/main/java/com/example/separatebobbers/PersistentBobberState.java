package com.example.separatebobbers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved lookup table for each player's fishing-rod ID and the bobber entity it owns.
 * The actual bobber remains a normal Minecraft entity saved in its dimension's chunk data.
 */
public final class PersistentBobberState extends PersistentState {
    private static final String STATE_ID = "separatebobbers_persistent_bobbers";

    public record BobberRecord(
            String ownerUuid,
            String rodId,
            String bobberUuid,
            String dimension,
            double x,
            double y,
            double z
    ) {
        public static final Codec<BobberRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("owner").forGetter(BobberRecord::ownerUuid),
                Codec.STRING.fieldOf("rod").forGetter(BobberRecord::rodId),
                Codec.STRING.fieldOf("bobber").forGetter(BobberRecord::bobberUuid),
                Codec.STRING.fieldOf("dimension").forGetter(BobberRecord::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(BobberRecord::x),
                Codec.DOUBLE.fieldOf("y").forGetter(BobberRecord::y),
                Codec.DOUBLE.fieldOf("z").forGetter(BobberRecord::z)
        ).apply(instance, BobberRecord::new));

        String key() {
            return ownerUuid + "|" + rodId;
        }
    }

    public static final Codec<PersistentBobberState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, BobberRecord.CODEC)
                    .fieldOf("entries")
                    .forGetter(state -> state.entries)
    ).apply(instance, PersistentBobberState::new));

    public static final PersistentStateType<PersistentBobberState> TYPE = new PersistentStateType<>(
            STATE_ID,
            PersistentBobberState::new,
            CODEC,
            null
    );

    private final Map<String, BobberRecord> entries;

    public PersistentBobberState() {
        this.entries = new HashMap<>();
    }

    private PersistentBobberState(Map<String, BobberRecord> entries) {
        this.entries = new HashMap<>(entries);
    }

    public static PersistentBobberState get(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(TYPE);
    }

    public void put(ServerPlayerEntity owner, String rodId, FishingBobberEntity bobber) {
        BobberRecord record = new BobberRecord(
                owner.getUuid().toString(),
                rodId,
                bobber.getUuid().toString(),
                bobber.getWorld().getRegistryKey().getValue().toString(),
                bobber.getX(), bobber.getY(), bobber.getZ()
        );
        entries.put(record.key(), record);
        markDirty();
    }

    public void remove(UUID owner, String rodId) {
        if (entries.remove(owner + "|" + rodId) != null) markDirty();
    }

    public BobberRecord get(UUID owner, String rodId) {
        return entries.get(owner + "|" + rodId);
    }

    public BobberRecord findByBobber(UUID bobberUuid) {
        String expected = bobberUuid.toString();
        for (BobberRecord record : entries.values()) {
            if (record.bobberUuid().equals(expected)) return record;
        }
        return null;
    }

    /** Update the stored chunk locator only when the real bobber crosses a chunk boundary. */
    public void updateChunkIfNeeded(FishingBobberEntity bobber) {
        String expected = bobber.getUuid().toString();
        for (Map.Entry<String, BobberRecord> mapEntry : entries.entrySet()) {
            BobberRecord old = mapEntry.getValue();
            if (!old.bobberUuid().equals(expected)) continue;

            int oldChunkX = ((int) Math.floor(old.x())) >> 4;
            int oldChunkZ = ((int) Math.floor(old.z())) >> 4;
            int newChunkX = ((int) Math.floor(bobber.getX())) >> 4;
            int newChunkZ = ((int) Math.floor(bobber.getZ())) >> 4;
            String currentDimension = bobber.getWorld().getRegistryKey().getValue().toString();
            if (oldChunkX != newChunkX || oldChunkZ != newChunkZ || !old.dimension().equals(currentDimension)) {
                mapEntry.setValue(new BobberRecord(
                        old.ownerUuid(), old.rodId(), old.bobberUuid(),
                        currentDimension,
                        bobber.getX(), bobber.getY(), bobber.getZ()
                ));
                markDirty();
            }
            return;
        }
    }

    /**
     * Resolves a saved bobber by loading its original chunk when needed. The returned entity is
     * rebound to the current ServerPlayerEntity so its owner reference doesn't point at the old
     * player instance left over from the previous session.
     */
    public static FishingBobberEntity restore(ServerPlayerEntity player, String rodId) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;

        PersistentBobberState state = get(server);
        BobberRecord record = state.get(player.getUuid(), rodId);
        if (record == null) return null;

        final UUID bobberUuid;
        final Identifier dimensionId;
        try {
            bobberUuid = UUID.fromString(record.bobberUuid());
            dimensionId = Identifier.of(record.dimension());
        } catch (IllegalArgumentException ex) {
            state.remove(player.getUuid(), rodId);
            return null;
        }

        RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
        ServerWorld bobberWorld = server.getWorld(dimensionKey);
        if (bobberWorld == null) return null;

        // Entity UUID lookup only searches loaded entities. Load the recorded chunk first.
        int chunkX = ((int) Math.floor(record.x())) >> 4;
        int chunkZ = ((int) Math.floor(record.z())) >> 4;
        bobberWorld.getChunk(chunkX, chunkZ);

        Entity entity = bobberWorld.getEntity(bobberUuid);
        if (entity instanceof FishingBobberEntity bobber && !bobber.isRemoved()) {
            bobber.setOwner(player);
            return bobber;
        }

        // Keep the record only if the chunk may still be waiting for entity data to load.
        // The lookup failed after a synchronous chunk load, so it is stale and safe to clear.
        state.remove(player.getUuid(), rodId);
        return null;
    }
}
