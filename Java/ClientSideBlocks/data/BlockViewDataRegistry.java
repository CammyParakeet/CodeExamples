package ClientSideBlocks.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class BlockViewDataRegistry implements BlockDataRegistry {
    public static final BlockDataRegistry DEFAULT_BLOCK_DATA_REGISTRY = new BlockViewDataRegistry();

    private final Map<String, Short> stringToId = new HashMap<>();
    private final Map<Short, ViewBlockData> idToBlock = new HashMap<>();
    private short nextId = 0;

    public BlockViewDataRegistry() {
        this.idToBlock.put(AIR_ID, VanillaViewBlockData.AIR);
        this.stringToId.put(VanillaViewBlockData.AIR.serialize(), AIR_ID);
    }

    @Override
    public synchronized short getId(@NotNull final ViewBlockData data) {
        // BlockData is generally considered ephemeral and thus needs to be serialized versus relying on equals/hashCode implementations
        final String key = data.serialize();

        return this.stringToId.computeIfAbsent(key, k -> {
            // This is somewhat of a shortcoming but if use-cases are kept somewhat limited, this should be hard to reach
            if (this.nextId == Short.MAX_VALUE) {
                throw new IllegalStateException("Exceeded maximum unique BlockData entries");
            }
            final short id = this.nextId++;

            this.idToBlock.put(id, data);

            return id;
        });
    }

    @Override
    public @Nullable ViewBlockData getBlockData(final short id) {
        return this.idToBlock.get(id);
    }

    @Override
    public synchronized void clear() {
        this.stringToId.clear();
        this.idToBlock.clear();

        this.nextId = 0;
    }
}
