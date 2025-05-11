package AuctionHouse.menu.shops.editor;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.grandexchange.ExchangeUpdateResponse;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.util.ThreadUtil;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.shops.ShopView;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A specialized view for managing matching items within a player's shop. This view is used when a
 * player attempts to add an item that matches existing items in the shop inventory. It allows the
 * player to merge or edit these items accordingly.
 *
 * @author Cammy
 */
@Slf4j
public final class MatchingItemsView extends ShopView {

  private final List<ShopItemData> matchingItems;
  private final ItemStack itemBeingAdded;

  @Inject
  public MatchingItemsView(
      @Assisted @NotNull Player owner,
      @Assisted @NotNull PlayerShopData shopData,
      @Assisted @NotNull ItemStack itemAdding,
      @Assisted @NotNull List<ShopItemData> matchingItems,
      @NotNull HideawayServer core,
      @NotNull PlayerShopManager playerShopManager,
      @NotNull GrandExchangeManager exchangeManager,
      @NotNull ServerStatisticManager statisticManager) {
    super(owner, shopData, core, playerShopManager, exchangeManager, statisticManager);
    this.matchingItems = matchingItems;
    this.itemBeingAdded = itemAdding.clone();
  }

  /* Don't add this menu to any history */
  @Override
  public void open(@NotNull Player player, boolean addHistory) {
    super.open(player, false);
  }

  /**
   * Initializes the items to be displayed in this view by transforming matching items into shop
   * items and preparing them for display in the paged menu.
   *
   * @return A future that provides a title builder once item initialization is complete.
   */
  @Override
  protected CompletableFuture<TitleBuilder> initStore() {
    return exchangeManager
        .createShopItems(this.matchingItems)
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
   * Alters the display of shop items by adding specific lore to indicate they are matching items.
   *
   * @param item The original item stack from the shop.
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
          newLore.addAll(gameTextDao.getResolvedTextMultiple("exchange.matching.item_lore"));
          meta.lore(newLore);
        });
    return clone;
  }

  /**
   * Handles click events on shop items, allowing items to be merged or managed directly from this
   * view.
   *
   * @return A bi-consumer that handles inventory click events and the price associated with the
   *     clicked item.
   */
  @Override
  protected BiConsumer<InventoryClickEvent, @NotNull Long> shopItemClickHandler() {
    return (event, price) -> {
      ShopItemData clickedItemData =
          exchangeManager.getShopItemFromStack(Objects.requireNonNull(event.getCurrentItem()));
      if (clickedItemData == null) return;

      clickedItemData = clickedItemData.withNewQuantity(clickedItemData.quantity() + 1);

      playerShopManager
          .upsertItemForShop(clickedItemData)
          .thenCompose(
              response -> {
                this.exchangeManager.processItemUpdate(response.updatedItem());
                return this.exchangeManager
                    .refreshItemStack(response.updatedItem())
                    .thenApply(item -> new Pair<>(item, response));
              })
          .thenApplyAsync(
              result -> {
                ItemStack createdItem = result.getKey();
                PlayerShopData updatedShop = result.getValue().updatedShop();

                if (event.isLeftClick()) {
                  menuFactory.createShopEditorMenu(this.owner, updatedShop);
                  return new Pair<>(ClickType.LEFT, result);
                } else if (event.isRightClick()) {
                  this.close(this.owner);
                  menuFactory
                      .createPriceEditorUI(this.owner, updatedShop)
                      .openPriceUI(createdItem, false);
                  return new Pair<>(ClickType.RIGHT, result);
                }
                return null;
              },
              ThreadUtil.SYNC_EXECUTOR)
          .thenAccept(
              clickResult -> {
                ExchangeUpdateResponse updateResponse = clickResult.getValue().getValue();
                ShopItemData updatedShopItem = updateResponse.updatedItem();
                boolean isLeft = clickResult.getKey().equals(ClickType.LEFT);
                // Left Click = true
                String id =
                    (isLeft) ? "exchange.update_price_message" : "exchange.update_quantity_message";
                String suffix = updatedShopItem.quantity() == 1 ? "" : "'s";

                TagResolver.Builder resolver = TagResolver.builder();
                resolver.tag("quantity", Tag.preProcessParsed(updatedShopItem.quantity() + ""));
                resolver.tag("item", Tag.inserting(clickResult.getValue().getKey().displayName()));
                resolver.tag("suffix", Tag.preProcessParsed(suffix));
                if (isLeft) resolver.tag("price", Tag.preProcessParsed(price + ""));

                owner.sendMessage(this.gameTextDao.getResolvedText(id, resolver.build()));
              })
          .exceptionally(
              ex -> {
                log.error("We have an error adding new quantity! ", ex);
                return null;
              });
    };
  }

  @Override
  protected List<Pair<Key, String>> getBackgroundComponents() {
    List<Pair<Key, String>> title = new ArrayList<>();
    title.add(Pair.of(null, "GE_player_matching_page"));
    title.addAll(getShopName());
    return title;
  }

  @Override
  protected void createOptionSection() {
    // todo maybe create surrounding hover?
    this.setExitButton(
        8,
        0,
        event ->
            menuFactory
                .createPriceEditorUI(this.owner, this.shopData)
                .openPriceUI(this.itemBeingAdded.clone(), true));
  }
}
