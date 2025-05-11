package AuctionHouse.menu.shops;

import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.util.item.ItemUtil;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.ExchangeView;
import com.pixelhideaway.grandexchange.utils.ShopMenuUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides a base for creating shop-related views within the game's exchange system. This abstract
 * class extends {@link ExchangeView} and includes functionalities specific to handling shop data
 * and interactions.
 *
 * @see ExchangeView
 * @author Cammy
 */
@Slf4j
@Getter
public abstract class ShopView extends ExchangeView {

  protected PlayerShopData shopData;

  protected final NamespacedKey shopItemKey = exchangeManager.getExchangePlugin().getShopItemKey();
  protected final NamespacedKey shopPriceKey =
      exchangeManager.getExchangePlugin().getShopItemPrice();
  protected final NamespacedKey shopQuantityKey =
      exchangeManager.getExchangePlugin().getShopItemQuantity();

  public ShopView(
      @NotNull Player owner,
      @NotNull PlayerShopData shopData,
      @NotNull HideawayServer core,
      @NotNull PlayerShopManager playerShopManager,
      @NotNull GrandExchangeManager grandExchangeManager,
      @NotNull ServerStatisticManager statisticManager) {
    super(owner, core, playerShopManager, grandExchangeManager, statisticManager);
    this.shopData = shopData;
    open();
  }

  protected List<Pair<Key, String>> getShopName() {
    return ShopMenuUtils.createTitleComponents(
        ShopMenuUtils.formatShopName(this.shopData.shopName(), 16), -1);
  }

  /**
   * Initializes the shop by loading and setting up the shop items.
   *
   * @return A future that provides a {@link TitleBuilder} to set the inventory title
   *     post-initialization.
   */
  protected CompletableFuture<TitleBuilder> initStore() {
    return exchangeManager
        .createShopItems(this.shopData.itemIds())
        .thenApply(
            items ->
                new Initializer(this.owner)
                    .sectionPosition(1, 2)
                    .sectionSize(7, 2)
                    .items(alterShopItems(items))
                    .clickHandler(getShopItemHandler())
                    .titleElements(getBackgroundComponents())
                    .initialize());
  }

  /**
   * Alters the list of item stacks to fit the shop display requirements.
   *
   * @param itemsIn The list of original item stacks.
   * @return A list of altered item stacks.
   */
  protected List<ItemStack> alterShopItems(List<ItemStack> itemsIn) {
    return itemsIn.stream().filter(Objects::nonNull).map(this::alterShopItem).toList();
  }

  /**
   * Alters a single item stack for display in the shop view. Override this method to customize item
   * display.
   *
   * @param item The original item stack.
   * @return The altered item stack.
   */
  protected ItemStack alterShopItem(@NonNull ItemStack item) {
    return item;
  }

  /**
   * Provides the handler for shop item clicks.
   *
   * @return A consumer that handles inventory click events related to shop items.
   */
  protected Consumer<InventoryClickEvent> getShopItemHandler() {
    return event -> {
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null) return;

      Long itemPrice = ItemUtil.getPDC(clicked, this.shopPriceKey, Long.class);
      if (itemPrice == null) return;

      playClickSound();
      shopItemClickHandler().accept(event, itemPrice);
    };
  }

  /**
   * Adds new shop items to the current shop data.
   *
   * @param newItems The list of new shop items to be added.
   */
  protected void addNewShopItems(List<ShopItemData> newItems) {
    this.shopData = shopData.withAddedItems(newItems);
  }

  /**
   * Must be implemented to provide the specific item click behavior in the shop view.
   *
   * @return A bi-consumer that handles the logic for item clicks, including the price parameter.
   */
  protected abstract BiConsumer<InventoryClickEvent, @NotNull Long> shopItemClickHandler();

  /**
   * Must be implemented to provide the identifiers for background UI elements.
   *
   * @return List for {@link com.pixelhideaway.core.menu.HideawayMenu.TitleBuilder} array for
   *     TitleBuilders.
   */
  protected abstract List<Pair<Key, String>> getBackgroundComponents();

  /** Must be implemented to create sections with options in the shop view. */
  protected abstract void createOptionSection();

  @Override
  protected void populate(@NotNull Player player) {
    initStore()
        .thenAccept(
            titleBuilder -> {
              createOptionSection();
              this.setTitle(titleBuilder.build());
            })
        .exceptionally(
            e -> {
              logger.error("Problem creating title builder? ", e);
              return null;
            });
  }
}
