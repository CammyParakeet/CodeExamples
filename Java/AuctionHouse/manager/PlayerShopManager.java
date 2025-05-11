package AuctionHouse.manager;

import com.google.auto.service.AutoService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pixelhideaway.commons.base.client.Clients;
import com.pixelhideaway.commons.base.grandexchange.ExchangeUpdateResponse;
import com.pixelhideaway.commons.base.grandexchange.PlayerShopData;
import com.pixelhideaway.commons.base.grandexchange.ShopItemData;
import com.pixelhideaway.commons.base.nats.NatsEndpoint;
import com.pixelhideaway.commons.base.util.ExponentialBackoff;
import com.pixelhideaway.commons.base.util.Pair;
import com.pixelhideaway.commons.server.injection.Manager;
import com.pixelhideaway.grandexchange.GrandExchange;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages player shops within the grand exchange, including caching, updating, and interacting with
 * shop data.
 *
 * <p>This manager handles both local cache updates and interactions with remote services to fetch
 * and update shop data.
 *
 * @author Cammy
 */
@Slf4j
@Singleton
@AutoService({Manager.class, Listener.class})
public final class PlayerShopManager implements Manager, Listener {

  private final GrandExchange exchangePlugin;
  private final ExchangeSearchManager searchManager;

  /**
   * Map from {@link PlayerShopData#id() Shop Id} to its data Contains a lighter weight cached
   * version of the player shop database. <br>
   * This Shop Data instance only contains Shop Item ID References so the actual caching of {@link
   * ShopItemData} must be manually maintained!
   */
  private final Map<Long, PlayerShopData> shopDataCache = new ConcurrentHashMap<>();

  private final ExponentialBackoff backoff =
      ExponentialBackoff.builder()
          .baseTime(250, TimeUnit.MILLISECONDS)
          .maximumTime(1, TimeUnit.SECONDS)
          .maximumRetries(3)
          .build();

  @Inject
  public PlayerShopManager(
      @NotNull final GrandExchange exchangePlugin,
      @NotNull final ExchangeSearchManager searchManager) {
    this.searchManager = searchManager;
    this.exchangePlugin = exchangePlugin;
  }

  @Override
  public void onEnable() {
    loadPlayerShops();

    NatsEndpoint.EXCHANGE_SHOP_UPDATE.listen(
        event -> {
          event.updatedShops().forEach(this::processShopUpdate);
        });
  }

  /** Loads all player shops from the remote service asynchronously. */
  private void loadPlayerShops() {
    this.backoff
        .<List<PlayerShopData>>attempt(
            callback -> callback.success(Clients.PLAYER_SHOP_CLIENT.getPlayerShops()))
        .handle(
            (shops, e) -> {
              if (e != null) {
                // todo handle
                log.error("There was an error loading all Player Shop Data from service");
                return null;
              }
              this.shopDataCache.clear();
              shops.forEach(this::processShopUpdate);
              return null;
            });
  }

  /* Local Caching Management */

  /**
   * Processes updates for a specific shop, updating the local cache with the latest data.
   *
   * @param shopData The updated player shop data.
   */
  public void processShopUpdate(PlayerShopData shopData) {
    this.shopDataCache.put(shopData.id(), shopData);
  }

  /**
   * Retrieves the shop name for a given shop ID.
   *
   * @param shopId The ID of the shop.
   * @return The name of the shop or "Unknown" if the shop ID does not exist in the cache.
   */
  public @NotNull String getShopName(long shopId) {
    PlayerShopData data = shopDataCache.get(shopId);
    return data == null ? "Unknown" : data.shopName(); // todo handle better?
  }

  /**
   * Currently players will only have 1 player shop, so this method will either find their existing
   * default shop, or create a new one.
   *
   * @param player to find/create a default shop for
   * @return A future with the player shop data
   */
  public CompletableFuture<@NonNull PlayerShopData> findOrCreatePlayerShop(@NonNull Player player) {
    UUID playerId = player.getUniqueId();
    Optional<PlayerShopData> cachedShopData =
        shopDataCache.values().stream()
            .filter(shop -> shop.shopOwner().equals(playerId))
            .findFirst();

    exchangePlugin
        .getSLF4JLogger()
        .warn(
            "Looking for shop data for player '{}' - any cached? {}",
            player.getName(),
            cachedShopData.isPresent());

    return cachedShopData
        .map(CompletableFuture::completedFuture)
        .orElseGet(
            () ->
                CompletableFuture.supplyAsync(
                    () ->
                        Clients.PLAYER_SHOP_CLIENT.getOrDefaultPlayerShop(
                            player.getUniqueId(), player.getName())));
  }

  /**
   * Retrieves or loads player shop data for a given shop ID.
   *
   * @param shopId The ID of the shop to retrieve.
   * @return A future with the shop data.
   */
  public CompletableFuture<PlayerShopData> getOrLoadPlayerShop(long shopId) {
    Optional<PlayerShopData> cachedShopData = Optional.ofNullable(shopDataCache.get(shopId));

    return cachedShopData
        .map(CompletableFuture::completedFuture)
        .orElseGet(
            () ->
                CompletableFuture.supplyAsync(
                    () -> Clients.PLAYER_SHOP_CLIENT.getPlayerShop(shopId)));
  }

  /**
   * Renames a shop and updates the remote service with the new name. todo component? - nah color
   * setting?
   *
   * @param shopData The current shop data.
   * @param newName The new name for the shop.
   * @return A future that completes with updated shop data after the rename operation.
   */
  public CompletableFuture<PlayerShopData> renameShop(
      @NonNull PlayerShopData shopData, @NonNull String newName) {
    log.warn("Shop: {} renaming to {}", shopData.id(), newName);
    return CompletableFuture.supplyAsync(
            () -> Clients.PLAYER_SHOP_CLIENT.updateShop(shopData.id(), shopData))
        .thenApplyAsync(
            response -> {
              processShopUpdate(response);
              return response;
            });
  }

  /* Adding/Removing */

  /**
   * Upserts an item for a shop, either adding a new item or updating an existing one, depending on
   * whether the item already exists in the shop.
   *
   * @param data The shop item data to upsert.
   * @return A CompletableFuture that, when completed, provides an ExchangeUpdateResponse containing
   *     the updated shop data.
   */
  public CompletableFuture<ExchangeUpdateResponse> upsertItemForShop(@NonNull ShopItemData data) {
    return CompletableFuture.supplyAsync(
            () -> Clients.PLAYER_SHOP_CLIENT.upsertItemForShop(data.shopId(), data))
        .thenApplyAsync(
            response -> {
              processShopUpdate(response.updatedShop());
              return response;
            });
  }

  /**
   * Extracts a specific quantity of items from a shop's stock.
   *
   * @param data The shop item data from which items are to be extracted.
   * @param amount The amount of items to extract.
   * @return A CompletableFuture that, when completed, returns a Pair containing the actual number
   *     of items extracted and the ExchangeUpdateResponse with the updated shop data.
   */
  public CompletableFuture<Pair<Integer, ExchangeUpdateResponse>> extractItemsFromShop(
      @NonNull ShopItemData data, int amount) {
    int amountReturned = Math.min(data.quantity(), amount);
    int newQuantity = Math.max(data.quantity() - amount, 0);
    ShopItemData updatedData = data.withNewQuantity(newQuantity);
    return CompletableFuture.supplyAsync(
            () -> Clients.PLAYER_SHOP_CLIENT.upsertItemForShop(updatedData.shopId(), updatedData))
        .thenApplyAsync(
            response -> {
              processShopUpdate(response.updatedShop());
              return new Pair<>(amountReturned, response);
            });
  }

  /**
   * Removes an item completely from a shop.
   *
   * @param data The shop item data to be removed.
   * @return A CompletableFuture that, when completed, provides an ExchangeUpdateResponse containing
   *     the updated shop data.
   */
  public CompletableFuture<ExchangeUpdateResponse> removeItemFromShop(@NonNull ShopItemData data) {
    return CompletableFuture.supplyAsync(
            () -> Clients.PLAYER_SHOP_CLIENT.deleteItem(data.shopId(), data.id()))
        .thenApplyAsync(
            response -> {
              processShopUpdate(response.updatedShop());
              return response;
            });
  }

  /* Purchasing */

  /**
   * Handles the purchase of a player shop by handling transactional logic and updates.
   *
   * @param player The player making the purchase.
   */
  public void purchasePlayerShop(@NotNull final Player player) {
    // todo send to service - get id from service - create new empty menu
  }
}
