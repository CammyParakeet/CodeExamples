package AuctionHouse.menu.ui;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.commons.base.data.ItemFilterCriteria;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.data.item.ItemRarity;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.text.TextUtils;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.ExchangeUI;
import com.pixelhideaway.grandexchange.menu.ExchangeView;
import com.playhideaway.menus.InvisibleMenuItem;
import com.playhideaway.menus.MenuPanel;
import lombok.NonNull;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Represents a user interface for filtering shop items by their rarity within the exchange system.
 * This UI allows players to select a rarity level to filter items displayed in the previous menu.
 *
 * @author Cammy
 */
public final class RarityFilterUI extends ExchangeUI {

  private final ExchangeView prevMenu;
  private final ItemFilterCriteria currentFilter;

  private static final List<ItemRarity> rarities =
      List.of(
          ItemRarity.COMMON,
          ItemRarity.UNCOMMON,
          ItemRarity.RARE,
          ItemRarity.EXOTIC,
          ItemRarity.MYTHICAL);

  @Inject
  public RarityFilterUI(
      @Assisted @NonNull ExchangeView context,
      @NonNull HideawayServer entrypoint,
      @NonNull PlayerShopManager playerShopManager,
      @NonNull GrandExchangeManager grandExchangeManager,
      @NonNull ServerStatisticManager statisticManager) {
    super(
        context.getOwner(),
        entrypoint,
        playerShopManager,
        grandExchangeManager,
        statisticManager,
        6);
    this.prevMenu = context;
    this.currentFilter = context.getCurrentFilter();
  }

  @Override
  public void open(@NotNull Player player, boolean addHistory) {
    super.open(player, false);
  }

  /**
   * Applies the specified filter criteria to the previous menu context and plays a sound effect to
   * acknowledge the filter application.
   *
   * @param criteria The criteria to apply as the current filter.
   */
  private void applyFilter(ItemFilterCriteria criteria) {
    playClickSound();
    this.prevMenu.applyFilters(criteria);
  }

  @Override
  protected void populate(@NotNull Player player) {
    this.setTitle(new TitleBuilder("GE_filters").build());
    this.setExitButton(
        8,
        0,
        event -> {
          playClickSound();
          this.prevMenu.open();
        });

    for (int i = 0; i < rarities.size(); i++) {
      this.setSlot(3, i, new RarityButton(rarities.get(i)));
    }

    for (int i = 0; i < 3; i++) {
      this.setSlot(
          3 + i,
          5,
          InvisibleMenuItem.get(this.gameTextDao.getResolvedText("exchange.filter.reset_message")),
          event -> this.applyFilter(this.currentFilter.setRarities(Collections.emptyList())));
    }
  }

  /** Represents a button within the UI that applies a specific rarity filter when clicked. */
  private class RarityButton extends MenuPanel {

    private final ItemRarity rarity;

    public RarityButton(ItemRarity rarity) {
      super(3, 1);
      this.rarity = rarity;
    }

    @Override
    protected void populate(@NotNull Player player) {
      TagResolver resolver =
          TagResolver.resolver(
              "filter",
              Tag.preProcessParsed(TextUtils.capitalizeFirstCharacter(rarity.toString())));
      for (int i = 0; i < 3; i++) {
        this.setSlot(
            i,
            0,
            InvisibleMenuItem.get(
                RarityFilterUI.this.gameTextDao.getResolvedText(
                    "exchange.filter.apply_message", resolver)),
            event -> applyFilter(currentFilter.toggleRarity(rarity.toString())));
      }
    }
  }
}
