package AuctionHouse.menu;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.pixelhideaway.core.HideawayServer;
import com.pixelhideaway.core.statistic.ServerStatisticManager;
import com.pixelhideaway.grandexchange.manager.GrandExchangeManager;
import com.pixelhideaway.grandexchange.manager.PlayerShopManager;
import com.playhideaway.menus.InvisibleMenuItem;
import lombok.NonNull;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The central hub menu for the Grand Exchange This simple menu links the other main parts of the
 * Exchange system together.
 *
 * @author Cammy
 */
public final class GrandExchangeHub extends ExchangeUI {

  @Inject
  public GrandExchangeHub(
      @Assisted @NonNull Player owner,
      @NonNull HideawayServer entrypoint,
      @NonNull PlayerShopManager playerShopManager,
      @NonNull GrandExchangeManager grandExchangeManager,
      @NonNull ServerStatisticManager statisticManager) {
    super(owner, entrypoint, playerShopManager, grandExchangeManager, statisticManager);
    logger.warn("CREATING HUB MENU FOR '{}'", owner.getName());
  }

  @Override
  public void open(@NotNull Player player, boolean addHistory) {
    super.open(player, false);
  }

  private boolean exchangeNerdStats = false;
  private boolean shopNerdStats = false;

  @Override
  protected void populate(@NotNull Player player) {
    this.setTitle(new TitleBuilder("GE_hub").build());

    ButtonPanel itemsMenuButton =
        new ButtonPanel(
            4,
            6,
            getExchangeItem(),
            event -> {
              playClickSound();
              if (event.isRightClick()) {
                this.exchangeNerdStats = !exchangeNerdStats;
                this.update(player);
                return;
              }
              menuFactory.createExchangeItemMenu(this.owner);
            });

    ButtonPanel shopMenuButton =
        new ButtonPanel(
            4,
            4,
            getShopListItem(),
            event -> {
              playClickSound();
              if (event.isRightClick()) {
                this.shopNerdStats = !shopNerdStats;
                this.update(player);
                return;
              }
              menuFactory.createShopListMenu(owner);
            });

    ButtonPanel shopEditorButton =
        new ButtonPanel(
            4,
            2,
            "exchange.hub.shop_editor",
            event -> {
              playClickSound();
              logger.warn("ABOUT TO OPEN PLAYERS SHOP FOR '{}'", owner.getName());
              playerShopManager
                  .findOrCreatePlayerShop(owner)
                  .thenAccept(shopData -> {
                    logger.warn("Did we find or create shop data: {}", shopData);
                    menuFactory.createShopEditorMenu(owner, shopData);
                  });
            });

    this.setSlot(0, 0, itemsMenuButton);
    this.setSlot(5, 0, shopMenuButton);
    this.setSlot(5, 4, shopEditorButton);
  }

  private ItemStack getExchangeItem() {
    String id = "exchange.hub.item_menu" + (exchangeNerdStats ? ".nerd" : "");
    ItemStack stack;
    if (exchangeNerdStats) {
      // todo more tags for stats
      TagResolver resolver = TagResolver.resolver("2", Tag.preProcessParsed(""));
      stack = InvisibleMenuItem.get(this.gameTextDao.getResolvedTextMultiple(id, resolver));
    } else {
      stack = InvisibleMenuItem.get(this.gameTextDao.getResolvedTextMultiple(id));
    }
    return stack;
  }

  private ItemStack getShopListItem() {
    String id = "exchange.hub.shop_list" + (shopNerdStats ? ".nerd" : "");
    ItemStack stack;
    if (shopNerdStats) {
      // todo more tags for stats
      TagResolver resolver = TagResolver.resolver("2", Tag.preProcessParsed(""));
      stack = InvisibleMenuItem.get(this.gameTextDao.getResolvedTextMultiple(id, resolver));
    } else {
      stack = InvisibleMenuItem.get(this.gameTextDao.getResolvedTextMultiple(id));
    }
    return stack;
  }
}
