package AuctionHouse.data;

/**
 * Defines the capability to sort shop items or listings according to specified sorting criteria.
 * Implementing classes are expected to handle the sorting logic based on the {@link ShopSort}
 * options provided and maintain the state of the current sorting option.
 *
 * @author Cammy
 */
public interface ShopSorted {

  /**
   * Applies a specified sorting option to the shop's items or listings. This method should organize
   * the items according to the criteria defined in the given {@link ShopSort}.
   *
   * @param sortOption The sorting option to apply, which defines the order and criteria for sorting
   *     items.
   */
  void applySort(ShopSort sortOption);

  /**
   * Retrieves the current sorting option applied to the shop's items or listings. This method
   * allows querying of the active sort state to display or further process the sorted items.
   *
   * @return The current {@link ShopSort} option that is being used to sort the shop's items. If no
   *     sorting has been applied yet, implementations might return a default or null value.
   */
  ShopSort getCurrentSortOption();

  enum ShopSort {
    PRICE_ASCENDING,
    PRICE_DESCENDING,
    CREATION_ASCENDING,
    CREATION_DESCENDING
  }
}
