inline fun <reified T : EntityDamageByEntityEvent> attackerItemEffect(
    tag: String,
    useOffHand: Boolean = false,
    crossinline handle: (T, ItemTriggerContext) -> Unit
): HeldItemEffectHandler<T> {
    return object : HeldItemEffectHandler<T>(tag, T::class, useOffHand) {
        override fun findTaggedItem(player: Player, event: T, tag: String): ShadedItem? {
            val attacker = event.damager as? Player ?: return null

            if (attacker.uniqueId != player.uniqueId) return null

            val main = ShadedItem.from(attacker.inventory.itemInMainHand)
            val off = if (useOffHand) ShadedItem.from(attacker.inventory.itemInOffHand) else null

            return listOfNotNull(main, off).firstOrNull { it.hasTag(tag) }
        }

        override fun handle(event: T, context: ItemTriggerContext) {
            handle(event, context)
        }
    }
}