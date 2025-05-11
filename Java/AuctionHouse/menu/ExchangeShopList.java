package AuctionHouse.menu;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.client.Clients;
import com.pixelhideaway.commons.base.data.ItemFilterCriteria;
import com.pixelhideaway.commons.base.data.PageResult;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.data.player.HideawayPlayer;
import com.pixelhideaway.core.menu.MenuTriggerSource;
import com.pixelhideaway.core.menu.player.HideawayProfile;
import com.pixelhideaway.core.menu.player.ProfileMenu;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.util.item.ItemBuilder;
import com.pixelhideaway.core.util.item.ItemUtil;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.utils.ExchangeCategory;
import com.pixelhideaway.grandexchange.utils.ShopItemCount;
import com.pixelhideaway.grandexchange.utils.ShopMenuUtils;
import com.playhideaway.menus.InvisibleMenuItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents a page-buffered menu for displaying lists of player shops within the Grand Exchange.
 * This menu allows players to navigate through available shops, providing interactive elements for
 * further actions such as editing or updating shop details.
 *
 * <p>extends PageBufferedMenu with type {@link PlayerShopData}, handling the pagination and
 * interactive display of player shops.
 *
 * @author Cammy
 */
@Slf4j
public final class ExchangeShopList extends PageBufferedMenu<PlayerShopData> {

  private final NamespacedKey shopIndexKey;
  private final NamespacedKey shopIconPlayerKey;

  @Inject
  public ExchangeShopList(
      @Assisted @NonNull Player owner,
      @NonNull HideawayServer core,
      @NonNull PlayerShopManager playerShopManager,
      @NonNull GrandExchangeManager grandExchangeManager,
      @NonNull ServerStatisticManager statisticManager) {
    super(owner, core, playerShopManager, grandExchangeManager, statisticManager);
    this.shopIndexKey = new NamespacedKey(core, "player_shop_list_index");
    this.shopIconPlayerKey = new NamespacedKey(core, "player_shop_icon_player");

    // todo show initial loading?
    // eg open immediately but have some form of "loading screen"
    // depends on how long it takes? Might just be fine

    open();
  }

  /**
   * Asynchronously loads shop data pages based on requested indices.
   *
   * @param pages A list of page indices to load.
   * @return A future that will complete with a list of page results containing player shop data.
   */
  @Override
  protected CompletableFuture<List<PageResult<PlayerShopData>>> loadPagesFromDatasource(
      List<Integer> pages) {
    return CompletableFuture.supplyAsync(
        () -> Clients.PLAYER_SHOP_CLIENT.getShopsByPageNumbers(this.rows, pages));
  }

  /**
   * Initializes the shop list menu, fetching and caching the first few pages and then opening the
   * menu.
   */
  @Override
  public void open() {
    log.warn("About to open Shop List Menu with filters: {}", this.currentFilter);
    // initialize with first 5 pages cached
    fetchAndCachePages(0, 4).thenRun(() -> super.open(this.owner));
  }

  @Override
  protected void populate(@NotNull Player player) {
    List<Pair<Key, String>> titleElements = new ArrayList<>();

    titleElements.add(Pair.of(null, "explore_public_light"));
    titleElements.add(Pair.of(null, "public_rooms_filters"));
    titleElements.add(Pair.of(null, "public_rooms_refresh"));
    titleElements.add(Pair.of(null, "public_rooms_search"));

    List<ShopDisplayData> currentDisplays = setupShopDisplays();
    currentDisplays.forEach(display -> titleElements.addAll(display.titleComponents));

    ShopListSection shopListSection =
        new ShopListSection(
            7,
            6,
            currentDisplays,
            // Icon Click Handler
            event -> {
              this.playClickSound();
              ItemStack clickedItem = event.getCurrentItem();
              if (clickedItem == null) return;
              ItemUtil.getOptionalPDC(clickedItem, shopIconPlayerKey, PersistentDataType.STRING)
                  .ifPresent(
                      idString -> {
                        @Nullable
                        ProfileMenu profileMenu =
                            HideawayProfile.createMenu(
                                this.owner, UUID.fromString(idString), MenuTriggerSource.EXCHANGE);
                        if (profileMenu != null) {
                          profileMenu.open(player);
                        }
                      });
            },
            // Bar Click Handler
            event -> {
              this.playClickSound();
              ItemStack clickedItem = event.getCurrentItem();
              if (clickedItem == null) return;

              ItemUtil.getOptionalPDC(clickedItem, shopIndexKey, PersistentDataType.INTEGER)
                  .ifPresentOrElse(
                      index -> {
                        PlayerShopData clickedShopData = getPageItem(this.page, index);
                        if (clickedShopData == null) {
                          logger.error(
                              "Player {} clicked on a null shop? Clicked slot {} on page {}",
                              player,
                              event.getSlot(),
                              this.page);
                          return;
                        }
                        resetPages();
                        menuFactory.createPlayerShopMenu(this.owner, clickedShopData);
                      },
                      () ->
                          logger.error(
                              "No Index PDC found on ShopListSection Item {}", clickedItem));
            });

    TitleBuilder builder =
        initPagedMenu(player, 0, 0, shopListSection, titleElements.toArray(new Pair[0]));

    createOptionButtons();

    this.setTitle(builder.build());
  }

  /**
   * Creates buttons for various options such as filtering, refreshing, and searching within the
   * shop list.
   */
  private void createOptionButtons() {
    // filter
    setOptionSlot(
        8,
        2,
        "exchange.shop_list.filter",
        event -> {
          applyFilters(ItemFilterCriteria.empty());
          this.update(this.owner);
          // todo switch selected tab?
        });
    // refresh
    setOptionSlot(
        8,
        3,
        "exchange.shop_list.refresh",
        event -> {
          this.resetPages();
          this.changePage(this.owner, 0);
          logger.warn("Refreshed page now have cache: {}", pageCache.keySet());
        });
    // search
    setOptionSlot(
        8, 4, "exchange.shop_list.search", event -> menuFactory.createSearchUI(this).openSearch());
    // exit
    this.setExitButton(
        8,
        0,
        event -> {
          this.popMenuHistory(this.owner.getUniqueId());
          menuFactory.createGrandExchangeHub(this.owner).open();
        });
  }

  /**
   * Sets up the display elements for each shop, organizing them into a list of {@link
   * ShopDisplayData}. This method creates visual and interactive components for each shop based on
   * its data.
   *
   * @return A list of display data for each shop, used to populate the shop list section.
   */
  private List<ShopDisplayData> setupShopDisplays() {
    List<ShopDisplayData> shopDisplays = new ArrayList<>();
    List<PlayerShopData> currentlyViewedShops = getPage(this.page);

    for (int i = 0; i < Math.min(this.rows, currentlyViewedShops.size()); i++) {
      PlayerShopData shopData = currentlyViewedShops.get(i);
      String shopName = ShopMenuUtils.formatShopName(shopData.shopName(), 18);
      List<Pair<Key, String>> titleComponents = ShopMenuUtils.createTitleComponents(shopName, i);

      /* Setup Shop Icon */
      ItemStack icon = getShopIcon(shopData);
      ItemUtil.setPDC(icon, shopIndexKey, i);

      /* Setup Resolvers & Lore */
      List<Component> barLore = getBarLore(shopData);
      shopDisplays.add(new ShopDisplayData(icon, titleComponents, barLore));
    }
    return shopDisplays;
  }

  /**
   * Generates an icon for the shop using the owner's player head. If the owner's player data is
   * unavailable, a default player head is used.
   *
   * @param shopData The data of the shop for which the icon is created.
   * @return An {@link ItemStack} representing the shop's icon.
   */
  private ItemStack getShopIcon(@NonNull PlayerShopData shopData) {
    // todo - currently only create shop icon from player skull
    ItemStack shopIcon;
    final HideawayPlayer hideawayPlayer = this.playerDao.getHideawayPlayer(shopData.shopOwner());
    if (hideawayPlayer == null) {
      shopIcon =
          new ItemBuilder(Material.PLAYER_HEAD)
              .addCustomModelData(5)
              .displayName(Component.text("Unknown Player"))
              .lore(Component.text(shopData.description()))
              .build();
    } else {
      shopIcon =
          hideawayPlayer
              .makePlayerHeadItem()
              .displayName(
                  this.gameTextDao.getResolvedText(
                      "exchange.shop_list.profile_message",
                      TagResolver.resolver(
                          "owner", Tag.preProcessParsed(hideawayPlayer.getUsername()))))
              .lore(Component.empty(), Component.text(shopData.description()))
              .withTag(
                  this.shopIconPlayerKey,
                  PersistentDataType.STRING,
                  hideawayPlayer.getUniqueId().toString())
              .build();
    }

    return shopIcon;
  }

  /**
   * Retrieves lore information for the shop based on the items it contains, categorized into
   * furniture, profiles, and cosmetics.
   *
   * @param shopData The data of the shop for which the lore is generated.
   * @return A list of {@link Component} objects containing formatted text for the shop's lore.
   */
  private List<Component> getBarLore(@NonNull PlayerShopData shopData) {
    ShopItemCount furnitureCounter =
        this.exchangeManager.countCategoryItems(ExchangeCategory.FURNITURE, shopData);
    ShopItemCount profileCounter =
        this.exchangeManager.countCategoryItems(ExchangeCategory.PROFILE, shopData);
    ShopItemCount cosmeticCounter =
        this.exchangeManager.countCategoryItems(ExchangeCategory.COSMETIC, shopData);

    List<Component> barLore =
        new ArrayList<>(
            this.gameTextDao.getResolvedTextMultiple(
                "exchange.shop_list.bar_message",
                TagResolver.resolver("owner", Tag.preProcessParsed(shopData.shopName()))));

    if (profileCounter.count() > 0) {
      barLore.add(
          this.gameTextDao.getResolvedText(
              "exchange.shop_list.profile_counter",
              TagResolver.resolver(
                  "profile_count", Tag.preProcessParsed("" + profileCounter.count())),
              TagResolver.resolver(
                  "profile_uni", Tag.preProcessParsed("" + profileCounter.unique()))));
    }
    if (cosmeticCounter.count() > 0) {
      barLore.add(
          this.gameTextDao.getResolvedText(
              "exchange.shop_list.cosmetic_counter",
              TagResolver.resolver(
                  "cosmetic_count", Tag.preProcessParsed("" + cosmeticCounter.count())),
              TagResolver.resolver(
                  "cosmetic_uni", Tag.preProcessParsed("" + cosmeticCounter.unique()))));
    }
    if (furnitureCounter.count() > 0) {
      barLore.add(
          this.gameTextDao.getResolvedText(
              "exchange.shop_list.furniture_counter",
              TagResolver.resolver(
                  "furniture_count", Tag.preProcessParsed("" + furnitureCounter.count())),
              TagResolver.resolver(
                  "furniture_uni", Tag.preProcessParsed("" + furnitureCounter.unique()))));
    }
    return barLore;
  }

  @Override
  protected TitleBuilder addPaginationButtons(Player player, TitleBuilder titleBuilder) {
    // page up
    this.setOptionSlot(
        8, 1, "item.public_rooms.previous_page", event -> this.changePage(player, -1));

    // page down
    this.setOptionSlot(8, 5, "item.public_rooms.next_page", event -> this.changePage(player, 1));

    return titleBuilder;
  }

  /**
   * Constructs the shop list section within the user interface, where each shop display is handled.
   * This section manages shop icons and their corresponding actions.
   */
  private final class ShopListSection extends ItemSection {

    private final Consumer<InventoryClickEvent> barClickHandler;
    private final List<List<Component>> lore;

    public ShopListSection(
        int width,
        int height,
        List<ShopDisplayData> displays,
        Consumer<InventoryClickEvent> iconClick,
        Consumer<InventoryClickEvent> barClick) {
      super(width, height, rows, displays.stream().map(ShopDisplayData::icon).toList(), iconClick);
      this.barClickHandler = barClick;
      this.lore = displays.stream().map(ShopDisplayData::barLore).toList();
    }

    @Override
    protected void populate(@NotNull Player player) {
      for (int i = 0; i < this.pageItems.length; i++) {
        // set icon
        this.setSlot(0, i, pageItems[i]);

        ItemStack barBackgroundItem = InvisibleMenuItem.get(lore.get(i));
        ItemUtil.setPDC(barBackgroundItem, shopIndexKey, i);
        for (int j = 1; j < 6; j++) {
          this.setSlot(j, i, barBackgroundItem, this.barClickHandler);
        }
      }
    }

    // no sorting is needed in this section
    @Override
    protected List<ItemStack> sortPageItems(Collection<ItemStack> items) {
      return new ArrayList<>(items);
    }
  }

  /**
   * Data structure for representing a shop display within the UI. This includes the shop icon,
   * title components, and additional lore that describes the shop.
   */
  private record ShopDisplayData(
      ItemStack icon, List<Pair<Key, String>> titleComponents, List<Component> barLore) {}
}
