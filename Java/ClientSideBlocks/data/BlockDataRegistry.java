package ClientSideBlocks.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages mappings between {@link ViewBlockData} instances and compact numeric identifiers.
 * <p>
 * Enables efficient storage and retrieval of block data by converting between detailed block representations
 * and short numeric IDs. Particularly useful for scenarios involving large sets of blocks.
 */
public interface BlockDataRegistry {

    /**
     * The predefined ID for air blocks.
     */
    short AIR_ID = -1;

    /**
     * Retrieves or assigns a unique short ID for the provided {@link ViewBlockData}.
     * If the data isn't already registered, a new ID is assigned and returned.
     *
     * @param data The block data to retrieve or register
     * @return Short ID corresponding to the provided block data
     */
    short getId(@NotNull ViewBlockData data);

    /**
     * Retrieves the {@link ViewBlockData} associated with the specified short ID.
     *
     * @param id The short ID to look up
     * @return The corresponding block data, or null if not found
     */
    @Nullable
    ViewBlockData getBlockData(short id);

    /**
     * Clears all existing mappings within the registry.
     * <p>
     * Warning: This operation invalidates all previously assigned IDs and their block data references.
     */
    void clear();
}
