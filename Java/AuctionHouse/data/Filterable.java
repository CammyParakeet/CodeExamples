package AuctionHouse.data;

import com.pixelhideaway.commons.base.data.ItemFilterCriteria;

/**
 * Represents an entity that can be filtered based on various criteria defined in {@link
 * ItemFilterCriteria}. This interface ensures that implementing classes provide methods to apply
 * filtering and retrieve the current set of filters.
 *
 * @author Cammy
 */
public interface Filterable {

  /**
   * Applies filtering to this object based on the provided criteria.
   *
   * @param criteria The filtering criteria to apply.
   */
  void applyFilters(ItemFilterCriteria criteria);

  /**
   * Retrieves the current filtering criteria applied to this object.
   *
   * @return The current set of filter criteria. If no filters are applied, this might return a
   *     default or empty set of criteria.
   */
  ItemFilterCriteria getCurrentFilter();
}
