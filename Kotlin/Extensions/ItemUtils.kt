package Extensions

/**
 * Code example
 * This utility object provides extension functions for inline lambda editing of an Itemstacks
 * ItemMeta
 */

object ItemUtils {

    /**
     * Modifies the item's [ItemMeta] inline
     * @param action A lambda that receives the items current ItemMeta as a specific type and allows direct modifications.
     */
    inline fun <reified T : ItemMeta> ItemStack.editMeta(action: T.() -> Unit) {
        if (!editMetaSafe<T>(action))
            throw IllegalStateException("ItemMeta type mismatch: Expected ItemMeta type ${T::class.java.simpleName}, but found ${itemMeta?.javaClass?.simpleName}")
    }

    /**
     * Safely modifies the item's [ItemMeta] inline
     * @param action A lambda that receives the items current ItemMeta as a specific type and allows direct modifications.
     * @return true if the ItemMeta could be modified, false otherwise.
     */
    inline fun <reified T : ItemMeta> ItemStack.editMetaSafe(action: T.() -> Unit): Boolean {
        val meta = this.itemMeta
        if (meta is T) {
            action(meta)
            this.itemMeta = meta
            return true
        }
        return false
    }

    // USAGE
    fun usage(item: ItemStack) {
        item.editMeta<LeatherArmorMeta> {
            this.color = ...
        }
    }

}