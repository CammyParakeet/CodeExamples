package AuctionHouse.menu;

import com.pixelhideaway.commons.base.data.PageResult;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Abstract class representing a menu system with pages of data, using a caching mechanism to manage
 * and prefetch data around the currently viewed page. This system is designed to minimize server
 * load and improve user experience by preemptively loading and caching data pages.
 *
 * @param <T> The type of data contained within the menu pages.
 * @author Cammy
 */
public abstract class PageBufferedMenu<T> extends ExchangeView {

  /**
   * Maximum number of pages to keep in the cache. <br>
   * Must be MAX_BUFFER * 2 + 1
   */
  protected static final int CACHE_LIMIT = 9;

  /**
   * Maximum number of pages to prefetch around the current page. <br>
   * Must be safely CACHE_LIMIT / 2
   */
  protected static final int MAX_BUFFER = 4;

  /**
   * Maximum number of pages to prefetch around the current page. <br>
   * Must be safely CACHE_LIMIT / 2
   */
  protected static final int BOUNDARY_THRESHOLD = 2;

  protected long totalPages;

  /** Flag to indicate whether a page fetch operation is in progress. */
  protected final AtomicBoolean inPageFetch = new AtomicBoolean(false);

  /**
   * Minimal interval between page change operations to prevent spam. todo this may not be needed
   */
  protected static final long DEBOUNCE_DELAY = 125;

  /** Timestamp of the last page change. */
  protected long lastPageChange;

  /** Cache storing pages of data. */
  protected final Map<Long, List<T>> pageCache = new ConcurrentHashMap<>();

  public PageBufferedMenu(
      @NotNull Player owner,
      @NotNull HideawayServer entrypoint,
      @NotNull PlayerShopManager playerShopManager,
      @NotNull GrandExchangeManager grandExchangeManager,
      @NotNull ServerStatisticManager statisticManager) {
    super(owner, entrypoint, playerShopManager, grandExchangeManager, statisticManager);
  }

  /**
   * Retrieves a page of data.
   *
   * @param page The page number to retrieve.
   * @return The data for the specified page or an empty list if no data exists for that page.
   */
  protected List<T> getPage(long page) {
    return this.pageCache.getOrDefault(page, Collections.emptyList());
  }

  /**
   * Retrieves a specific item from a page.
   *
   * @param page The page number.
   * @param index The index of the item on the page.
   * @return The item at the specified index on the specified page, or null if the index is out of
   *     bounds.
   */
  protected @Nullable T getPageItem(long page, int index) {
    try {
      return getPage(page).get(index);
    } catch (ArrayIndexOutOfBoundsException e) {
      logger.error(
          "Attempted to retrieve out of bounds page item - Page: {} - Index: {}", page, index);
      return null;
    }
  }

  /**
   * Abstract method to load pages from the datasource.
   *
   * @param pages The list of page numbers to load.
   * @return A CompletableFuture that, when completed, provides a list of PageResult containing the
   *     data for the requested pages.
   */
  protected abstract CompletableFuture<List<PageResult<T>>> loadPagesFromDatasource(
      List<Integer> pages);

  /**
   * Fetches and caches pages if they are not already in the cache.
   *
   * @param startPage The first page number to fetch.
   * @param endPage The last page number to fetch.
   * @return A CompletableFuture that completes when the pages are fetched and cached.
   */
  protected CompletableFuture<Void> fetchAndCachePages(int startPage, int endPage) {
    // Determine pages that actually need fetching
    List<Integer> pagesToFetch =
        IntStream.rangeClosed(startPage, endPage)
            .filter(page -> !pageCache.containsKey((long) page))
            // .filter(page -> page >= 0 && page < this.totalPages)
            .boxed()
            .toList();

    if (pagesToFetch.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    return loadPagesFromDatasource(pagesToFetch)
        .thenAccept(
            pageResults -> {
              pageResults.forEach(page -> this.pageCache.put(page.pageNumber(), page.items()));

              if (!pageResults.isEmpty()) this.totalPages = pageResults.getFirst().totalPages();

              maintainCacheSize();
            })
        .exceptionally(
            e -> {
              logger.error("Failed to fetch shop pages", e);
              return null;
            });
  }

  /**
   * Maintains the cache size by removing pages that are outside the buffer range around the current
   * page.
   */
  protected void maintainCacheSize() {
    if (this.pageCache.size() <= CACHE_LIMIT) return;

    List<Long> evictablePages =
        this.pageCache.keySet().stream()
            .filter(page -> page < getBufferLowerBound() || page > getBufferUpperBound())
            .toList();

    for (Long page : evictablePages) {
      if (this.pageCache.size() <= CACHE_LIMIT) break;

      this.pageCache.remove(page);
    }
  }

  /**
   * Changes the currently viewed page and triggers data fetching if necessary.
   *
   * @param player The player viewing the menu.
   * @param increment The number of pages to move from the current page.
   */
  @Override
  protected void changePage(@NotNull Player player, int increment) {
    long currentTime = System.currentTimeMillis();
    if (this.inPageFetch.get() || currentTime - this.lastPageChange < DEBOUNCE_DELAY) return;
    this.lastPageChange = currentTime;

    long newPage = this.page + increment;
    // page index starts at 0
    if (newPage < 0 || newPage >= this.totalPages) return;
    this.page = newPage;

    if (needsCacheUpdate()) {
      this.inPageFetch.set(true);
      fetchAndCachePages((int) getBufferLowerBound(), (int) getBufferUpperBound())
          .thenRun(
              () -> {
                this.inPageFetch.set(false);
                this.update(player);
              });
    } else {
      this.update(player);
    }
  }

  /**
   * Checks if a cache update is necessary based on the provided page boundaries.
   *
   * @return true if an update is necessary, false otherwise.
   */
  protected boolean needsCacheUpdate() {
    if (this.pageCache.isEmpty()) return true;

    if (this.page + BOUNDARY_THRESHOLD > getHighestCachedPage()
        && getHighestCachedPage() < this.totalPages - 1) return true;

    return this.page - BOUNDARY_THRESHOLD < getLowestCachedPage() && getLowestCachedPage() > 0;
  }

  /**
   * Returns the lowest currently cached page
   *
   * @return The lowest page number.
   */
  protected long getLowestCachedPage() {
    return Math.max(
        this.pageCache.keySet().stream().min(Long::compare).orElse(this.page),
        getBufferLowerBound());
  }

  /**
   * Returns the largest currently cached page
   *
   * @return The largest page number.
   */
  protected long getHighestCachedPage() {
    return Math.min(
        this.pageCache.keySet().stream().max(Long::compare).orElse(this.page),
        getBufferUpperBound());
  }

  /**
   * Returns the lower bound of the buffer around the current page.
   *
   * @return The lower bound.
   */
  protected long getBufferLowerBound() {
    return Math.max(0, this.page - MAX_BUFFER);
  }

  /**
   * Returns the upper bound of the buffer around the current page.
   *
   * @return The upper bound.
   */
  protected long getBufferUpperBound() {
    return Math.min(this.page + MAX_BUFFER, this.totalPages - 1);
  }

  /** Resets the page cache and total pages count. */
  @Override
  protected void resetPages() {
    super.resetPages();
    this.pageCache.clear();
    this.totalPages = 0;
  }

  @Override
  protected boolean hasNextPage() {
    return this.page < this.totalPages;
  }
}
