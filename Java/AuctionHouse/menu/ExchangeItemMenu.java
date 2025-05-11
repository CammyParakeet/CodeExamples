package AuctionHouse.menu;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.configuration.data.text.UnicodeData;
import com.pixelhideaway.commons.base.data.ItemFilterCriteria;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.data.item.ItemRarity;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.text.TextUtils;
import com.pixelhideaway.core.util.ThreadUtil;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.playhideaway.menus.InvisibleMenuItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents a user interface in the Grand Exchange that displays a list of items for purchase or
 * trade, allowing interactions such as filtering, sorting, and purchasing. It extends the
 * functionality provided by {@link ExchangeView}.
 *
 * @author Cammy
 */
@Slf4j
public final class ExchangeItemMenu extends ExchangeView {

  @Inject
  public ExchangeItemMenu(
      @Assisted final Player owner,
      @NonNull final HideawayServer core,
      @NonNull final PlayerShopManager playerShopManager,
      @NonNull final GrandExchangeManager grandExchangeManager,
      @NonNull final ServerStatisticManager statisticManager) {
    super(owner, core, playerShopManager, grandExchangeManager, statisticManager);
    open();
  }

  @Override
  public void open(@NotNull Player player, boolean addHistory) {
    this.itemSection = null;
    super.open(player, true);
  }

  /**
   * Initializes the menu by loading items based on the current filter and sort options, then
   * displaying them on the menu interface.
   */
  private void initialize() {
    exchangeManager
        .getFilteredShopItems(this.currentSortOption, this.currentFilter)
        .thenComposeAsync(this::getItemsForPage)
        .thenAcceptAsync(
            items -> {
              this.itemSection = new ItemSection(9, 4, parseItemMenuItems(items), clickHandler());
            })
        .thenRun(
            () -> {
              if (this.owner.getOpenInventory().getTopInventory().equals(this.inventory)) {
                this.update(this.owner);
              } else this.open();
            });
  }

  /**
   * Parses the list of items for display in the menu, adding specific lore to each based on the
   * item's shop data.
   *
   * @param itemsIn The list of {@link ItemStack} objects to be processed for display.
   * @return A list of {@link ItemStack} objects with updated lore for menu display.
   */
  private List<ItemStack> parseItemMenuItems(List<ItemStack> itemsIn) {
    return itemsIn.stream()
        .map(
            item -> {
              ItemStack clone = item.clone();
              ShopItemData shopItem = exchangeManager.getShopItemFromStack(clone);
              if (shopItem == null) return null;
              clone.editMeta(
                  meta -> {
                    List<Component> newLore = meta.lore();
                    if (newLore == null) return;
                    newLore.add(Component.empty());
                    newLore.addAll(
                        gameTextDao.getResolvedTextMultiple(
                            "exchange.item_menu.item_lore",
                            TagResolver.resolver(
                                "shop_name",
                                Tag.preProcessParsed(
                                    playerShopManager.getShopName(shopItem.shopId())))));
                    meta.lore(newLore);
                  });
              return clone;
            })
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Processes a collection of {@link ShopItemData} to create a list of {@link ItemStack} that can
   * be displayed in the menu.
   *
   * @param shopItems The collection of shop items to process.
   * @return A future that completes with a list of item stacks representing the shop items.
   */
  public CompletableFuture<List<ItemStack>> getItemsForPage(Collection<ShopItemData> shopItems) {
    List<CompletableFuture<ItemStack>> itemFutures =
        shopItems.stream().map(exchangeManager::getOrCreateItemStack).toList();

    return CompletableFuture.allOf(itemFutures.toArray(new CompletableFuture[0]))
        .thenApplyAsync(v -> itemFutures.stream().map(CompletableFuture::join).toList());
  }

  @Override
  protected void populate(@NonNull Player player) {
    List<String> unicodeIds = new ArrayList<>();
    // add base unicode ids
    unicodeIds.add("GE_search_page");

    // Load items or set section
    if (this.itemSection != null) {
      this.setSlot(0, 1, this.itemSection);
    } else {
      initialize();
    }

    createHeaderButtons();
    this.setTitle(initPagedMenu(this.owner, unicodeIds.toArray(new String[0])).build());
  }

  /**
   * Defines the actions to take when an item in the menu is clicked, such as purchasing or editing
   * an item.
   *
   * @return A consumer that handles inventory click events within the menu.
   */
  private Consumer<InventoryClickEvent> clickHandler() {
    return event -> {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      ItemStack clickedItem = event.getCurrentItem();
      if (clickedItem == null) throw new IllegalStateException("Null Menu Items Should Not Exist!");

      if (event.isLeftClick()) {
        menuFactory.createItemPurchaseUI(player, clickedItem);
        return;
      }

      ShopItemData shopItem = exchangeManager.getShopItemFromStack(clickedItem);
      if (shopItem == null) {
        log.error("Did not get shop item from {}", clickedItem);
        return;
      }

      playerShopManager
          .getOrLoadPlayerShop(shopItem.shopId())
          .thenAcceptAsync(
              shop -> {
                pushMenuHistory(this.owner.getUniqueId());
                menuFactory.createPlayerShopMenu(this.owner, shop);
              },
              ThreadUtil.SYNC_EXECUTOR);
    };
  }

  /* Navigation/Options */

  /** Creates interactive header buttons for the menu such as sorting, filtering, and search. */
  private void createHeaderButtons() {
    // all | home
    setOptionSlot(
        0,
        0,
        "exchange.item_menu.all",
        event -> {
          applyFilters(ItemFilterCriteria.empty().withSearch(this.currentFilter.searchKeyword()));
          // todo switch selected tab?
        });

    // wearables
    setOptionSlot(
        1,
        0,
        "exchange.item_menu.cosmetic",
        event -> {
          applyFilters(this.currentFilter.withCategory("cosmetic"));
        });

    // furniture
    setOptionSlot(
        2,
        0,
        "exchange.item_menu.furniture",
        event -> {
          applyFilters(this.currentFilter.withCategory("furniture"));
        });

    // profile
    setOptionSlot(
        3,
        0,
        "exchange.item_menu.profile",
        event -> {
          applyFilters(this.currentFilter.withCategory("profile"));
        });

    // misc
    setOptionSlot(
        4,
        0,
        "exchange.item_menu.sort",
        event -> {
          // todo sort menu
        });

    // search
    String searchLore =
        this.currentFilter.searchKeyword() == null
            ? ""
            : "Last Search: " + this.currentFilter.searchKeyword();
    this.setSlot(
        5,
        0,
        InvisibleMenuItem.get(
            this.gameTextDao.getResolvedTextMultiple(
                "exchange.item_menu.search",
                TagResolver.resolver("previous_search", Tag.preProcessParsed(searchLore)))),
        event -> menuFactory.createSearchUI(this).openSearch());

    // filter
    List<String> currentRarities = this.currentFilter.rarities();
    ItemStack rarityFilterItem =
        InvisibleMenuItem.get(
            this.gameTextDao.getResolvedTextMultiple("exchange.item_menu.filter"));
    rarityFilterItem.editMeta(
        meta -> {
          List<Component> lore = meta.lore();
          if (lore == null) lore = new ArrayList<>();
          lore.add(
              TextUtils.cleanLoreComponent(
                  Component.text("Applied Filters:"), TextColor.fromHexString("#91d9ff")));

          for (String rarity : currentRarities) {
            final UnicodeData rarityUnicode =
                this.unicodeDao
                    .get(ItemRarity.valueOf(rarity.toUpperCase()).tagName())
                    .orElse(null);
            if (rarityUnicode == null) continue;
            lore.add(TextUtils.cleanLoreComponent(Component.text(rarityUnicode.getUnicode())));
          }
          meta.lore(lore);
        });
    this.setSlot(
        6,
        0,
        rarityFilterItem,
        event -> {
          openRarityFilter();
        });

    // reset
    setOptionSlot(
        7,
        0,
        "exchange.item_menu.reset",
        event -> {
          clearSearchRarityFilters();
        });

    // exit
    this.setExitButton(
        8,
        0,
        event -> {
          this.popMenuHistory(this.owner.getUniqueId());
          menuFactory.createGrandExchangeHub(this.owner).open();
        });
  }

  /* Filtering & Search */

  /**
   * Resets the pagination and refreshes the item listing when filters or sorting options change.
   */
  @Override
  protected void resetPages() {
    super.resetPages();
    initialize();
  }

  /** Clears the current search and rarity filters, applying only the category filter. */
  private void clearSearchRarityFilters() {
    ItemFilterCriteria criteria =
        ItemFilterCriteria.builder()
            .categoryType(this.currentFilter.categoryType())
            .rarities(Collections.emptyList())
            .build();
    this.applyFilters(criteria);
  }

  /** Clears the current search and rarity filters, applying only the category filter. */
  public void openRarityFilter() {
    menuFactory.createRarityFilterUI(this).open();
  }
}
