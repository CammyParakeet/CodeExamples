package AuctionHouse.menu.ui;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.client.Clients;
import com.pixelhideaway.commons.base.grandexchange.ExchangeUpdateResponse;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.data.item.ItemDao;
import com.pixelhideaway.core.data.sound.SoundData;
import com.pixelhideaway.core.menu.HideawayMenu;
import com.pixelhideaway.core.menu.helper.ConfirmMenu;
import com.pixelhideaway.core.util.ThreadUtil;
import com.pixelhideaway.core.util.item.ItemUtil;
import com.pixelhideaway.core.util.item.give.ItemReturnResult;
import com.pixelhideaway.core.util.item.give.ItemReturnSettings;
import com.pixelhideaway.grandexchange.injection.ExchangeMenuFactory;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.ExchangeItemMenu;
import com.pixelhideaway.grandexchange.menu.ExchangeShopList;
import com.playhideaway.menus.InvisibleMenuItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents a user interface for confirming the purchase of items within the exchange system. This
 * UI handles the entire purchase process, including confirmation dialogs, coin deductions, and item
 * acquisitions. It provides visual and functional elements to facilitate the transaction. If any
 * part of the transaction fails, it ensures appropriate feedback is provided to the user. TODO -
 * transaction rollback
 *
 * @author Cammy
 */
@Slf4j
public final class ItemPurchaseUI extends ConfirmMenu {

  private static final Slot ITEM_SLOT = new Slot(4, 1);

  private final Player owner;
  private final ItemStack purchaseItem;
  private final ShopItemData shopItemData;

  private final GrandExchangeManager exchangeManager;
  private final PlayerShopManager shopManager;
  private final ItemDao itemDao;
  private final ExchangeMenuFactory menuFactory;

  private final SoundData failSound;
  private final SoundData buySound;

  private final long price;

  private boolean currentlyProcessing = false;

  @Inject
  public ItemPurchaseUI(
      @Assisted @NonNull Player owner,
      @Assisted @NonNull ItemStack item,
      @NotNull HideawayServer core,
      @NotNull GrandExchangeManager exchangeManager,
      @NotNull PlayerShopManager shopManager,
      @NotNull ExchangeMenuFactory menuFactory,
      @NotNull ItemDao itemDao) {
    super(core);
    this.owner = owner;
    this.purchaseItem = item.clone();
    this.shopItemData = exchangeManager.getShopItemFromStack(purchaseItem);
    if (shopItemData == null)
      throw new IllegalStateException("Cannot purchase an unregistered shop item");
    this.itemDao = itemDao;
    this.shopManager = shopManager;
    this.exchangeManager = exchangeManager;
    this.menuFactory = menuFactory;
    this.failSound = soundDao.get("ui.notification.error").orElseThrow();
    this.buySound = soundDao.get("ui.click_purchase").orElseThrow();
    this.price = shopItemData.price();

    if (shopItemData.quantity() <= 0) {
      this.failSound.play(owner);
    } else this.open(owner);
  }

  /**
   * Initiates the purchase process, including coin deduction and item acquisition.
   *
   * @param player The player attempting the purchase.
   */
  private void initiatePurchase(Player player) {
    if (this.currentlyProcessing) return;
    this.currentlyProcessing = true;
    CompletableFuture.supplyAsync(() -> Clients.COIN.removeCoins(player.getUniqueId(), price))
        .thenApplyAsync(success -> handleCoinResult(player, success))
        .thenCompose(response -> processPurchaseResult(player, response))
        .thenAcceptAsync(
            result -> handleFinalPurchaseResult(player, result), ThreadUtil.SYNC_EXECUTOR)
        /* Handle exception */
        .exceptionally(
            ex -> {
              log.error(
                  "Something went wrong with player {}'s purchase for {}\n",
                  player.getName(),
                  this.shopItemData,
                  ex);
              purchaseFail(player, true);
              return null;
            });
  }

  /**
   * Handles the result of coin removal.
   *
   * @param player The player involved in the transaction.
   * @param success Whether the coins were successfully removed.
   * @return A pair of success status and potentially updated shop data.
   */
  private Pair<Boolean, ExchangeUpdateResponse> handleCoinResult(Player player, Boolean success) {
    if (!success) {
      purchaseFail(player, false);
      return new Pair<>(false, null);
    }
    return new Pair<>(
        true, Clients.EXCHANGE_CLIENT.processItemSale(player.getUniqueId(), this.shopItemData));
  }

  /**
   * Processes the result of the item purchase attempt.
   *
   * @param response The response containing the purchase success status and shop data.
   * @return A CompletableFuture representing the pending result of item addition.
   */
  private CompletableFuture<ItemReturnResult> processPurchaseResult(
      Player player, Pair<Boolean, ExchangeUpdateResponse> response) {
    if (!response.getKey()) {
      return CompletableFuture.completedFuture(ItemReturnResult.INVALID_ITEM);
    }

    // process local shop update
    PlayerShopData shopData = response.getValue().updatedShop();
    this.shopManager.processShopUpdate(shopData);

    // process local item update
    ShopItemData updatedShopItem = response.getValue().updatedItem();
    this.exchangeManager.processItemUpdate(updatedShopItem);

    ItemStack realItem = itemDao.create(shopItemData.itemId());
    if (realItem == null) {
      throw new IllegalStateException(
          "Could not create configured item from shop item data " + shopItemData.itemId());
    }

    return ItemUtil.giveItem(player.getUniqueId(), realItem, new ItemReturnSettings());
  }

  /**
   * Handles the final result of the purchase process, including user notification.
   *
   * @param result The result of attempting to add the item to the player's inventory.
   */
  private void handleFinalPurchaseResult(Player player, ItemReturnResult result) {
    this.buySound.play(this.owner);

    String suffix = ".";
    switch (result) {
      case INVALID_ITEM -> {
        return; // purchase failed somewhere
      }
      case NO_SPACE_FOUND -> {
        // todo undo transaction? Or check for space first..
        return;
      }
      case ADDED_TO_MAIL -> suffix = "Mail!";
      case ADDED_TO_LUGGAGE -> suffix = "Luggage!";
      case ADDED_TO_INVENTORY -> suffix = "Inventory!";
    }

    TagResolver resolver =
        TagResolver.resolver(
            TagResolver.resolver("item", Tag.inserting(this.purchaseItem.displayName())),
            TagResolver.resolver("price", Tag.preProcessParsed(String.valueOf(this.price))),
            TagResolver.resolver("location", Tag.preProcessParsed(suffix)));

    player.sendMessage(this.gameTextDao.getResolvedText("exchange.purchase_message", resolver));

    // open the context menu
    handleReturn();
  }

  @Override
  protected Consumer<InventoryClickEvent> confirmHandler() {
    return event -> {
      if (!(event.getWhoClicked().equals(this.owner))) return;
      playClickSound(this.owner);
      initiatePurchase(this.owner);
    };
  }

  @Override
  protected Consumer<InventoryClickEvent> cancelHandler() {
    return event -> {
      if (!(event.getWhoClicked().equals(this.owner))) return;
      playClickSound(this.owner);
      handleReturn();
    };
  }

  /**
   * Handles a failed purchase attempt by informing the player and logging the failure if necessary.
   *
   * @param player The player who attempted the purchase.
   * @param isException Flag indicating whether the failure was due to an exception.
   */
  private void purchaseFail(Player player, boolean isException) {
    // todo return coins??
    String reason = isException ? "Internal Error" : "Insufficient Funds!";
    entrypoint.getNotificationUtils().queueNotification(player.getUniqueId(), "ERROR", reason);
  }

  /** Returns to the previous menu or closes the UI if there is no previous menu. */
  private void handleReturn() {
    @Nullable HideawayMenu previous = getHistory(this.owner.getUniqueId()).pop();
    if (previous != null) {
      // shop list needs to be re-cached with current impl
      if (previous instanceof ExchangeShopList) {
        menuFactory.createShopListMenu(this.owner);
        return;
      } else if (previous instanceof ExchangeItemMenu) {
        menuFactory.createExchangeItemMenu(this.owner);
        return;
      }
      previous.open(owner, false);
    } else this.close(owner);
  }

  @Override
  protected void populate(@NotNull Player player) {
    super.populate(player);
    this.setSlot(ITEM_SLOT, purchaseItem, null);
  }

  @Override
  protected void setButtons() {
    this.setSlot(1, 2, getCancelButton());
    this.setSlot(7, 2, getConfirmButton());
  }

  @Override
  protected ButtonPanel getConfirmButton() {
    TagResolver resolver = TagResolver.resolver("price", Tag.preProcessParsed("" + this.price));

    return new ButtonPanel(
        1,
        1,
        InvisibleMenuItem.get(
            this.gameTextDao.getResolvedTextMultiple("exchange.confirm_purchase", resolver)),
        this.confirmHandler());
  }

  @Override
  protected TitleBuilder createConfirmUI() {
    return new TitleBuilder("item_confirm");
  }
}
