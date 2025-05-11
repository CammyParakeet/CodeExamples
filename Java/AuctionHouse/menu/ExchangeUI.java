package AuctionHouse.menu;

import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Provides an abstract base for user interfaces within the Grand Exchange system that only display
 * a single page. This class is used for creating UI elements where multiple pages are not
 * necessary, streamlining the interaction for simpler tasks or displays.
 *
 * <p>This class extends {@link ExchangeView} and initializes with a default of 6 rows
 *
 * @author Cammy
 */
public abstract class ExchangeUI extends ExchangeView {

  public ExchangeUI(
      @NotNull final Player owner,
      @NotNull final HideawayServer core,
      @NotNull final PlayerShopManager playerShopManager,
      @NotNull final GrandExchangeManager grandExchangeManager,
      @NotNull final ServerStatisticManager statisticManager) {
    this(owner, core, playerShopManager, grandExchangeManager, statisticManager, 6);
    this.page = 0;
  }

  public ExchangeUI(
      @NotNull final Player owner,
      @NotNull final HideawayServer core,
      @NotNull final PlayerShopManager playerShopManager,
      @NotNull final GrandExchangeManager grandExchangeManager,
      @NotNull final ServerStatisticManager statisticManager,
      int rows) {
    super(owner, core, playerShopManager, grandExchangeManager, statisticManager, rows);
  }

  @Override
  protected boolean hasNextPage() {
    return false;
  }

  @Override
  protected boolean hasPreviousPage() {
    return false;
  }
}
