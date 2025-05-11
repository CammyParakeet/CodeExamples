package AuctionHouse.menu.shops.editor;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.menu.helper.ConfirmMenu;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.util.ThreadUtil;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.shops.ShopView;
import com.playhideaway.menus.InvisibleMenuItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.wesjd.anvilgui.AnvilGUI.ResponseAction;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Provides a user interface for managing and editing items in a player's shop. This includes
 * functionalities for item removal, price adjustments, and other modifications to items directly
 * from the shop's inventory.
 *
 * @author Cammy
 */
@Slf4j
public final class ShopEditorMenu extends ShopView {

  /** Lore to be displayed for each item in the editor. */
  private final List<Component> editorLore;

  @Inject
  public ShopEditorMenu(
      @Assisted @NonNull Player owner,
      @Assisted @NonNull PlayerShopData shopData,
      @NonNull HideawayServer core,
      @NonNull PlayerShopManager playerShopManager,
      @NonNull GrandExchangeManager grandExchangeManager,
      @NonNull ServerStatisticManager statisticManager) {
    super(owner, shopData, core, playerShopManager, grandExchangeManager, statisticManager);
    this.editorLore = gameTextDao.getResolvedTextMultiple("shop_editor.item_lore");
  }

  /**
   * Modifies each shop item to add custom lore info for editing purposes.
   *
   * @param item The original item stack.
   * @return An altered version of the item stack with additional lore.
   */
  @Override
  protected ItemStack alterShopItem(@NonNull ItemStack item) {
    ItemStack clone = item.clone();
    clone.editMeta(
        meta -> {
          List<Component> newLore = meta.lore();
          if (newLore == null) return;
          newLore.add(Component.empty());
          newLore.addAll(editorLore);
          meta.lore(newLore);
        });
    return clone;
  }

  /**
   * Handles click events within the shop editor, providing different functionalities based on the
   * type of click (shift, right, or left).
   *
   * @return A consumer that handles how each item click within the shop is processed.
   */
  @Override
  protected BiConsumer<InventoryClickEvent, @NonNull Long> shopItemClickHandler() {
    return (event, price) -> {
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null) return;

      if (event.isShiftClick()) {
        confirmItemRemoval(clicked, true);
      } else if (event.isRightClick()) {
        confirmItemRemoval(clicked, false);
      } else if (event.isLeftClick()) {
        clicked.editMeta(
            meta -> {
              List<Component> lore = new ArrayList<>(Objects.requireNonNull(meta.lore()));
              if (lore.size() > editorLore.size()) {
                lore.subList(lore.size() - editorLore.size() - 1, lore.size()).clear();
              }
              meta.lore(lore);
            });
        openPriceEditor(clicked, false);
      }
    };
  }

  /* Removing */

  /**
   * Opens the interface to confirm the removal of an item from the shop.
   *
   * @param itemStack The item stack to be removed.
   * @param removeAll Whether to remove all instances of the item or just one.
   */
  private void confirmItemRemoval(@NonNull ItemStack itemStack, boolean removeAll) {
    this.popMenuHistory(this.owner.getUniqueId());
    String suffix = removeAll ? "all" : "single";
    new ConfirmMenu.Builder()
        .confirmItem(
            InvisibleMenuItem.get(
                this.gameTextDao.getResolvedTextMultiple(
                    "exchange.shop_editor.confirm_remove_" + suffix,
                    TagResolver.resolver("item", Tag.inserting(itemStack.displayName())))))
        .onConfirm(
            event -> {
              playClickSound();
              removeShopItem(itemStack, removeAll);
            })
        .onCancel(
            event -> {
              playClickSound();
              ShopEditorMenu.this.open();
            })
        .build()
        .open(this.owner);
  }

  /**
   * Handles the actual removal of an item from the shop, either by removing a single instance or
   * all instances.
   *
   * @param itemStack The item stack to remove.
   * @param removeAll Whether to remove all instances of the item or just one.
   */
  private void removeShopItem(@NonNull ItemStack itemStack, boolean removeAll) {
    final ShopItemData currentItemData = exchangeManager.getShopItemFromStack(itemStack);
    if (currentItemData == null) return;

    // remove stock entry
    if (removeAll) {
      playerShopManager
          .removeItemFromShop(currentItemData)
          .thenAccept(
              response -> {
                ShopItemData removedItem = response.updatedItem();
                int quantityToReturn = removedItem.quantity();
                this.exchangeManager.processItemRemoval(removedItem.id());
                menuFactory.createShopEditorMenu(this.owner, response.updatedShop());
                returnMultiItem(removedItem, quantityToReturn);
              });
      return;
    }

    // remove by unit
    playerShopManager
        .extractItemsFromShop(currentItemData, 1)
        .thenAccept(
            pair -> {
              ShopItemData removedItem = pair.getValue().updatedItem();
              this.exchangeManager.processItemUpdate(removedItem);
              menuFactory.createShopEditorMenu(this.owner, pair.getValue().updatedShop());
              if (pair.getKey() > 0) returnSingleItem(removedItem);
            });
  }

  /**
   * Handles returning ItemStacks for the amount of items being removed from the shop.
   *
   * @param shopItemData The data being removed.
   * @param amount multiple amount to remove
   */
  private void returnMultiItem(@NonNull ShopItemData shopItemData, int amount) {
    this.exchangeManager
        .giveRealItemMulti(this.owner, shopItemData, amount)
        .thenAccept(
            result ->
                this.owner.sendMessage(
                    this.gameTextDao.getResolvedText(
                        "exchange.shop_editor.return_items_multi",
                        TagResolver.resolver("amount", Tag.preProcessParsed("" + amount)),
                        TagResolver.resolver(
                            "amount_suffix", Tag.preProcessParsed(amount > 1 ? "'s" : "")))));
  }

  /**
   * Handles returning an ItemStack for a single unit being removed from the shop.
   *
   * @param shopItemData The data that the unit is remove from.
   */
  private void returnSingleItem(@NonNull ShopItemData shopItemData) {
    this.exchangeManager
        .giveRealItem(this.owner, shopItemData, 1)
        .thenAccept(
            result -> {
              String location = "";
              switch (result) {
                case INVALID_ITEM -> {
                  log.error("Tried to return an invalid item");
                  return; // purchase failed somewhere
                }
                case NO_SPACE_FOUND -> {
                  // todo undo transaction? Or check for space first..
                  log.error("No space was found anywhere!");
                  return;
                }
                case ADDED_TO_MAIL -> location = "Mail!";
                case ADDED_TO_LUGGAGE -> location = "Luggage!";
                case ADDED_TO_INVENTORY -> location = "Inventory!";
              }

              this.owner.sendMessage(
                  this.gameTextDao.getResolvedText(
                      "exchange.shop_editor.return_items",
                      TagResolver.resolver("amount", Tag.preProcessParsed("" + 1)),
                      TagResolver.resolver("amount_suffix", Tag.preProcessParsed("")),
                      TagResolver.resolver("location", Tag.preProcessParsed(location))));
            });
  }

  @Override
  protected List<Pair<Key, String>> getBackgroundComponents() {
    List<Pair<Key, String>> title = new ArrayList<>();
    title.add(Pair.of(null, "GE_player_store_editor"));
    title.addAll(getShopName());
    return title;
  }

  /* Option Section */

  /**
   * Sets up options for the shop editor menu, including selling, buying, and exit functionalities.
   */
  @Override
  protected void createOptionSection() {
    // add sell item
    setOptionSlot(8, 0, "shop_view.sell_item.enabled", event -> openPriceEditor(null, true));
    // add buy item
    setOptionSlot(7, 0, "shop_view.buy_item.disabled", event -> owner.sendMessage("Coming soon!"));
    // rename shop
    setOptionSlot(0, 0, "shop_editor.rename_shop", event -> openShopRenameUI());
    // extra options todo
    // setOptionSlot(1, 0, "shop_editor.options", event -> owner.sendMessage("Options coming
    // soon!"));
    // exit
    this.setExitButton(6, 0, event -> menuFactory.createGrandExchangeHub(this.owner).open());
  }

  /** Opens the shop renaming user interface. */
  private void openShopRenameUI() {
    this.popMenuHistory(this.owner.getUniqueId());
    playClickSound();

    this.coreMenuFactory
        .createEditMenu(
            "",
            input ->
                List.of(
                    ResponseAction.close(),
                    ResponseAction.run(
                        () ->
                            CompletableFuture.runAsync(
                                    () -> playerShopManager.renameShop(this.shopData, input))
                                .thenRunAsync(
                                    () -> ShopEditorMenu.this.open(owner),
                                    ThreadUtil.SYNC_EXECUTOR))),
            state -> List.of(ResponseAction.close(), ResponseAction.run(ShopEditorMenu.this::open)))
        .open(owner);
  }

  /**
   * Opens the price editor interface.
   *
   * @param stack The item stack to edit.
   * @param isNew Indicates whether the item is newly added or existing.
   */
  private void openPriceEditor(@Nullable ItemStack stack, boolean isNew) {
    playClickSound();
    this.popMenuHistory(this.owner.getUniqueId());
    menuFactory.createPriceEditorUI(this.owner, this.shopData).openPriceUI(stack, isNew);
  }
}
