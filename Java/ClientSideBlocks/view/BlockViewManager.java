package ClientSideBlocks.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Registry for tracking BlockViews within chunks and which players can see them.
 * Provides efficient lookup for:
 * 1. Finding views that contain a specific block
 * 2. Finding views visible to a specific player
 * 3. Finding views visible to a player that contain a specific block
 */
public interface BlockViewManager {

    /**
     * Checks if a view with the given ID is registered.
     *
     * @param viewId The UUID of the view to check
     * @return true if the view is registered, false otherwise
     */
    boolean isViewRegistered(@NotNull UUID viewId);

    /**
     * Adds a player to a BlockView, making the view visible to the player.
     *
     * @param player The player to add
     * @param blockView The BlockView to make visible to the player
     * @throws IllegalStateException if the view is a placeholder
     */
    void addPlayerToView(@NotNull Player player, @NotNull BlockView blockView);

    /**
     * Removes a player from a BlockView, making the view no longer visible to the player.
     *
     * @param player The player to remove
     * @param blockView The BlockView to remove the player from
     * @throws IllegalStateException if the view is a placeholder
     */
    void removePlayerFromView(@NotNull Player player, @NotNull BlockView blockView);

    /**
     * Removes a player from a BlockView, making the view no longer visible to the player.
     *
     * @param playerId The UUID of the player to remove
     * @param blockView The BlockView to remove the player from
     * @throws IllegalStateException if the view is a placeholder
     */
    void removePlayerFromView(@NotNull UUID playerId, @NotNull BlockView blockView);

    /**
     * Registers a BlockView with all chunks it overlaps.
     *
     * @param blockView The BlockView to register
     * @throws IllegalStateException if the view is a placeholder
     */
    void registerView(@NotNull BlockView blockView);

    /**
     * Unregisters a BlockView from all chunks it overlaps with.
     *
     * @param blockView The BlockView to unregister
     * @throws IllegalStateException if the view is a placeholder
     */
    void unregisterView(@NotNull BlockView blockView);

    /**
     * Gets all views in a specific chunk.
     *
     * @param chunk The chunk to get views for
     * @return Collection of BlockViews that overlap with the chunk
     */
    @NotNull
    Collection<BlockView> getViewsInChunk(@NotNull Chunk chunk);

    /**
     * Gets all views in a specific chunk.
     *
     * @param world The world containing the chunk
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return Collection of BlockViews that overlap with the chunk
     */
    @NotNull Collection<BlockView> getViewsInChunk(
            @NotNull World world,
            int chunkX,
            int chunkZ
    );

    /**
     * Gets all views in a specific chunk that are visible to a specific player.
     *
     * @param player The player to check visibility for
     * @param chunk The chunk to get views for
     * @return Collection of BlockViews that overlap with the chunk and are visible to the player
     */
    @NotNull Collection<BlockView> getViewsInChunkVisibleToPlayer(
            @NotNull Player player,
            @NotNull Chunk chunk
    );

    /**
     * Gets all views in a specific chunk that are visible to a specific player.
     *
     * @param player The player to check visibility for
     * @param world The world containing the chunk
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return Collection of BlockViews that overlap with the chunk and are visible to the player
     */
    @NotNull Collection<BlockView> getViewsInChunkVisibleToPlayer(
            @NotNull Player player,
            @NotNull World world,
            int chunkX,
            int chunkZ
    );

    /**
     * Gets all views that contain a specific block.
     *
     * @param world The world containing the block
     * @param x The block's X coordinate
     * @param y The block's Y coordinate
     * @param z The block's Z coordinate
     * @return Collection of BlockViews that contain the block
     */
    @NotNull Collection<BlockView> getViewsContainingBlock(
            @NotNull World world,
            int x,
            int y,
            int z
    );

    /**
     * Gets all views visible to a specific player.
     *
     * @param player The player to check
     * @return Collection of BlockViews visible to the player
     */
    @NotNull Collection<BlockView> getViewsVisibleToPlayer(@NotNull Player player);

    /**
     * Gets all views visible to a player that contain a specific block.
     *
     * @param player The player to check
     * @param world The world containing the block
     * @param x The block's X coordinate
     * @param y The block's Y coordinate
     * @param z The block's Z coordinate
     * @return Collection of BlockViews visible to the player that contain the block
     */
    @NotNull Collection<BlockView> getViewsVisibleToPlayerContainingBlock(
            @NotNull Player player,
            @NotNull World world,
            int x,
            int y,
            int z
    );

    /**
     * Gets a BlockView by its ID.
     *
     * @param viewId The UUID of the view
     * @return The BlockView, or null if not found
     */
    @Nullable
    BlockView getViewById(@NotNull UUID viewId);
}
