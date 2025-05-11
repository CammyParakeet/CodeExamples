interface EffectEventHandler<T : Event> {
    val eventType: KClass<T>

    /**
     * Responsible for deciding whether this effect should apply
     */
    fun findApplicableItem(event: T, player: Player): ItemTriggerContext?

    /**
     * Run the effect logic
     */
    fun handle(event: T, context: ItemTriggerContext)
}