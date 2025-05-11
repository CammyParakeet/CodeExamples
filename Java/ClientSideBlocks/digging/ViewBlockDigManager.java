package ClientSideBlocks.digging;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the destruction state and sessions of blocks within a {@link BlockView}.
 * <p>
 * Allows players to start, stop, complete, or cancel block destruction sessions, tracking progress and state persistently.
 */
public class ViewBlockDigManager {
    private final Plugin plugin;
    private final BlockView view;
    private final Map<BlockPosition, BlockRecord> blockDestructionStates = new ConcurrentHashMap<>();

    public ViewBlockDigManager(@NotNull final Plugin plugin, @NotNull final BlockView view) {
        this.plugin = plugin;
        this.view = view;
    }

    public void resetBlock(@NotNull final BlockPosition blockPosition) {
        final BlockRecord blockRecord = this.blockDestructionStates.get(blockPosition);

        if (blockRecord == null) {
            return;
        }
        if (!blockRecord.isActive()) {
            this.blockDestructionStates.remove(blockPosition);
        }
        this.view.setBlockProgress(blockRecord.entityId, blockPosition, -1);
    }

    /**
     * Retrieves all block positions currently tracked by destruction sessions.
     *
     * @return Collection of block positions with active or paused destruction sessions
     */
    public @NotNull Collection<BlockPosition> getBlockPositions() {
        return this.blockDestructionStates.keySet();
    }

    /**
     * Retrieves all block records managed by this manager.
     *
     * @return Collection of {@link BlockRecord}s representing destruction states
     */
    public @NotNull Collection<BlockRecord> getBlockRecords() {
        return this.blockDestructionStates.values();
    }

    /**
     * Attempts to initiate a block destruction session for a given player.
     *
     * @param player        The player initiating the destruction
     * @param blockPosition The position of the block to be destroyed
     * @return True if the session successfully started; false otherwise
     */
    public boolean start(@NotNull final Player player, @NotNull final BlockPosition blockPosition) {
        final BlockRecord existingState = this.blockDestructionStates.computeIfAbsent(blockPosition, (key) -> new BlockRecord(blockPosition));

        if (existingState.isActive()) {
            return false;
        }
        existingState.start(player, 1.0F);

        return true;
    }

    /**
     * Attempts to initiate a block destruction session for a given player.
     *
     * @param player        The player initiating the destruction
     * @param blockPosition The position of the block to be destroyed
     * @return True if the session successfully started; false otherwise
     */
    public boolean start(@NotNull final Player player, @NotNull final BlockPosition blockPosition, float speedMultiplier) {
        final BlockRecord record = blockDestructionStates.computeIfAbsent(blockPosition, BlockRecord::new);
        if (record.isActive()) return false;
        record.start(player, speedMultiplier);
        return true;
    }

    /**
     * Stops all block destruction sessions initiated by the specified player. This will skip certain lifecycle methods.
     *
     * @param player The player stopping their sessions
     */
    public void stop(@NotNull final Player player) {
        final UUID playerId = player.getUniqueId();
        final List<BlockRecord> existingStates = this.blockDestructionStates.values()
                .stream()
                .filter((blockRecord) -> playerId.equals(blockRecord.lastDamager))
                .toList();

        if (existingStates.isEmpty()) {
            return;
        }
        existingStates.forEach(BlockRecord::destroy);
    }

    /**
     * Cancels an active block destruction session at a given position initiated by a player.
     *
     * @param blockPosition The position of the block being destroyed
     * @param player        The player who initiated the session
     */
    public void cancel(@NotNull final BlockPosition blockPosition, @NotNull final Player player) {
        final BlockRecord existingState = this.blockDestructionStates.get(blockPosition);

        if (existingState == null || !existingState.isActive() || !existingState.lastDamager.equals(player.getUniqueId())) {
            return;
        }
        existingState.cancel();
    }

    /**
     * Completes an active block destruction session at a given position initiated by a player.
     *
     * @param blockPosition The position of the block being destroyed
     * @param player        The player who initiated the session
     */
    public void complete(@NotNull final BlockPosition blockPosition, @NotNull final Player player) {
        final BlockRecord existingState = this.blockDestructionStates.get(blockPosition);

        if (existingState == null || !existingState.isActive() || !existingState.lastDamager.equals(player.getUniqueId())) {
            return;
        }
        existingState.complete();
    }

    /**
     * Fakes damage to a block with saved progress, without starting a session
     */
    public void simulatePartialBreak(@NotNull Player player, @NotNull BlockPosition pos, float breakProgressFraction) {
        ViewBlockData block = view.getBlock(pos);
        if (block == null || !block.isResumable()) return;

        long fullTime = ViewUtils.getBlockBreakTime(block, player.getEquipment().getItemInMainHand(), player);
        long simulatedTime = (long) (breakProgressFraction * fullTime);
        int stage = (int) Math.ceil(breakProgressFraction * 9);

        BlockRecord record = this.blockDestructionStates.computeIfAbsent(pos, BlockRecord::new);
        record.lastDamager = player.getUniqueId();
        record.accumulatedTime = simulatedTime;
        record.lastStage = stage;

        // Save it into the block's progress bar
        this.view.setBlockProgress(record.entityId, pos, stage);
    }

    private int generateUniqueEntityId() {
        return -1 - (int) (Math.random() * Integer.MAX_VALUE);
    }

    /**
     * Represents the destruction state of a block within a view.
     */
    @Getter
    public final class BlockRecord {
        private final int entityId;
        private final BlockPosition blockPosition;

        private UUID lastDamager;
        private ViewBlockDigSession destructionSession;
        private long accumulatedTime;
        private int lastStage;

        private BlockRecord(@NotNull final BlockPosition blockPosition) {
            this.entityId = ViewBlockDigManager.this.generateUniqueEntityId();
            this.blockPosition = blockPosition;
            this.accumulatedTime = 0;
            this.lastStage = -1;
        }

        public void sync() {
            if (this.isActive()) {
                return;
            }
            if (this.accumulatedTime <= 0 || this.lastStage < 0) {
                if (ViewBlockDigManager.this.blockDestructionStates.remove(this.blockPosition, this)) {
                    return;
                }
            }
            final ViewBlockData viewBlockData = ViewBlockDigManager.this.view.getBlock(this.blockPosition);

            if (viewBlockData == null) {
                throw new IllegalStateException("Cannot start a block destruction session on a non block view block.");
            }
            ViewBlockDigManager.this.view.setBlockProgress(this.entityId, this.blockPosition, this.lastStage);
        }

        private void start(@NotNull final Player player, float speedMultiplier) {
            final ViewBlockData viewBlockData = ViewBlockDigManager.this.view.getBlock(this.blockPosition);

            if (viewBlockData == null) {
                throw new IllegalStateException("Cannot start a block destruction session on a non block view block.");
            }
            this.lastDamager = player.getUniqueId();
            this.destructionSession = new ViewBlockDigSession(
                    ViewBlockDigManager.this.plugin,
                    ViewBlockDigManager.this.view,
                    viewBlockData,
                    this.blockPosition,
                    player,
                    (accumulatedMs, stage, blockWasBroken) -> {
                        if (!blockWasBroken && viewBlockData.isResumable() && accumulatedMs > 0) {
                            this.lastStage = stage;
                            this.accumulatedTime = Math.max(this.accumulatedTime, accumulatedMs);
                        } else {
                            ViewBlockDigManager.this.blockDestructionStates.remove(this.blockPosition);
                            ViewBlockDigManager.this.view.setBlockProgress(this.entityId, this.blockPosition, -1);
                        }
                    },
                    this.accumulatedTime,
                    this.entityId,
                    speedMultiplier
            );

            this.destructionSession.start();
        }

        private void destroy() {
            if (this.destructionSession == null) {
                return;
            }
            final ViewBlockDigSession session = this.destructionSession;

            this.destructionSession = null;
            session.destroySession();
        }

        private void complete() {
            if (this.destructionSession == null) {
                return;
            }
            final ViewBlockDigSession session = this.destructionSession;

            this.destructionSession = null;
            session.completeSession();
        }

        private void cancel() {
            if (this.destructionSession == null) {
                return;
            }
            final ViewBlockDigSession session = this.destructionSession;

            this.destructionSession = null;
            session.cancelSession();
        }

        public boolean isActive() {
            return this.destructionSession != null;
        }
    }
}
