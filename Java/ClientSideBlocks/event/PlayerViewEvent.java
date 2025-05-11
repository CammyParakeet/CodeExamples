package ClientSideBlocks.event;

import ClientSideBlocks.view.BlockView;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base event class representing player-specific actions within a {@link BlockView}.
 * <p>
 * Events extending this class provide direct access to the player who initiated the action.
 */
@Getter
public abstract class PlayerViewEvent extends ViewEvent {

    /**
     * The player associated with this event.
     */
    private final Player player;

    /**
     * Constructs a PlayerViewEvent linked to a specific BlockView and player.
     *
     * @param view   the BlockView involved in the event
     * @param player the player involved in the event
     */
    public PlayerViewEvent(@NotNull final BlockView view, @NotNull final Player player) {
        super(view);

        this.player = player;
    }

    public PlayerViewEvent(@NotNull final BlockView view, @NotNull final Player player, final boolean async) {
        super(view, async);

        this.player = player;
    }
}
