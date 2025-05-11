import kotlin.reflect.KClass

object EffectEventBus {

    private val handlers = mutableMapOf<KClass<out Event>, MutableList<EffectEventHandler<out Event>>>()

    fun <T : Event> register(handler: EffectEventHandler<T>) {
        handlers.computeIfAbsent(handler.eventType) { mutableListOf() }.add(handler)
    }

    @Suppress("UNCHECKED_CAST")
    fun dispatch(event: Event, player: Player) {
        val matchingHandlers = handlers.entries
            .filter { (registeredType, _) -> registeredType.isInstance(event) }
            .flatMap { it.value }

        for (handler in matchingHandlers) {
            val applicable = (handler as EffectEventHandler<Event>).findApplicableItem(event, player) ?: continue
            handler.handle(event, applicable)
        }
    }

    fun clear() {
        handlers.clear()
    }

}