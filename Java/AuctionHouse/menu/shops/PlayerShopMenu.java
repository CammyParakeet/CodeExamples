package AuctionHouse.menu.shops;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.menu.HideawayMenu;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.ExchangeShopList;
import lombok.NonNull;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Represents the menu interface for a player-owned shop within the exchange system. This class
 * handles the user interactions specific to a player's shop, such as purchasing and viewing items.
 *
 * @author Cammy
 */
public final class PlayerShopMenu extends ShopView {

  @Inject
  public PlayerShopMenu(
      @Assisted @NonNull Player owner,
      @Assisted @NonNull PlayerShopData shopData,
      @NonNull HideawayServer core,
      @NonNull PlayerShopManager playerShopManager,
      @NonNull GrandExchangeManager grandExchangeManager,
      @NonNull ServerStatisticManager statisticManager) {
    super(owner, shopData, core, playerShopManager, grandExchangeManager, statisticManager);
  }

  /**
   * Provides the click handler for items within the player shop. This handler initiates the item
   * purchase UI.
   *
   * @return A consumer handling inventory click events associated with shop items.
   */
  @Override
  protected BiConsumer<InventoryClickEvent, @NonNull Long> shopItemClickHandler() {
    return (event, price) -> {
      if (!(event.getWhoClicked() instanceof Player p)) return;
      this.pushMenuHistory(this.owner.getUniqueId());
      menuFactory.createItemPurchaseUI(p, Objects.requireNonNull(event.getCurrentItem()));
    };
  }

  @Override
  protected List<Pair<Key, String>> getBackgroundComponents() {
    List<Pair<Key, String>> title = new ArrayList<>();
    title.add(Pair.of(null, "GE_player_store_page"));
    title.addAll(getShopName());
    return title;
  }

  /**
   * Sets up the option section of the player shop menu, including functionality for selling and
   * buying items. todo right now selling is disabled and buying is a placeholder.
   */
  @Override
  protected void createOptionSection() {
    // add sell item
    setOptionSlot(8, 0, "shop_view.sell_item.disabled", event -> owner.sendMessage("Coming soon!"));
    // add buy item
    setOptionSlot(7, 0, "shop_view.buy_item.enabled", event -> {});
    // exit
    this.setExitButton(
        6,
        0,
        event -> {
          if (hasMenuHistory(owner.getUniqueId())) {
            @Nullable HideawayMenu previous = popAndPeekMenuHistory(owner.getUniqueId());
            if (previous != null) {
              // shop list needs to be re-cached with current impl
              if (previous instanceof ExchangeShopList) {
                menuFactory.createShopListMenu(this.owner);
                return;
              }
              previous.open(owner, true);
            }
          } else {
            this.close(owner);
          }
        });
  }
}
