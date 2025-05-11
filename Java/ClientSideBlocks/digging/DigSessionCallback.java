package ClientSideBlocks.digging;

/**
 * Callback interface invoked upon completion, cancellation, or interruption of a block destruction session.
 */
@FunctionalInterface
public interface DigSessionCallback {
    /**
     * Called when a block destruction session ends.
     *
     * @param accumulatedTimeMs Total accumulated time of the session in milliseconds
     * @param stage             Final visual stage of the destruction progress (0-9)
     * @param blockWasBroken    True if the block was successfully broken; false if cancelled or interrupted
     */
    void onCall(long accumulatedTimeMs, int stage, boolean blockWasBroken);
}
