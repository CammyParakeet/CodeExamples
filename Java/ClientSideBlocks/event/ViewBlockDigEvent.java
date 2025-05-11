package ClientSideBlocks.event;

import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public final class ViewBlockDigEvent extends PlayerViewEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * The position of the block being dug.
     */
    private final BlockPosition blockPosition;

    /**
     * The current {@link ViewBlockData} state of the block being dug.
     */
    private final ViewBlockData blockData;

    /**
     * The block destruction stage (0-9) of the block being dug.
     */
    private int stage;

    public ViewBlockDigEvent(
            @NotNull final BlockView view,
            @NotNull final Player player,
            @NotNull final BlockPosition blockPosition,
            @NotNull final ViewBlockData blockData,
            final int stage
    ) {
        super(view, player);

        this.blockPosition = blockPosition;
        this.blockData = blockData;
        this.stage = stage;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
