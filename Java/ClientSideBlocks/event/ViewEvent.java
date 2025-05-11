package ClientSideBlocks.event;

import ClientSideBlocks.view.BlockView;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all events involving a {@link BlockView}.
 * <p>
 * Events extending this class represent interactions or actions within the context of a BlockView,
 * providing access to the associated view instance.
 */
@Getter
public abstract class ViewEvent extends Event {

    /**
     * The {@link BlockView} instance associated with this event.
     */
    private final BlockView view;

    /**
     * Constructs a new ViewEvent tied to the specified BlockView, executed synchronously.
     *
     * @param view the BlockView related to this event
     */
    public ViewEvent(@NotNull final BlockView view) {
        this(view, true);
    }

    /**
     * Constructs a new ViewEvent tied to the specified BlockView, specifying whether it's asynchronous.
     *
     * @param view  the BlockView related to this event
     * @param async whether the event is asynchronous
     */
    public ViewEvent(@NotNull final BlockView view, final boolean async) {
        super(async);
        this.view = view;
    }
}
