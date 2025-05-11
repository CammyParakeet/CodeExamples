import kotlin.reflect.KClass

abstract class TagEffectHandler<T : Event>(
    val tag: String,
    override val eventType: KClass<T>
) : EffectEventHandler<T> {

    override fun findApplicableItem(event: T, player: Player): ItemTriggerContext? {
        val shaded = findTaggedItem(player, event, tag) ?: return null
        return ItemTriggerContext(player, shaded, null)
    }

    abstract fun findTaggedItem(player: Player, event: T, tag: String): ShadedItem?

}

typealias CombatEffect<T> = TagEffectHandler<T>
typealias ActiveEffect<T> = TagEffectHandler<T>
typealias PassiveEffect<T> = TagEffectHandler<T>