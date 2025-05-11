package ClientSideBlocks.view;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public interface BlockView extends ForwardingAudience {
    @NotNull
    UUID getId();

    @NotNull
    World getWorld();

    @NotNull BlockPosition getOrigin();

    @NotNull Vector3i getDimensions();

    @NotNull BlockViewOptions getOptions();

    @NotNull BlockViewType getType();

    @NotNull BlockDataRegistry getBlockDataRegistry();

    @Nullable UUID getOwnerId();

    void setOwnerId(@Nullable UUID ownerId);

    default @NotNull Map<BlockPosition, ViewBlockData> getNearbyBlocks(@NotNull final BlockPosition position, final int radius) {
        return this.getNearbyBlocks(position.blockX(), position.blockY(), position.blockZ(), radius);
    }

    @NotNull Map<BlockPosition, ViewBlockData> getNearbyBlocks(int x, int y, int z, int radius);

    default boolean isOriginalContent(@NotNull final BlockPosition blockPosition) {
        return this.isOriginalContent(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ());
    }

    boolean isOriginalContent(int x, int y, int z);

    default boolean isManagedBlock(@NotNull final BlockPosition blockPosition) {
        return this.isManagedBlock(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ());
    }

    boolean isManagedBlock(int x, int y, int z);

    default @NotNull BlockPosition getRelativePosition(@NotNull final BlockPosition blockPosition) {
        return this.getRelativePosition(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ());
    }

    default @NotNull BlockPosition getRelativePosition(final int worldX, final int worldY, final int worldZ) {
        final BlockPosition origin = this.getOrigin();

        return Position.block(
                worldX - origin.blockX(),
                worldY - origin.blockY(),
                worldZ - origin.blockZ()
        );
    }

    default @NotNull BlockPosition getWorldPosition(@NotNull final BlockPosition blockPosition) {
        return this.getWorldPosition(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ());
    }

    default @NotNull BlockPosition getWorldPosition(final int relativeX, final int relativeY, final int relativeZ) {
        return this.getOrigin().offset(relativeX, relativeY, relativeZ);
    }

    @Nullable
    default ViewBlockData getBlock(@NotNull final BlockPosition position) {
        return this.getBlock(position.blockX(), position.blockY(), position.blockZ());
    }

    @Nullable
    ViewBlockData getBlock(int x, int y, int z);

    default boolean isInside(@NotNull final BlockPosition position) {
        return this.isInside(position.blockX(), position.blockY(), position.blockZ());
    }

    boolean isInside(int x, int y, int z);

    default void setBlockProgress(final int entityId, @NotNull final BlockPosition position, @Range(from = -1, to = 9) final int progress) {
        this.setBlockProgress(entityId, position.blockX(), position.blockY(), position.blockZ(), progress);
    }

    void setBlockProgress(int entityId, int x, int y, int z, @Range(from = -1, to = 9) int progress);

    void setBlocks(@NotNull Map<BlockPosition, ViewBlockData> blocks, boolean callEvents);

    default void setBlock(@NotNull final BlockPosition position, @NotNull final ViewBlockData data, final boolean callEvent) {
        this.setBlock(
                position.blockX(),
                position.blockY(),
                position.blockZ(),
                data,
                callEvent
        );
    }

    void setBlock(int x, int y, int z, @NotNull ViewBlockData data, boolean callEvent);

    default void breakBlock(@NotNull final Player player, @NotNull final BlockPosition position) {
        this.breakBlock(player, position, true);
    }

    default void breakBlock(@NotNull final Player player, @NotNull final BlockPosition position, final boolean playAnimation) {
        this.breakBlock(player, position.blockX(), position.blockY(), position.blockZ(), playAnimation, true, null);
    }

    default void breakBlock(@NotNull final Player player, @NotNull final BlockPosition position, final boolean playAnimation, final boolean callEvent) {
        this.breakBlock(player, position.blockX(), position.blockY(), position.blockZ(), playAnimation, callEvent, null);
    }

    default void breakBlock(@NotNull final Player player, final int x, final int y, final int z) {
        this.breakBlock(player, x, y, z, true, true, null);
    }

    default void breakBlock(@NotNull final Player player, final int x, final int y, final int z, final boolean playAnimation) {
        this.breakBlock(player, x, y, z, playAnimation, true, null);
    }

    default void breakBlock(@NotNull Player player, @NotNull BlockPosition pos, boolean playAnimation, boolean callEvent, @Nullable TriggerSource triggerSource) {
        breakBlock(player, pos.blockX(), pos.blockY(), pos.blockZ(), playAnimation, callEvent, triggerSource);
    }

    void breakBlock(@NotNull Player player, int x, int y, int z, boolean playAnimation, boolean callEvent, @Nullable TriggerSource triggerSource);

    /**
     * Applies visual and internal partial damage to a block
     *
     * @param player       The player who sees the damage
     * @param position     The block being damaged
     * @param progress     Stage from 0 to 9
     * @param durationMs   Time already spent mining (used for resuming)
     */
    default void damageBlock(@NotNull Player player, @NotNull BlockPosition position, float progress) {
        //this.getDigManager().fakeDamage(player, position, progress, durationMs);
        this.getDigManager().simulatePartialBreak(player, position, progress);
    }

    default void refreshBlock(@NotNull final BlockPosition position) {
        this.refreshBlock(position.blockX(), position.blockY(), position.blockZ());
    }

    void refreshBlock(int x, int y, int z);


    default void refreshBlock(@NotNull final Player player, @NotNull final BlockPosition position) {
        this.refreshBlock(player, position.blockX(), position.blockY(), position.blockZ());
    }

    void refreshBlock(@NotNull Audience audience, int x, int y, int z);

    void apply(@NotNull Audience audience);

    void applyChunk(@NotNull Audience audience, int chunkX, int chunkZ);

    void reset(@NotNull Audience audience);

    void resetChunk(@NotNull Audience audience, int chunkX, int chunkZ);

    @NotNull
    Iterator<@NotNull BlockPosition> blockPositionIterator();

    @NotNull
    Collection<@NotNull Player> getViewers();

    @ApiStatus.Internal
    boolean addAudience(@NotNull Audience audience, boolean apply);

    @ApiStatus.Internal
    boolean removeAudience(@NotNull Audience audience, boolean reset);

    @NotNull ViewBlockDigManager getDigManager();

    default @NotNull BlockView copy(@NotNull final BlockViewType type) {
        return this.copy(this.getOrigin(), type);
    };

    @NotNull BlockView copy(@NotNull BlockPosition newOrigin, @NotNull BlockViewType type);

    default void onUnregister() {

    }
}
