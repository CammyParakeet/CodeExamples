package AuctionHouse.menu;

import com.pixelhideaway.commons.base.data.ItemFilterCriteria;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.injection.MenuFactory;
import com.pixelhideaway.core.menu.HideawayMenu;
import com.pixelhideaway.core.menu.PageableHideawayMenu;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.grandexchange.injection.ExchangeMenuFactory;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.utils.Filterable;
import com.pixelhideaway.grandexchange.utils.ShopSorted;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Represents an abstract base class for creating paginated, filterable, and sortable exchange views
 * within the Hideaway server's grand exchange system. This class extends {@link
 * PageableHideawayMenu} and implements {@link Filterable} and {@link ShopSorted} interfaces to
 * provide dynamic content filtering and sorting capabilities.
 *
 * @author Cammy
 */
public abstract class ExchangeView extends PageableHideawayMenu implements Filterable, ShopSorted {

  protected final Player owner;

  protected final GrandExchangeManager exchangeManager;
  protected final PlayerShopManager playerShopManager;
  protected final ServerStatisticManager statisticManager;
  protected final ExchangeMenuFactory menuFactory;
  protected final MenuFactory coreMenuFactory;

  protected final int rows;

  protected ItemFilterCriteria currentFilter = ItemFilterCriteria.empty();
  protected ShopSort currentSortOption = ShopSort.CREATION_ASCENDING;

  public ExchangeView(
      @NotNull final Player owner,
      @NotNull final HideawayServer core,
      @NotNull final PlayerShopManager playerShopManager,
      @NotNull final GrandExchangeManager exchangeManager,
      @NotNull final ServerStatisticManager statisticManager,
      int rows) {
    super(core, rows);
    this.rows = rows;
    this.playerShopManager = playerShopManager;
    this.exchangeManager = exchangeManager;
    this.statisticManager = statisticManager;
    this.owner = owner;
    this.menuFactory = exchangeManager.getMenuFactory();
    this.coreMenuFactory = exchangeManager.getCoreMenuFactory();
  }

  public ExchangeView(
      @NotNull final Player owner,
      @NotNull final HideawayServer core,
      @NotNull final PlayerShopManager playerShopManager,
      @NotNull final GrandExchangeManager exchangeManager,
      @NotNull final ServerStatisticManager statisticManager) {
    this(owner, core, playerShopManager, exchangeManager, statisticManager, 6);
  }

  /** Opens this view for the set owner */
  public void open() {
    this.open(owner);
  }

  /**
   * Get the {@link Player} that this view will belong to
   *
   * @return the owning player
   */
  public @NotNull Player getOwner() {
    return owner;
  }

  /** Plays a click sound effect for the owner when interacting with the view. */
  protected void playClickSound() {
    this.playClickSound(owner);
  }

  protected void setExitButton() {
    setExitButton(8, 0);
  }

  /**
   * Sets an exit button at the specified coordinates in the inventory UI.
   *
   * @param x The x-coordinate of the slot.
   * @param y The y-coordinate of the slot.
   */
  protected void setExitButton(int x, int y) {
    setExitButton(
        x,
        y,
        (event) -> {
          if (hasMenuHistory(owner.getUniqueId())) {
            @Nullable HideawayMenu previous = popAndPeekMenuHistory(owner.getUniqueId());
            if (previous != null) previous.open(owner, true);
          } else {
            this.close(owner);
          }
        });
  }

  /**
   * Sets an exit button with a custom action.
   *
   * @param x The x-coordinate of the slot.
   * @param y The y-coordinate of the slot.
   * @param consumer The consumer that handles the click event on the exit button.
   */
  protected void setExitButton(int x, int y, @NotNull Consumer<InventoryClickEvent> consumer) {
    setOptionSlot(x, y, "item.shop_menu.back", consumer);
  }

  @Override
  public void applyFilters(ItemFilterCriteria criteria) {
    if (this.currentFilter.matches(criteria)) return;
    this.currentFilter = criteria;
    resetPages();
  }

  @Override
  public ItemFilterCriteria getCurrentFilter() {
    return this.currentFilter;
  }

  @Override
  public ShopSort getCurrentSortOption() {
    return this.currentSortOption;
  }

  @Override
  public void applySort(ShopSort sortOption) {
    if (this.currentSortOption.equals(sortOption)) return;
    this.currentSortOption = sortOption;
    resetPages();
  }
}
