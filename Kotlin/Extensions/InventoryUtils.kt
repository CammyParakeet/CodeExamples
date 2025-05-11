package Extensions

/**
 * Provides utility functions for managing player inventories.
 *
 * @author Cammy
 * @since 1.0
 */

object InventoryUtils {

    /**
     * Attempts to add an item to the player's inventory in slots other than the hotbar and armor slots.
     * This function starts searching from the top inventory slot and excludes the hotbar and armor slots,
     * trying to place the item in the first available (empty or air) slot.
     *
     * @param item The [ItemStack] to be added to the inventory.
     * @return `true` if the item was successfully added, `false` otherwise.
     */
    fun Player.giveToSecondaryInventory(item: ItemStack): Boolean {
        // loop through all inventory slots excluding hotbar and armor slots
        for (i in 9..< inventory.size - 5) {
            val invItem = inventory.getItem(i)
            if (invItem == null || invItem.type == Material.AIR) {
                inventory.setItem(i, item)
                return true
            }
        }
        return false
    }

    /**
     * Adds an item to the player's inventory or drops it in the world if the inventory is full.
     * This method first tries to add the item to the player's inventory. If the inventory cannot
     * accommodate the item, the leftover items are dropped at the player's location in the world.
     *
     * @param item The [ItemStack] to be added to the inventory or dropped.
     */
    fun Player.giveOrDropItem(item: ItemStack) {
        val leftOver = inventory.addItem(item)
        leftOver.forEach { (_, itemStack) ->
            world.dropItem(location, itemStack)
        }
    }

    /**
     * Attempts to add an item to the player's secondary inventory (excluding the hotbar and armor slots)
     * and drops it in the world if no space is available in the secondary inventory.
     * This method leverages [giveToSecondaryInventory] to attempt the addition; if unsuccessful, the item
     * is dropped at the player's current location.
     *
     * @param item The [ItemStack] to be added to the secondary inventory or dropped in the world.
     */
    fun Player.giveSecondaryOrDrop(item: ItemStack) {
        if (!giveToSecondaryInventory(item)) world.dropItem(location, item)
    }



}