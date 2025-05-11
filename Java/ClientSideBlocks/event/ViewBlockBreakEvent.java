package ClientSideBlocks.event;

import ClientSideBlocks.data.ViewBlockData;
import ClientSideBlocks.utils.TriggerSource;
import ClientSideBlocks.view.BlockView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event triggered when a player attempts to break a block within a {@link BlockView}.
 * <p>
 * Provides access to the player performing the action, the position and original state of the block being broken,
 * and the state the block will transition to upon successful completion.
 */
@Getter
@Setter
public class ViewBlockBreakEvent extends PlayerViewEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * The position of the block being broken.
     */
    private final BlockPosition blockPosition;

    /**
     * The original {@link ViewBlockData} state of the block before being broken.
     */
    private final ViewBlockData originalBlockData;

    /**
     * The {@link ViewBlockData} state the block will have after being broken.
     * Modifiable to alter the outcome of the event.
     */
    private ViewBlockData outputBlockData;

    /**
     * Indicates if the event is cancelled. If true, the block break action will not proceed.
     */
    private boolean cancelled;

    /**
     * The source of this view block break
     */
    @Nullable
    private TriggerSource triggerSource;

    public boolean isPlayerTriggered() {
        return this.triggerSource == null || this.triggerSource.isPlayer();
    }

    public ViewBlockBreakEvent(
            @NotNull final BlockView view,
            @NotNull final Player player,
            @NotNull final BlockPosition blockPosition,
            @NotNull final ViewBlockData blockData,
            @NotNull final ViewBlockData outputBlockData,
            @Nullable final TriggerSource triggerSource
    ) {
        super(view, player);

        this.blockPosition = blockPosition;
        this.originalBlockData = blockData;
        this.outputBlockData = outputBlockData;
        this.triggerSource = triggerSource;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}