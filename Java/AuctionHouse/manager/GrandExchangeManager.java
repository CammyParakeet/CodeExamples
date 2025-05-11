package AuctionHouse.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pixelhideaway.commons.base.client.Clients;
import com.pixelhideaway.commons.base.data.ItemFilterCriteria;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.commons.base.nats.NatsEndpoint;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.commons.server.injection.Manager;
import com.pixelhideaway.commons.server.text.UnicodeDao;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.data.gametext.GameTextDao;
import com.pixelhideaway.core.data.item.ItemDao;
import com.pixelhideaway.core.data.item.ItemData;
import com.pixelhideaway.core.data.item.ItemRarity;
import com.pixelhideaway.core.data.item.origin.ItemOriginData;
import com.pixelhideaway.core.data.item.origin.OriginType;
import com.pixelhideaway.core.injection.MenuFactory;
import com.pixelhideaway.core.util.item.ItemUtil;
import com.pixelhideaway.core.util.item.give.ItemReturnResult;
import com.pixelhideaway.core.util.item.give.ItemReturnSettings;
import com.pixelhideaway.grandexchange.GrandExchange;
import com.pixelhideaway.grandexchange.injection.ExchangeMenuFactory;
import com.pixelhideaway.grandexchange.utils.ExchangeCategory;
import com.pixelhideaway.grandexchange.utils.ShopItemCount;
import com.pixelhideaway.grandexchange.utils.ShopSorted.ShopSort;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.pixelhideaway.core.util.item.ItemUtil.getOptionalPDC;

/**
 * Manages the Grand Exchange shop items, caching, and search functionalities. This manager is
 * responsible for handling item updates and removals from the shop, caching item data, and
 * providing search and filter capabilities.
 *
 * @author Cammy
 */
@Slf4j
@Singleton
@AutoService({Manager.class, Listener.class})
public final class GrandExchangeManager implements Manager, Listener {

  /** Map from {@link ShopItemData#id() Shop Item Id} to its data */
  private final Map<Long, ShopItemData> shopItemCache = new ConcurrentHashMap<>();

  /* Filtered caches */
  private final Map<ItemRarity, Set<Long>> rarityFilteredIds = new ConcurrentHashMap<>();
  private final Map<ExchangeCategory, Set<Long>> categoryFilteredIds = new ConcurrentHashMap<>();

  /* Item Data - Shop Item Link */
  private final Map<String, Set<Long>> itemDataLink = new ConcurrentHashMap<>();

  /* ItemStack Cache Pool */
  private final Cache<Long, ItemStack> itemStackCache;

  /* Item Data Keys */
  private final NamespacedKey consumeKey;
  private final NamespacedKey signedIdKey;
  private final NamespacedKey signedTimeKey;
  private final NamespacedKey customTextKey;
  private final NamespacedKey randomKey;

  /* Core components and managers */
  @Getter private final GrandExchange exchangePlugin;
  @Getter private final ExchangeSearchManager searchManager;
  @Getter private final PlayerShopManager shopManager;
  @Getter private final ExchangeMenuFactory menuFactory;
  @Getter private final MenuFactory coreMenuFactory;
  @Getter private final ItemDao itemDao;
  @Getter private final UnicodeDao unicodeDao;
  @Getter private final GameTextDao gameTextDao;
  private final HideawayServer core;

  @Inject
  public GrandExchangeManager(
      @NotNull final GrandExchange exchangePlugin,
      @NotNull final HideawayServer core,
      @NotNull final ExchangeSearchManager searchManager,
      @NotNull final ExchangeMenuFactory menuFactory,
      @NotNull final MenuFactory coreMenuFactory) {
    this.exchangePlugin = exchangePlugin;
    this.core = core;
    this.itemDao = core.getItemDao();
    this.unicodeDao = core.getUnicodeDao();
    this.gameTextDao = core.getGameTextDao();
    this.menuFactory = menuFactory;
    this.coreMenuFactory = coreMenuFactory;
    this.searchManager = searchManager;
    this.shopManager = exchangePlugin.getPlayerShopManager();
    this.consumeKey = new NamespacedKey(core, "consume_chance_uses");
    this.signedIdKey = new NamespacedKey(core, "signed_uuid");
    this.signedTimeKey = new NamespacedKey(core, "signed_timestamp");
    this.customTextKey = new NamespacedKey(core, "custom_text");
    this.randomKey = new NamespacedKey(core, "random");
    this.itemStackCache =
        Caffeine.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).softValues().build();
  }

  @Override
  public void onEnable() {
    loadShopItems();
    NatsEndpoint.EXCHANGE_ITEM_UPDATE.listen(
        event -> event.updatedItems().forEach(this::processItemUpdate));
    NatsEndpoint.EXCHANGE_ITEM_REMOVAL.listen(
        event -> event.removedItemIds().forEach(this::processItemRemoval));
  }

  /** Loads all shop items from the backend asynchronously. */
  public void loadShopItems() {
    CompletableFuture.supplyAsync(Clients.EXCHANGE_CLIENT::getAllShopItems)
        .thenAcceptAsync(shopItems -> shopItems.forEach(this::processItemUpdate));
  }

  /* Local Caching Management */

  /**
   * Processes an item update, refreshing the item data and cache entries.
   *
   * @param shopItemData The shop item data that needs to be updated.
   */
  public void processItemUpdate(ShopItemData shopItemData) {
    this.shopItemCache.put(shopItemData.id(), shopItemData);
    this.itemStackCache.invalidate(shopItemData.id());

    Optional<ItemData> itemData = this.itemDao.get(shopItemData.itemId());
    itemData.ifPresentOrElse(
        data -> {
          updateItemDataView(shopItemData, data);
          updateRarityFilterView(shopItemData, data);
          updateCategoryFilterView(shopItemData, data);
        },
        () -> {
          throw new IllegalStateException(
              "Invalid or unloaded item data "
                  + shopItemData.itemId()
                  + " for shop item: "
                  + shopItemData.id());
        });
  }

  /**
   * Processes the removal of a shop item, clearing related cache entries.
   *
   * @param itemId The ID of the shop item to be removed.
   */
  public void processItemRemoval(Long itemId) {
    this.itemStackCache.invalidate(itemId);

    ShopItemData removedItem = this.shopItemCache.remove(itemId);
    if (removedItem == null) return;

    Optional<ItemData> itemData = this.itemDao.get(removedItem.itemId());
    itemData.ifPresentOrElse(
        data -> {
          ItemRarity rarity = data.getRarity();
          this.rarityFilteredIds.getOrDefault(rarity, Collections.emptySet()).remove(itemId);

          ExchangeCategory category = deriveCategory(data);
          this.categoryFilteredIds.getOrDefault(category, Collections.emptySet()).remove(itemId);
        },
        () -> {
          throw new IllegalStateException(
              "Invalid or unloaded item data "
                  + removedItem.itemId()
                  + " for shop item: "
                  + removedItem.id());
        });
  }

  /**
   * Updates the mappings between item data and shop item IDs. Ensures that the shop item is linked
   * to the correct item data.
   *
   * @param shopItem The shop item data to be linked.
   * @param itemData The item data associated with the shop item.
   */
  private void updateItemDataView(ShopItemData shopItem, ItemData itemData) {
    if (!shopItem.itemId().equals(itemData.getId())) {
      throw new IllegalArgumentException(
          "Item Ids do not match - Shop: " + shopItem.itemId() + " ItemID: " + itemData.getId());
    }
    this.itemDataLink
        .computeIfAbsent(itemData.getId(), k -> ConcurrentHashMap.newKeySet())
        .add(shopItem.id());
  }

  /**
   * Updates the rarity filter view by adding the shop item ID to the corresponding rarity set.
   *
   * @param shopItem The shop item data to update in the rarity view.
   * @param itemData The item data containing the rarity information.
   */
  private void updateRarityFilterView(ShopItemData shopItem, ItemData itemData) {
    ItemRarity rarity = deriveValidRarity(itemData);

    this.rarityFilteredIds
        .computeIfAbsent(rarity, k -> ConcurrentHashMap.newKeySet())
        .add(shopItem.id());
  }

  /**
   * Updates the category filter view by adding the shop item ID to the corresponding category set.
   *
   * @param shopItem The shop item data to update in the category view.
   * @param itemData The item data containing the type information used to determine the category.
   */
  private void updateCategoryFilterView(ShopItemData shopItem, ItemData itemData) {
    ExchangeCategory category = deriveCategory(itemData);
    if (category == ExchangeCategory.ALL) return;
    this.categoryFilteredIds
        .computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet())
        .add(shopItem.id());
  }

  /**
   * Determines a valid item rarity.
   *
   * @param data The item data from which to derive the rarity.
   * @return The determined ItemRarity.
   */
  public ItemRarity deriveValidRarity(@NonNull ItemData data) {
    return switch (data.getRarity()) {
      case LIMITED_EDITION, BACKEND, SECRET_RARE -> ItemRarity.MYTHICAL;
      default -> data.getRarity();
    };
  }

  /**
   * Determines the category of an item based on its type.
   *
   * @param data The item data from which to derive the category.
   * @return The determined ExchangeCategory.
   */
  public ExchangeCategory deriveCategory(@NonNull ItemData data) {
    return switch (data.getType()) {
      case HAT, BACK -> ExchangeCategory.COSMETIC;
      case BADGE, PROFILE_PICTURE, PROFILE_SHELL -> ExchangeCategory.PROFILE;
      case FURNITURE -> ExchangeCategory.FURNITURE;
      case BLOCK -> ExchangeCategory.BLOCK;
      default -> ExchangeCategory.ALL;
    };
  }

  /* Cache retrieval */

  /**
   * Retrieves a shop item from the cache based on its ID.
   *
   * @param id The ID of the shop item to retrieve.
   * @return An Optional containing the shop item data if found, or an empty Optional if not found.
   */
  public Optional<ShopItemData> getShopItem(Long id) {
    return Optional.ofNullable(this.shopItemCache.get(id));
  }

  /**
   * Retrieves a list of shop items based on a set of item IDs.
   *
   * @param itemIds The set of item IDs for which to retrieve shop items.
   * @return A list of ShopItemData objects corresponding to the provided item IDs.
   */
  public List<ShopItemData> getShopItems(Set<Long> itemIds) {
    return itemIds.stream().map(shopItemCache::get).filter(Objects::nonNull).toList();
  }

  /**
   * Retrieves a list of shop items filtered by category from a specific player shop's inventory.
   *
   * @param category The category by which to filter shop items.
   * @param data The player shop data containing item IDs.
   * @return A list of shop items in the specified category.
   */
  public List<ShopItemData> getShopItemsByCategory(
      @NonNull ExchangeCategory category, @NonNull PlayerShopData data) {
    return data.itemIds().stream()
        .filter(
            id -> categoryFilteredIds.getOrDefault(category, Collections.emptySet()).contains(id))
        .map(shopItemCache::get)
        .toList();
  }

  /**
   * Counts the number of items and unique items in a category within a specific shop.
   *
   * @param category The category to count items within.
   * @param shopData The shop data containing item IDs.
   * @return An object containing the count of items and unique items in the specified category.
   */
  public ShopItemCount countCategoryItems(
      @NonNull ExchangeCategory category, @NonNull PlayerShopData shopData) {
    int itemCount = 0, unique;
    List<ShopItemData> shopItemsByCategory = getShopItemsByCategory(category, shopData);
    unique = shopItemsByCategory.size();
    for (ShopItemData item : shopItemsByCategory) {
      itemCount += item.quantity();
    }

    return new ShopItemCount(itemCount, unique);
  }

  /**
   * Retrieves a set of shop items that match specified filtering criteria and sorting preferences.
   * This method applies both text-based searches and non-text filters like rarity and category, and
   * then sorts the results according to the provided sorting option.
   *
   * @param sortOption The sorting option to order the final results.
   * @param criteria The filtering criteria that include search keywords, rarity, and category type.
   * @return A CompletableFuture that, when completed, provides a sorted set of ShopItemData
   *     matching the specified criteria.
   */
  public CompletableFuture<Set<ShopItemData>> getFilteredShopItems(
      ShopSort sortOption, ItemFilterCriteria criteria) {
    CompletableFuture<Stream<Long>> filteredIdsFuture;

    // If there's a search keyword, perform a search to filter IDs, then apply non-text filters.
    if (criteria.searchKeyword() != null && !criteria.searchKeyword().isBlank()) {
      filteredIdsFuture =
          searchManager
              .searchItemIds(criteria.searchKeyword(), 200)
              .thenApply(
                  searchResults -> {
                    Set<Long> resultIds =
                        searchResults.stream()
                            .map(
                                itemDataLink
                                    ::get) // Retrieve sets of IDs linked to the searched item IDs.
                            .filter(Objects::nonNull)
                            .flatMap(
                                Collection::stream) // Flatten the sets into a single stream of IDs.
                            .collect(Collectors.toSet());

                    // Further filter the result IDs by non-text filters and return a stream of IDs.
                    return shopItemCache.keySet().stream()
                        .filter(resultIds::contains)
                        .filter(id -> applyNonTextFilters(id, criteria));
                  });
    } else {
      // If there's no search keyword, apply non-text filters directly to all item IDs.
      filteredIdsFuture =
          CompletableFuture.completedFuture(
              shopItemCache.keySet().stream().filter(id -> applyNonTextFilters(id, criteria)));
    }
    // Finalize results by sorting the filtered IDs and converting them to a set of ShopItemData.
    return filteredIdsFuture.thenApply(stream -> finalizeResults(stream, sortOption));
  }

  /**
   * Applies non-text filters based on criteria to determine if an item ID matches the specified
   * conditions. This method checks if the item matches the specified rarity and category type.
   *
   * @param id The ID of the item to check.
   * @param criteria The filtering criteria containing rarity and category type.
   * @return true if the item matches the criteria; otherwise, false.
   */
  private boolean applyNonTextFilters(Long id, ItemFilterCriteria criteria) {
    boolean rarityMatch =
        Optional.of(criteria.rarities())
            .filter(r -> !r.isEmpty())
            .map(
                rarities ->
                    rarities.stream()
                        .map(String::toUpperCase)
                        .map(
                            rarityString -> {
                              try {
                                return ItemRarity.valueOf(rarityString);
                              } catch (IllegalArgumentException e) {
                                log.error(
                                    "Attempted a rarity check against an invalid Item Rarity: {}",
                                    rarityString);
                                return null;
                              }
                            })
                        .filter(Objects::nonNull)
                        .anyMatch(
                            rarity ->
                                rarityFilteredIds
                                    .getOrDefault(rarity, Collections.emptySet())
                                    .contains(id)))
            .orElse(true);

    boolean typeMatch =
        Optional.ofNullable(criteria.categoryType())
            .filter(t -> !t.isEmpty())
            .map(String::toUpperCase)
            .map(ExchangeCategory::valueOf)
            .map(
                category ->
                    categoryFilteredIds.getOrDefault(category, Collections.emptySet()).contains(id))
            .orElse(true);

    return rarityMatch && typeMatch;
  }

  /**
   * Finalizes the results for filtered shop items based on a provided sorting option. This method
   * collects the filtered IDs into shop item data and sorts them accordingly.
   *
   * @param filteredIds A stream of item IDs that passed the filtering process.
   * @param sortOption The sorting option to apply to the final results.
   * @return A sorted set of ShopItemData based on the specified sort option.
   */
  private Set<ShopItemData> finalizeResults(Stream<Long> filteredIds, ShopSort sortOption) {
    return filteredIds
        .map(shopItemCache::get)
        .sorted(getItemComparator(sortOption))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Retrieves the comparator for sorting shop items based on a specified sort option.
   *
   * @param sortOption The sorting option to apply.
   * @return A comparator used for sorting shop item data.
   */
  private Comparator<ShopItemData> getItemComparator(ShopSort sortOption) {
    return switch (sortOption) {
      case PRICE_ASCENDING -> Comparator.comparingLong(ShopItemData::price);
      case PRICE_DESCENDING -> Comparator.comparingLong(ShopItemData::price).reversed();
      case CREATION_ASCENDING -> Comparator.comparingLong(ShopItemData::creationDate);
      case CREATION_DESCENDING -> Comparator.comparingLong(ShopItemData::creationDate).reversed();
      default -> throw new IllegalArgumentException("Unhandled/Unexpected value: " + sortOption);
    };
  }

  /* Data <-> ItemStack Management */

  /**
   * Retrieves or creates an ItemStack representation of a shop item. This method uses a cache to
   * avoid recreating ItemStacks that have not changed.
   *
   * @param shopItemData The shop item data for which to retrieve or create an ItemStack.
   * @return A CompletableFuture that, when completed, provides the ItemStack associated with the
   *     shop item data.
   */
  public CompletableFuture<ItemStack> getOrCreateItemStack(@NonNull ShopItemData shopItemData) {
    ItemStack cachedItem = itemStackCache.getIfPresent(shopItemData.id());

    // If we haven't cached this item recently
    if (cachedItem == null) {
      // Create new and cache
      return getStackFromShopItem(shopItemData)
          .thenApply(
              itemStack -> {
                itemStackCache.put(shopItemData.id(), itemStack);
                return itemStack;
              });
    }
    // We have this itemStack already cached ready for use.
    return CompletableFuture.completedFuture(cachedItem);
  }

  public CompletableFuture<ItemStack> refreshItemStack(@NonNull ShopItemData shopItemData) {
    this.itemStackCache.invalidate(shopItemData.id());
    return getOrCreateItemStack(shopItemData);
  }

  /**
   * Creates a list of ItemStacks for a collection of shop item IDs. This method retrieves the shop
   * items from the cache and then generates ItemStacks for them.
   *
   * @param shopItemIds A set of shop item IDs for which to create ItemStacks.
   * @return A CompletableFuture that, when completed, provides a list of ItemStacks.
   */
  public CompletableFuture<List<ItemStack>> createShopItems(Set<Long> shopItemIds) {
    List<ShopItemData> shopItems =
        shopItemIds.stream().map(shopItemCache::get).filter(Objects::nonNull).toList();
    return createShopItems(shopItems);
  }

  /**
   * Creates a list of ItemStacks for a list of shop items. This method generates an ItemStack for
   * each shop item and collects the results.
   *
   * @param shopItems A list of ShopItemData objects for which to create ItemStacks.
   * @return A CompletableFuture that, when completed, provides a list of ItemStacks.
   */
  public CompletableFuture<List<ItemStack>> createShopItems(List<ShopItemData> shopItems) {
    List<CompletableFuture<ItemStack>> itemFutures =
        shopItems.stream().map(this::getOrCreateItemStack).toList();

    return CompletableFuture.allOf(itemFutures.toArray(CompletableFuture[]::new))
        .thenApply(v -> itemFutures.stream().map(CompletableFuture::join).toList());
  }

  /**
   * Generates an ItemStack from a shop item's data. This method applies various transformations and
   * metadata to the ItemStack based on the shop item's properties.
   *
   * <p>TODO SIGNED IDs??
   *
   * @param shopItemData The shop item data for which to generate an ItemStack.
   * @return A CompletableFuture that, when completed, provides the ItemStack with applied
   *     properties.
   */
  public CompletableFuture<ItemStack> getStackFromShopItem(ShopItemData shopItemData) {
    ItemData itemData =
        itemDao.get(shopItemData.itemId()).orElseThrow(IllegalArgumentException::new);

    return core.getItemOriginsDao()
        .applyItemOrigin(
            itemDao.create(itemData),
            new ItemOriginData(
                -1,
                shopItemData.signedTimestamp() > 0 ? shopItemData.signedId() : null,
                OriginType.SHOP,
                Instant.ofEpochMilli(shopItemData.signedTimestamp())))
        .thenApply(
            stack -> {
              stack.editMeta(
                  meta -> {
                    String suffixColor = "<#ccebff>";
                    String unavailableColor = "<#fc8a88>";

                    List<Component> currentLore = meta.lore();
                    if (currentLore == null || currentLore.isEmpty()) return;
                    List<Component> newLore = new ArrayList<>(currentLore);
                    newLore.add(Component.empty());

                    newLore.addAll(
                        this.gameTextDao.getResolvedTextMultiple(
                            "exchange.item_info",
                            TagResolver.builder()
                                .tag(
                                    "color",
                                    Tag.preProcessParsed(
                                        shopItemData.quantity() > 0
                                            ? suffixColor
                                            : unavailableColor))
                                .tag("stock", Tag.preProcessParsed("" + shopItemData.quantity()))
                                .tag(
                                    "price",
                                    Tag.preProcessParsed(formatPrice(shopItemData.price())))
                                .build()));

                    meta.lore(newLore);
                  });

              // Set Shop Info
              ItemUtil.setPDC(stack, exchangePlugin.getShopItemKey(), shopItemData.id());
              ItemUtil.setPDC(stack, exchangePlugin.getShopItemShopKey(), shopItemData.shopId());
              ItemUtil.setPDC(stack, exchangePlugin.getShopItemPrice(), shopItemData.price());
              ItemUtil.setPDC(stack, exchangePlugin.getShopItemQuantity(), shopItemData.quantity());

              // todo next checks and applying?
              return stack;
            });
  }

  public CompletableFuture<ItemReturnResult> giveRealItem(
      @NonNull Player player, @NonNull ShopItemData shopItemData, int amount) {
    return giveRealItem(player.getUniqueId(), shopItemData, amount);
  }

  public CompletableFuture<ItemReturnResult> giveRealItem(
      @NonNull Player player, @NonNull ShopItemData shopItemData) {
    return giveRealItem(player.getUniqueId(), shopItemData, 1);
  }

  public CompletableFuture<ItemReturnResult> giveRealItem(
      @NonNull OfflinePlayer player, @NonNull ShopItemData shopItemData) {
    return giveRealItem(player.getUniqueId(), shopItemData, 1);
  }

  public CompletableFuture<ItemReturnResult> giveRealItem(
      @NonNull UUID playerId, @NonNull ShopItemData shopItemData, int amount) {
    ItemStack realItem = itemDao.create(shopItemData.itemId());
    if (realItem == null) {
      throw new IllegalStateException(
          "Could not create configured item from shop item data " + shopItemData.itemId());
    }

    return ItemUtil.giveItem(playerId, realItem, new ItemReturnSettings());
  }

  public CompletableFuture<List<ItemReturnResult>> giveRealItemMulti(
      @NonNull Player player, @NonNull ShopItemData shopItemData, int amount) {
    log.warn("Will be giving {} items to you", amount);
    List<CompletableFuture<ItemReturnResult>> tasks =
        IntStream.range(0, amount)
            .mapToObj(
                i -> {
                  log.warn("Starting item creation for stream index {}", i);
                  return CompletableFuture.supplyAsync(() -> itemDao.create(shopItemData.itemId()))
                      .thenApply(
                          item -> {
                            log.warn("Created item for stream index {}", i);
                            if (item == null)
                              throw new IllegalStateException(
                                  "Could not create item from " + shopItemData.itemId());
                            return item;
                          })
                      .thenCompose(
                          realItem ->
                              ItemUtil.giveItem(
                                  player.getUniqueId(), realItem, new ItemReturnSettings()))
                      .exceptionally(
                          ex -> {
                            log.error("Failed for stream index {}", i, ex);
                            return null;
                          });
                })
            .toList();

    return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
        .thenApply(
            v ->
                tasks.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
  }

  /**
   * Retrieves a shop item from an ItemStack by extracting the persistent data container (PDC)
   * values. This method uses the ItemStack's PDC to reconstruct the corresponding ShopItemData.
   *
   * @param stack The ItemStack from which to extract the shop item data.
   * @return A ShopItemData object constructed from the extracted data, or null if not found.
   */
  public @Nullable ShopItemData getShopItemFromStack(@NonNull ItemStack stack) {
    // read shop item persistent data
    return getOptionalPDC(stack, exchangePlugin.getShopItemKey(), Long.class)
        .map(
            id -> {
              ShopItemData cachedData = this.shopItemCache.get(id);
              if (cachedData != null) return cachedData;

              Long shopId =
                  getOptionalPDC(stack, exchangePlugin.getShopItemShopKey(), Long.class)
                      .orElseThrow(() -> componentException("ShopIdKey"));
              Long price =
                  getOptionalPDC(stack, exchangePlugin.getShopItemPrice(), Long.class)
                      .orElseThrow(() -> componentException("PriceKey"));
              Integer quantity =
                  getOptionalPDC(stack, exchangePlugin.getShopItemQuantity(), Integer.class)
                      .orElseThrow(() -> componentException("QuantityKey"));

              return getShopItemFromStack(stack, id, shopId, price, quantity);
            })
        .orElse(null);
  }

  /**
   * Helper method to throw an exception when a required component is missing from an ItemStack's
   * PDC.
   *
   * @param key The key of the missing component.
   * @return IllegalArgumentException to be thrown.
   */
  private IllegalArgumentException componentException(String key) {
    return new IllegalArgumentException("Missing " + key + " component for ShopItemData");
  }

  /**
   * Constructs a ShopItemData object from the details extracted from an ItemStack and additional
   * parameters.
   *
   * @param stack The ItemStack containing additional item details.
   * @param shopItemId The ID of the shop item.
   * @param shopId The ID of the shop where the item is sold.
   * @param price The price of the shop item.
   * @param quantity The quantity of the shop item available.
   * @return The reconstructed ShopItemData object.
   */
  public @NotNull ShopItemData getShopItemFromStack(
      @NonNull ItemStack stack, long shopItemId, long shopId, long price, int quantity) {
    // fetch initial config data
    ItemData itemData = itemDao.from(stack);
    if (itemData == null) {
      throw new IllegalArgumentException(
          "Could not create ItemData from " + ItemUtil.getReadableComponent(stack.displayName()));
    }
    // check for item origin details
    Long originId = core.getItemOriginsDao().getItemOriginId(stack);

    // check signed data
    @Nullable UUID signedId = null;
    long signedTimestamp = 0L;
    Optional<Pair<UUID, Long>> signedData = getSignedData(stack);
    if (signedData.isPresent()) {
      signedId = signedData.get().getKey();
      signedTimestamp = signedData.get().getValue();
    }

    // check for stored item pdc
    Optional<String> optCustomText = getOptionalPDC(stack, customTextKey, String.class);
    Optional<String> optRandomId = getOptionalPDC(stack, randomKey, String.class);
    Optional<Integer> optConsumeUsage = getOptionalPDC(stack, consumeKey, Integer.class);

    return new ShopItemData(
        shopItemId,
        shopId,
        quantity,
        price,
        itemData.getId(),
        originId != null ? originId : -1,
        ItemUtil.getItemColor(stack),
        optRandomId.orElse(""),
        optConsumeUsage.orElse(0),
        signedId,
        signedTimestamp,
        optCustomText.orElse(""),
        -1,
        -1);
  }

  /**
   * Retrieves cached, or Constructs a new ShopItemData object from the details extracted from an
   * ItemStack
   *
   * @param shopId The ID of the shop where the item is sold.
   * @return The reconstructed ShopItemData object.
   */
  public @NotNull ShopItemData getOrCreateShopItem(
      @NonNull ItemStack itemStack, @NonNull Long shopId) {
    ShopItemData shopItemData = this.getShopItemFromStack(itemStack);
    if (shopItemData == null) {
      shopItemData = getShopItemFromStack(itemStack, -1, shopId, 0, 0);
    }
    return shopItemData;
  }

  /**
   * Formats a price value into a readable string format using locale-specific formatting.
   *
   * @param price The price value to format.
   * @return A formatted string representing the price.
   */
  private String formatPrice(long price) {
    return NumberFormat.getNumberInstance(Locale.US).format(price);
  }

  /* Shop Utils */

  public List<ShopItemData> findMatchingShopItem(
      @NonNull PlayerShopData shopData, @NonNull final ItemStack itemIn) {
    if (getSignedData(itemIn).isPresent()) return Collections.emptyList();
    final ItemData itemData = itemDao.from(itemIn);
    if (itemData == null) return Collections.emptyList();

    return shopData.itemIds().stream()
        .map(shopItemCache::get)
        .filter(Objects::nonNull)
        .filter(item -> item.itemId().equals(itemData.getId()))
        .toList();
  }

  public Optional<Pair<UUID, Long>> getSignedData(@NonNull ItemStack stack) {
    Optional<UUID> signedId =
        getOptionalPDC(stack, signedIdKey, String.class).map(UUID::fromString);
    Optional<Long> timestamp = getOptionalPDC(stack, signedTimeKey, Long.class);
    if (signedId.isPresent() && timestamp.isPresent()) {
      return Optional.of(new Pair<>(signedId.get(), timestamp.get()));
    }
    return Optional.empty();
  }

  /** Clears all caches and resets internal states upon plugin shutdown. */
  @Override
  public void onShutdown() {
    this.itemDataLink.clear();
    this.categoryFilteredIds.clear();
    this.rarityFilteredIds.clear();
    this.itemStackCache.invalidateAll();
    this.shopItemCache.clear();
  }
}
