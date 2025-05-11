package AuctionHouse.data;

import com.pixelhideaway.commons.base.configuration.data.text.BlankSpace;
import com.pixelhideaway.commons.base.util.Pair;
import lombok.NonNull;
import net.kyori.adventure.key.Key;
import org.intellij.lang.annotations.Subst;

import java.util.ArrayList;
import java.util.List;

public class ShopMenuUtils {

  private static final String DISTRICT = "district_ui_font_";

  /**
   * Formats the raw shop name by cleaning up unwanted characters and ensuring it does not exceed a
   * certain length.
   *
   * @param rawName The unformatted name of the shop.
   * @return A cleaned and possibly truncated version of the shop name suitable for display.
   */
  public static String formatShopName(@NonNull String rawName, int maxChars) {
    String cleanedName =
        rawName.toLowerCase().replaceAll("[^0-9a-z@#$%&'()*+\\-/\":;<=>?!¡\\[\\]\\\\^_{}., ]", "");
    if (cleanedName.length() > maxChars) {
      cleanedName = cleanedName.substring(0, maxChars) + " <";
    }
    return cleanedName;
  }

  /**
   * Creates title components for the shop display using the given shop name.
   *
   * @param shopName The formatted name of the shop.
   * @param index The index of the shop in the list (used for font styling).
   * @return A list of title components for the shop.
   */
  public static List<Pair<Key, String>> createTitleComponents(String shopName, int index) {
    List<Pair<Key, String>> titleComponents = new ArrayList<>();

    titleComponents.add(Pair.of(null, BlankSpace.get(19)));
    for (char c : shopName.toCharArray()) {
      String charAsString = Character.toString(c).replace(":", "colon").replace(";", "semi_colon");
      @Subst("")
      String keyName = DISTRICT + (index + 1);
      String fontValue = DISTRICT + charAsString;
      titleComponents.add(Pair.of(Key.key("hideaway", keyName), fontValue));
    }
    titleComponents.add(Pair.of(null, ""));

    return titleComponents;
  }
}
