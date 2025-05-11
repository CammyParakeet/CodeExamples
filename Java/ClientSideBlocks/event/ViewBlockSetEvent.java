package ClientSideBlocks.event;

import ClientSideBlocks.data.ViewBlockData;
import ClientSideBlocks.view.BlockView;
import org.jetbrains.annotations.NotNull;

@Getter
public class ViewBlockSetEvent extends ViewEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BlockPosition blockPosition;
    private final ViewBlockData blockData;

    public ViewBlockSetEvent(
            @NotNull final BlockView view,
            @NotNull final BlockPosition blockPosition,
            @NotNull final ViewBlockData blockData
    ) {
        super(view);

        this.blockPosition = blockPosition;
        this.blockData = blockData;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
