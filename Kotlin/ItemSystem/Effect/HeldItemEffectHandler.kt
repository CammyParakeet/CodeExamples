import kotlin.reflect.KClass

abstract class HeldItemEffectHandler<T : Event>(
    tag: String,
    eventType: KClass<T>,
    private val acceptOffHand: Boolean
) : TagEffectHandler<T>(tag, eventType) {

    override fun findTaggedItem(player: Player, event: T, tag: String): CustomItem? {
        val main = CustomItem.from(player.inventory.itemInMainHand)
        val off = if (acceptOffHand) ShadedItem.from(player.inventory.itemInOffHand) else null

        return listOfNotNull(main, off).firstOrNull { it.hasTag(tag) }
    }

}

fun <T : Event> heldItemEffect(
    tag: String,
    eventType: KClass<T>,
    useOffHand: Boolean,
    handle: (T, ItemTriggerContext) -> Unit
) : HeldItemEffectHandler<T> {
    return object : HeldItemEffectHandler<T>(tag, eventType, useOffHand) {
        override fun handle(event: T, context: ItemTriggerContext) {
            handle(event, context)
        }

    }
}