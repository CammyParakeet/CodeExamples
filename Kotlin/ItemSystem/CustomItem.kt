/**
 * Represents a custom item managed by the Shaded item system
 *
 * A `ShadedItem` wraps a Bukkit [ItemStack] and provides access to:
 * - Custom persistent tags (`tag(...)`, `hasTag(...)`, etc.)
 * - Strict slot rules for inventory placement
 * - Utility methods to give the item to players, handling slot logic and drops
 *
 * This system is designed to interoperate with `ItemEffect`s, `Enchantable`s
 *
 * @author Cammy
 */
abstract class CustomItem(
    open val itemStack: ItemStack
) {

    companion object {
        val TAG_KEY = NamespacedKey("****", "custom_item")
        val STACK_SIZE_KEY = NamespacedKey("****", "stack_size")
        val NON_STACKABLE_KEY = NamespacedKey("****", "non_stackable")
        val STRICT_SLOT_KEY = NamespacedKey("****", "strict_slot")

        /**
         * Checks if an ItemStack is a `CustomItem`
         */
        fun isCustomItem(item: ItemStack?): Boolean {
            return item?.itemMeta?.persistentDataContainer
                ?.has(TAG_KEY, PersistentDataType.STRING) == true
        }

        /**
         * Attempts to convert an `ItemStack` into a `CustomItem`, or returns `null` if invalid
         */
        fun from(itemStack: ItemStack?): CustomItem? {
            if (itemStack == null || !isCustomItem(itemStack)) return null
            return object : CustomItem(itemStack) {}
        }
    }

    /**
     * Retrieves the Bukkit `PersistentDataContainer`
     */
    private val pdc: PersistentDataContainer
        get() = itemStack.itemMeta!!.persistentDataContainer

    /**
     * Marks this item as non-stackable
     */
    var isNonStackable: Boolean
        get() = pdc.get(NON_STACKABLE_KEY, PersistentDataType.BYTE)?.toInt() == 1
        set(value) {
            itemStack.editMeta {
                val pdc = it.persistentDataContainer
                if (value) {
                    pdc.set(NON_STACKABLE_KEY, PersistentDataType.BYTE, 1)
                    it.setMaxStackSize(1)
                } else {
                    pdc.remove(NON_STACKABLE_KEY)
                    it.setMaxStackSize(stackSize)
                }
            }
        }

    /**
     * Gets the custom stack size or defaults to max stack size
     */
    var stackSize: Int
        get() = if (isNonStackable) 1 else pdc.get(STACK_SIZE_KEY, PersistentDataType.INTEGER) ?: itemStack.maxStackSize
        set(value) {
            if (value < 1) return
            if (isNonStackable) return

            itemStack.editMeta {
                it.persistentDataContainer.set(STACK_SIZE_KEY, PersistentDataType.INTEGER, value)
            }
        }

    /**
     * Checks if this item has a strict slot restriction
     */
    var isStrictSlot: Boolean
        get() = pdc.get(STRICT_SLOT_KEY, PersistentDataType.BYTE)?.toInt() == 1
        set(value) {
            itemStack.editMeta {
                it.persistentDataContainer.set(STRICT_SLOT_KEY, PersistentDataType.BYTE, (if (value) 1 else 0).toByte())
            }
        }

    /**
     * Retrieves the set of tags assigned to this item
     */
    val tags: MutableSet<String>
        get() {
            val meta = itemStack.itemMeta ?: return mutableSetOf()
            val raw = meta.persistentDataContainer.get(TAG_KEY, PersistentDataType.STRING) ?: return mutableSetOf()
            return raw.split(",").filter { it.isNotBlank() }.toMutableSet()
        }

    /**
     * Adds a tag to this item
     */
    fun addTag(tag: String) {
        val updatedTags = tags.apply { add(tag) }
        updateTags(updatedTags)
    }

    /**
     * Removes a tag from this item
     */
    fun removeTag(tag: String) {
        val updatedTags = tags.apply { remove(tag) }
        updateTags(updatedTags)
    }

    /**
     * Checks if the item has a specific tag
     */
    fun hasTag(tag: String): Boolean = tags.contains(tag)

    /**
     * Updates the persistent tags
     */
    private fun updateTags(tags: Set<String>) {
        val joined = tags.joinToString(",")
        itemStack.editMeta {
            it.persistentDataContainer.set(TAG_KEY, PersistentDataType.STRING, joined)
        }
    }

    /**
     * Gives this item to a single player.
     */
    fun giveTo(
        player: Player,
        slot: Int = -1,
        onFail: (ItemStack) -> Unit = { player.world.dropItemNaturally(player.location, it) }
    ) {
        giveItem(player, slot, onFail)
    }

    /**
     * Gives this item to multiple players.
     */
    fun giveTo(
        players: Collection<Player>,
        slot: Int = -1,
        onFail: (Player, ItemStack) -> Unit = { player, item -> player.world.dropItemNaturally(player.location, item) }
    ) {
        players.forEach { player -> giveTo(player, slot) { onFail(player, it) } }
    }

    /**
     * Internal function to handle giving a [CustomItem] to a player
     */
    private fun giveItem(
        player: Player,
        slot: Int = -1,
        onFail: (ItemStack) -> Unit
    ) {
        val itemStack = this.itemStack

        // Try placing in a specific slot if provided
        if (slot in 0 until player.inventory.size) {
            val existing = player.inventory.getItem(slot)

            if (existing == null || existing.type.isAir) {
                player.inventory.setItem(slot, itemStack)
                return
            }

            // If the item has a strict slot restriction, fail immediately
            if (this.isStrictSlot) {
                onFail(itemStack)
                return
            }
        }

        // Try placing in any available inventory slot
        val leftovers = player.inventory.addItem(itemStack)

        // Handle any items that couldn't fit
        leftovers.values.forEach(onFail)
    }

    /**
     * Converts to a readable string
     * TODO improve
     */
    override fun toString(): String {
        return "CustomItem(" +
                "type=${itemStack.type}, " +
                "tags=$tags, " +
                "stackSize=$stackSize, " +
                "nonStackable=$isNonStackable)"
    }

}