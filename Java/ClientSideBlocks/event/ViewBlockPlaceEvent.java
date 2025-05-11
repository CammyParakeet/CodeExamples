package ClientSideBlocks.event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event triggered when a player attempts to place a block within a {@link BlockView}.
 * <p>
 * Provides information about the block being placed, its position, the player placing the block,
 * and the block against which the new block is placed, if applicable.
 */
@Getter
@Setter
public class ViewBlockPlaceEvent extends PlayerViewEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * The position where the block is being placed.
     */
    private final BlockPosition blockPosition;

    /**
     * The {@link ViewBlockData} of the block against which the new block is being placed.
     * May be null if placement is not against another view block.
     */
    private final ViewBlockData placedAgainstBlockData;

    /**
     * The {@link ViewBlockData} representing the block being placed.
     * Modifiable to change the outcome of the event.
     */
    private ViewBlockData blockData;

    /**
     * Indicates if the event is cancelled. If true, the block placement will not occur.
     */
    private boolean cancelled;

    public ViewBlockPlaceEvent(
            @NotNull final BlockView view,
            @NotNull final Player player,
            @NotNull final BlockPosition blockPosition,
            @NotNull final ViewBlockData blockData,
            @Nullable final ViewBlockData placedAgainstBlockData
    ) {
        super(view, player);

        this.blockPosition = blockPosition;
        this.blockData = blockData;
        this.placedAgainstBlockData = placedAgainstBlockData;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
