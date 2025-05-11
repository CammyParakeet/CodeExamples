package ClientSideBlocks.digging;

import ClientSideBlocks.data.ViewBlockData;
import ClientSideBlocks.event.ViewBlockDigEvent;
import ClientSideBlocks.view.BlockView;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an active session of block destruction by a player within a {@link BlockView}.
 * <p>
 * Tracks destruction progress, manages visual updates, and handles completion or cancellation of the session.
 */
public final class ViewBlockDigSession extends BukkitRunnable {
    @Getter
    private final int entityId;

    @Getter
    private final BlockPosition blockPosition;

    @Getter
    private final Player player;

    private final Plugin plugin;
    private final BlockView blockView;
    private final DigSessionCallback cancelCallback;
    private final long baseAccumulatedTime;
    private final long fullBreakDuration;

    private long startTime;
    private int lastStage;

    private final float speedMultiplier;

    @Getter
    private boolean active = true;

    public ViewBlockDigSession(
            @NotNull final Plugin plugin,
            @NotNull final BlockView blockView,
            @NotNull final ViewBlockData blockData,
            @NotNull final BlockPosition blockPosition,
            @NotNull final Player player,
            @NotNull final DigSessionCallback cancelCallback,
            final long accumulatedTimeMs,
            final int entityId,
            float speedMultiplier
    ) {
        this.entityId = entityId;
        this.plugin = plugin;
        this.player = player;
        this.blockView = blockView;
        this.blockPosition = blockPosition;
        this.baseAccumulatedTime = accumulatedTimeMs;
        this.cancelCallback = cancelCallback;
        this.fullBreakDuration = ViewUtils.getBlockBreakTime(
                blockData, player.getEquipment().getItemInMainHand(), player
        );
        this.speedMultiplier = speedMultiplier;
    }

    private void _update(final boolean force) {
        if (!this.active) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long sessionTime = (long) ((now - this.startTime) * this.speedMultiplier);
        final long totalTime = this.baseAccumulatedTime + sessionTime;

        if (totalTime >= this.fullBreakDuration) {
            this.breakBlock();

            return;
        }
        final float fraction = (float) totalTime / (float) this.fullBreakDuration;
        final int stage = (int) Math.ceil(fraction * 9.0f);

        if (stage == this.lastStage && !force) {
            return;
        }
        this.setProgress(stage);
    }

    /**
     * Updates destruction progress based on elapsed time.
     */
    public void update() {
        this._update(false);
    }

    /**
     * Forces immediate update of destruction progress.
     */
    public void forceUpdate() {
        this._update(true);
    }

    /**
     * Starts the block destruction session, initiating scheduled updates.
     */
    public void start() {
        if (!this.active) {
            return;
        }
        this.startTime = System.currentTimeMillis();

        final float fraction = this.getCurrentProgress();
        final int stage = (int) Math.ceil(fraction * 9.0f);

        this.setProgress(stage);
        this.runTaskTimerAsynchronously(this.plugin, 0, 1);
    }

    /**
     * Completes the destruction session successfully. Completing a session means the animation was completed
     * and the block is broken.
     */
    public void completeSession() {
        this.stop(true, true);
    }

    /**
     * Cancels the destruction session.
     */
    public void cancelSession() {
        this.stop(false, true);
    }

    /**
     * Destroys the session without callback invocation.
     */
    public void destroySession() {
        this.stop(false, false);
    }

    /**
     * Sets visual progress of the block destruction.
     *
     * @param stage Visual progress stage (0-9)
     */
    public void setProgress(final int stage) {
        final ViewBlockData viewBlockData = this.blockView.getBlock(this.blockPosition);

        if (viewBlockData == null) {
            return;
        }
        final ViewBlockDigEvent event = new ViewBlockDigEvent(this.blockView, this.player, this.blockPosition, viewBlockData, stage);

        this.lastStage = event.getStage();
        this.blockView.setBlockProgress(this.entityId, this.blockPosition, this.lastStage);
    }

    /**
     * Calculates current progress percentage.
     *
     * @return Current progress as a float between 0.0 and 1.0
     */
    public float getCurrentProgress() {
        return (float) this.baseAccumulatedTime / (float) this.fullBreakDuration;
    }

    private void breakBlock() {
        this.blockView.getDigManager().complete(this.blockPosition, this.player);
        this.completeSession(); // The above call can technically not actually end the session but we definitely want this to stop ticking when we expect it to regardless

        this.blockView.breakBlock(this.player, this.blockPosition.blockX(), this.blockPosition.blockY(), this.blockPosition.blockZ());
    }

    private void stop(final boolean wasBlockBroken, final boolean callback) {
        if (!this.active) {
            return;
        }
        this.active = false;

        final long now = System.currentTimeMillis();
        final long sessionTime = now - this.startTime;
        final long totalTime = this.baseAccumulatedTime + Math.max(sessionTime, 0);

        try {
            this.cancel();
        } catch (final IllegalStateException e) {
            // Task might not be scheduled yet
        }
        if (callback) {
            this.cancelCallback.onCall(totalTime, this.lastStage, wasBlockBroken);
        }
    }

    @Override
    public void run() {
        this.update();
    }
}
