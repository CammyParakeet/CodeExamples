package AuctionHouse.menu.shops.editor;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.data.item.ItemDao;
import com.pixelhideaway.core.data.item.ItemData;
import com.pixelhideaway.core.menu.helper.ConfirmMenu.Builder;
import com.pixelhideaway.core.menu.helper.HideawayAnvil;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.util.ThreadUtil;
import com.pixelhideaway.core.util.item.ItemUtil;
import com.pixelhideaway.core.util.item.give.ItemReturnResult;
import com.pixelhideaway.core.util.item.give.ItemReturnSettings;
import com.pixelhideaway.grandexchange.GrandExchange;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.ExchangeUI;
import com.playhideaway.menus.InvisibleMenuItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.wesjd.anvilgui.AnvilGUI;
import net.wesjd.anvilgui.AnvilGUI.ResponseAction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Provides a user interface for setting or editing the price of an item within a player's shop.
 * This UI allows the player to input a new price and decide whether to update an existing item or
 * add a new one.
 *
 * @author Cammy
 */
@Slf4j
public final class PriceEditorUI extends ExchangeUI {

  private final PlayerShopData shopData;
  private final NamespacedKey shopItemKey;

  private final ItemDao itemDao;

  private boolean isSubmitting = false;

  /** Indicates whether the item being initially set is new, or if it exists. */
  private boolean isNewItem;

  /** Holds the instance of the currently open Anvil GUI. */
  private AnvilGUI openAnvil;

  @Inject
  public PriceEditorUI(
      @Assisted @NonNull final Player owner,
      @Assisted @NonNull final PlayerShopData shopData,
      @NonNull final GrandExchange exchange,
      @NonNull final PlayerShopManager playerShopManager,
      @NonNull final GrandExchangeManager grandExchangeManager,
      @NonNull final ServerStatisticManager statisticManager,
      @NonNull final HideawayServer core,
      @NonNull final ItemDao itemDao) {
    super(owner, core, playerShopManager, grandExchangeManager, statisticManager, 1);
    this.itemDao = itemDao;
    this.shopData = shopData;
    shopItemKey = exchange.getShopItemKey();

    Bukkit.getPluginManager().registerEvents(this, core);
  }

  @Override
  protected void populate(@NotNull Player player) {}

  /**
   * Opens the Anvil GUI to allow the player to set or edit the price of an item.
   *
   * @param initialInput The initial item stack to display in the GUI, possibly null.
   * @param isNewItem Indicates whether the item is new to the shop or existing.
   * @return The configured AnvilGUI instance ready to be shown to the player.
   */
  public AnvilGUI openPriceUI(@Nullable ItemStack initialInput, @NonNull Boolean isNewItem) {
    isSubmitting = false;
    this.isNewItem = isNewItem;

    final ItemStack displayItem =
        (initialInput == null || initialInput.getType().isAir())
            ? ItemStack.empty()
            : initialInput.clone();

    if (!isNewItem && !displayItem.isEmpty()) {
      displayItem.editMeta(
          meta -> {
            List<Component> lore = new ArrayList<>(Objects.requireNonNull(meta.lore()));
            lore.add(Component.empty());
            lore.addAll(gameTextDao.getResolvedTextMultiple("exchange.allow_add_quantity"));
            meta.lore(lore);
          });
    }

    // Create custom Anvil
    AnvilGUI.Builder builder =
        HideawayAnvil.createBuilder(entrypoint, this::handleItemOnClose, this::handleGiveItemResult)
            .jsonTitle(getTitleJson())
            .itemRight(displayItem)
            .itemLeft(InvisibleMenuItem.get())
            .itemOutput(InvisibleMenuItem.get())
            .onClick(this::handleUIClick);

    if (isNewItem) {
      builder.interactableSlots(AnvilGUI.Slot.INPUT_RIGHT);
    }

    AnvilGUI gui = builder.open(this.owner);
    this.openAnvil = gui;
    return gui;
  }

  /** Util to get a gson serialized title component for the anvil title packet */
  private String getTitleJson() {
    return GsonComponentSerializer.gson()
        .serialize(new TitleBuilder("GE_player_store_page_sell").build());
  }

  /**
   * Handles user clicks within the Anvil GUI.
   *
   * @param slot The slot that was clicked.
   * @param state The current state snapshot of the Anvil GUI.
   * @return A list of response actions to perform based on the click.
   */
  private List<ResponseAction> handleUIClick(int slot, AnvilGUI.StateSnapshot state) {
    Player player = state.getPlayer();
    ItemStack cursorItem = player.getItemOnCursor();

    /* Middle Slot */
    if (slot == AnvilGUI.Slot.INPUT_RIGHT) {
      if (isNewItem) {
        ItemStack slotItem = state.getRightItem();
        if (cursorItem == null || cursorItem.isEmpty() || cursorItem.getType().isAir()) {
          return List.of();
        }

        List<ShopItemData> matchingItems =
            exchangeManager.findMatchingShopItem(this.shopData, cursorItem);
        if (!matchingItems.isEmpty()) {
          isSubmitting = true;
          if (slotItem != null && !slotItem.isEmpty() && !slotItem.getType().isAir()) {
            ItemUtil.giveItem(player.getUniqueId(), slotItem)
                .thenAcceptAsync(
                    result -> {
                      handleGiveItemResult(result);
                      player.setItemOnCursor(ItemStack.empty());
                    },
                    Bukkit.getScheduler().getMainThreadExecutor(plugin));
          }

          menuFactory.createMatchingItemsView(player, shopData, cursorItem, matchingItems);
          return List.of(ResponseAction.close());
        }
      }
    }

    /* We have clicked return/close */
    if (slot == AnvilGUI.Slot.INPUT_LEFT) {
      playClickSound();
      return openPrevMenu();
    }

    /* We have clicked submit */
    if (slot == AnvilGUI.Slot.OUTPUT) {
      playClickSound();
      final String input = state.getText();
      final ItemStack inSlotItem = state.getRightItem();
      return handlePriceInput(inSlotItem, input);
    }
    return List.of();
  }

  /**
   * Handles the user interaction within the Anvil GUI when they click on a slot. It manages the
   * validation and processing of the price entered by the user.
   *
   * @param inSlotItem The item present in the slot where the user interacted.
   * @param input The text input provided by the user.
   * @return A list of response actions based on the user's input and the validation result.
   */
  private List<ResponseAction> handlePriceInput(ItemStack inSlotItem, String input) {
    if (inSlotItem == null || inSlotItem.getType() == Material.AIR) {
      entrypoint
          .getNotificationUtils()
          .queueNotification(owner.getUniqueId(), "WARNING", "No Item Present!");
      return List.of();
    }

    if (input == null || input.isEmpty() || input.isBlank()) {
      entrypoint
          .getNotificationUtils()
          .queueNotification(owner.getUniqueId(), "WARNING", "No Price Set!");
      return List.of();
    }

    if (!input.matches("\\d+")) {
      entrypoint
          .getNotificationUtils()
          .queueNotification(owner.getUniqueId(), "WARNING", "Invalid price!");
      return openPrevMenu();
    }

    long price;
    try {
      price = Long.parseLong(input);
      if (price < 1) throw new NumberFormatException("small");
      if (price > getMaxSellPrice(owner)) throw new NumberFormatException("big");
    } catch (NumberFormatException e) {
      String message =
          "Invalid price! "
              + (e.getMessage().equals("small")
                  ? "Price cannot be lower than 1"
                  : "Price cannot be bigger than " + getMaxSellPrice(this.owner));

      entrypoint.getNotificationUtils().queueNotification(owner.getUniqueId(), "WARNING", message);
      return openPrevMenu();
    }

    isSubmitting = true;
    return List.of(ResponseAction.run(() -> handleItemSubmission(inSlotItem, price)));
  }

  /**
   * Handles inventory clicks that aim to combine quantities of an existing item in the shop. This
   * method checks if the clicked item matches the item in the anvil slot and then proceeds to
   * combine quantities.
   *
   * @param event The inventory click event that contains the click context.
   */
  @EventHandler
  public void handleAddQuantity(InventoryClickEvent event) {
    if (this.isNewItem) return;
    if (this.openAnvil != null
        && event.getView().getTopInventory().equals(this.openAnvil.getInventory())) {
      if (event.getClickedInventory() != this.openAnvil.getInventory()) {
        @Nullable final ItemStack clickedItem = event.getCurrentItem();
        @Nullable
        final ItemStack anvilItem =
            this.openAnvil.getInventory().getItem(AnvilGUI.Slot.INPUT_RIGHT);
        if (clickedItem == null
            || clickedItem.getType().isAir()
            || exchangeManager.getSignedData(clickedItem).isPresent()
            || anvilItem == null
            || clickedItem.getType().isAir()) return;

        @Nullable final ItemData anvilItemData = this.itemDao.from(anvilItem);
        @Nullable final ItemData clickedData = this.itemDao.from(clickedItem);
        if (clickedData == null || anvilItemData == null) return;

        if (clickedData.getId().equals(anvilItemData.getId())) {
          event.setCancelled(true);
          this.owner.getInventory().setItem(event.getSlot(), ItemStack.empty());
          confirmCombineItems(anvilItem, clickedItem.clone());
        }
      }
    }
  }

  /**
   * Opens a confirmation menu to confirm the combination of quantities for an existing item in the
   * shop. If confirmed, it updates the item's quantity in the shop.
   *
   * @param existingItem The item already present in the shop.
   * @param addingItem The item whose quantity is being added to the existing item.
   */
  private void confirmCombineItems(@NotNull ItemStack existingItem, @NotNull ItemStack addingItem) {
    Inventory i = this.owner.getInventory();
    new Builder()
        .confirmItem(
            InvisibleMenuItem.get(
                this.gameTextDao.getResolvedTextMultiple(
                    "exchange.update_quantity_confirm",
                    TagResolver.resolver("item", Tag.inserting(addingItem.displayName())))))
        .onConfirm(
            event -> {
              playClickSound();
              @Nullable
              ShopItemData existingData = this.exchangeManager.getShopItemFromStack(existingItem);
              if (existingData == null) {
                log.error("Could not extract data from existing shop item? {}", existingItem);
                this.close(this.owner);
                return;
              }
              final ShopItemData newQuantityData = existingData.adjustQuantity(1);
              this.playerShopManager
                  .upsertItemForShop(newQuantityData)
                  .thenCompose(
                      response -> {
                        @NotNull final ShopItemData updatedData = response.updatedItem();
                        this.exchangeManager.processItemUpdate(updatedData);
                        return this.exchangeManager.refreshItemStack(updatedData);
                      })
                  .thenAcceptAsync(
                      updatedItem -> PriceEditorUI.this.openPriceUI(updatedItem, false),
                      ThreadUtil.SYNC_EXECUTOR);
            })
        .onCancel(
            event -> {
              playClickSound();
              PriceEditorUI.this.openPriceUI(existingItem, false);
              ItemUtil.giveItem(this.owner.getUniqueId(), addingItem, new ItemReturnSettings())
                  .thenAccept(this::handleGiveItemResult);
            })
        .build()
        .open(this.owner);
  }

  /**
   * Handles the closure of the Anvil GUI, managing the return of items to the player if necessary.
   *
   * @param state The state snapshot of the Anvil GUI at the time of closure.
   */
  private void handleItemOnClose(AnvilGUI.StateSnapshot state) {
    ItemStack cursorItem = state.getPlayer().getItemOnCursor();
    if (cursorItem != null && !cursorItem.isEmpty() && !cursorItem.getType().isAir()) {
      ItemUtil.giveItem(this.owner.getUniqueId(), cursorItem)
          .thenAcceptAsync(this::handleGiveItemResult);
    }

    this.openAnvil = null;
    // if we're submitting a possible item - don't return it to player here
    if (isSubmitting) return;

    ItemStack inSlotItem = state.getRightItem();
    // do nothing for empty
    if (inSlotItem == null || inSlotItem.isEmpty() || inSlotItem.getType().isAir()) return;
    // check for sold item
    if (ItemUtil.hasPDC(inSlotItem, shopItemKey)) return;
    ItemUtil.giveItem(this.owner.getUniqueId(), inSlotItem)
        .thenAcceptAsync(this::handleGiveItemResult);
  }

  /**
   * Submits the item for pricing update or addition to the shop based on the user's input.
   *
   * @param itemToUpdate The ItemStack to update or add.
   * @param price The new price set by the user.
   */
  public void handleItemSubmission(@NonNull ItemStack itemToUpdate, long price) {
    ShopItemData shopItemData = exchangeManager.getOrCreateShopItem(itemToUpdate, shopData.id());
    if (isNewItem) shopItemData = shopItemData.withNewQuantity(1);
    shopItemData = shopItemData.withNewPrice(price);
    final int quantity = shopItemData.quantity();

    playerShopManager
        .upsertItemForShop(shopItemData)
        .thenAccept(
            response -> {
              PlayerShopData updatedShop = response.updatedShop();
              if (updatedShop.itemIds().isEmpty()) {
                logger.error("Something went wrong saving item for {}", this.owner.getName());
                reOpen(itemToUpdate, this.isNewItem);
                return;
              }
              this.exchangeManager.processItemUpdate(response.updatedItem());

              // after receiving updated data - reopen editor
              this.menuFactory.createShopEditorMenu(this.owner, updatedShop);
            })
        .thenRun(
            () ->
                owner.sendMessage(
                    this.gameTextDao.getResolvedText(
                        "exchange.update_price_message",
                        TagResolver.resolver("quantity", Tag.preProcessParsed("" + quantity)),
                        TagResolver.resolver("item", Tag.inserting(itemToUpdate.displayName())),
                        TagResolver.resolver(
                            "suffix", Tag.preProcessParsed(quantity == 1 ? "" : "'s")),
                        TagResolver.resolver("price", Tag.preProcessParsed("" + price)))))
        .exceptionally(
            e -> {
              logger.error("Error while adding new item", e);
              reOpen(itemToUpdate, this.isNewItem);
              return null;
            });
  }

  private void reOpen(@Nullable ItemStack stack, boolean isNewItem) {
    playClickSound();
    menuFactory.createPriceEditorUI(this.owner, this.shopData).openPriceUI(stack, isNewItem);
  }

  /**
   * Retrieves the maximum allowable sell price for items in the shop, which may be specific to the
   * player or global.
   *
   * @param player The player for whom the max sell price is being retrieved.
   * @return The maximum sell price as a long value.
   */
  private Long getMaxSellPrice(@NonNull Player player) {
    return 1000000L;
  }

  /**
   * Returns to the previous menu, currently the shop editor menu.
   *
   * @return A list of actions that close the current UI and open the previous one.
   */
  private List<ResponseAction> openPrevMenu() {
    return List.of(
        ResponseAction.close(),
        (gui, player) -> menuFactory.createShopEditorMenu(this.owner, this.shopData));
  }

  /**
   * Decides what information to give the player when an item is returned to them after the
   * inventory closes
   *
   * @param result the return result of an {@link ItemUtil#giveItem(UUID, ItemStack)} usage
   */
  private void handleGiveItemResult(ItemReturnResult result) {
    String location = "";
    switch (result) {
      case INVALID_ITEM -> {
        return;
      }
      case NO_SPACE_FOUND -> {
        // todo undo transaction? Or check for space first..
        return;
      }
      case ADDED_TO_MAIL -> location = "Mail";
      case ADDED_TO_LUGGAGE -> location = "Luggage";
    }
    if (!location.isEmpty()) {
      this.owner.sendMessage("No inventory space - returning item to " + location);
    }
  }
}
