package ClientSideBlocks.data;

import org.jetbrains.annotations.NotNull;

/**
 * Represents block data within a BlockView, supporting both vanilla and custom block types.
 * Implementations must provide serialization logic and block representation compatible with Bukkit's BlockData.
 */
public sealed interface ViewBlockData permits VanillaViewBlockData, CustomViewBlockData {

    /**
     * Serializes the ViewBlockData into a String representation.
     *
     * @return serialized form of the block data
     */
    @NotNull
    String serialize();

    /**
     * Get the bukkit type of this block.
     *
     * @return the type of the block data
     */
    @NotNull Material getType();

    /**
     * Retrieves the hardness of the block, influencing how long it takes to break.
     *
     * @return block hardness, defaulting to 1.0
     */
    default float getHardness() {
        return 1.0f;
    }

    /**
     * Determines if the block supports resuming block-breaking progress.
     *
     * @return true if breaking can be resumed, false otherwise
     */
    default boolean isResumable() {
        return false;
    }

    /**
     * Checks if the specified ItemStack can harvest this block.
     *
     * @param itemStack the item used to attempt harvesting
     * @return true if the block can be harvested with the given item, false otherwise
     */
    default boolean canHarvestWithItem(@NotNull final ItemStack itemStack) {
        return true;
    }

    /**
     * Converts this ViewBlockData into a Bukkit-compatible BlockData instance.
     *
     * @return the corresponding Bukkit BlockData
     */
    @NotNull BlockData toBlockData();
}
