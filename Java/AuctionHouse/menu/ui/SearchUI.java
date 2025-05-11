package AuctionHouse.menu.ui;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.menu.helper.HideawayAnvil;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.core.text.TextUtils;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.pixelhideaway.grandexchange.menu.ExchangeUI;
import com.pixelhideaway.grandexchange.menu.ExchangeView;
import com.playhideaway.menus.InvisibleMenuItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.wesjd.anvilgui.AnvilGUI;
import net.wesjd.anvilgui.AnvilGUI.ResponseAction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents the user interface for searching within the exchange environment. This UI class
 * provides a specialized search feature allowing players to input search terms directly through an
 * Anvil GUI.
 *
 * @author Cammy
 */
@Slf4j
public final class SearchUI extends ExchangeUI {

  private final ExchangeView context;

  @Inject
  public SearchUI(
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
        1);
    this.context = context;
  }

  /**
   * Opens the search interface using an Anvil GUI, allowing the player to input search terms
   * directly. It handles the input validation and updates the parent context with the appropriate
   * search filters.
   */
  public void openSearch() {
    HideawayAnvil.createBuilder(entrypoint)
        .jsonTitle(GsonComponentSerializer.gson().serialize(new TitleBuilder("GE_search").build()))
        .itemLeft(getAnvilButtonItem("exchange.search.return"))
        .itemOutput(getAnvilButtonItem("exchange.search.accept"))
        .onClick(
            (slot, state) -> {
              if (slot == AnvilGUI.Slot.OUTPUT) {
                playClickSound();
                String input = state.getText();

                if (TextUtils.containsProfanity(input)) {
                  entrypoint
                      .getNotificationUtils()
                      .queueNotification(owner.getUniqueId(), "Invalid input: Contains Profanity");
                  return List.of();
                }

                log.warn("About to apply filter for: `{}`", input);
                return List.of(
                    ResponseAction.run(
                        () -> context.applyFilters(this.currentFilter.withSearch(input))),
                    ResponseAction.close(),
                    ResponseAction.run(this.context::open));
              }

              if (slot == AnvilGUI.Slot.INPUT_LEFT) {
                return List.of(ResponseAction.close(), ResponseAction.run(this.context::open));
              }
              return List.of();
            })
        .open(owner);
  }

  private ItemStack getAnvilButtonItem(String loreId) {
    ItemStack stack = InvisibleMenuItem.get();
    stack.editMeta(
        meta -> {
          List<Component> lore = meta.lore();
          if (lore == null) return;
          lore.add(this.gameTextDao.getResolvedText(loreId));
          meta.lore(lore);
        });
    return stack;
  }

  @Override
  protected void populate(@NotNull Player player) {}
}
