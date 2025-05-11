abstract class InteractTriggerEffect(
    tag: String,
    useOffHand: Boolean = false,
    private val allowedActions: Set<Action>
) : HeldItemEffectHandler<PlayerInteractEvent>(tag, PlayerInteractEvent::class, useOffHand) {

    override fun findApplicableItem(event: PlayerInteractEvent, player: Player): ItemTriggerContext? {
        if (event.action !in allowedActions) return null
        return super.findApplicableItem(event, player)
    }

}

fun interactTriggerEffect(
    tag: String,
    useOffHand: Boolean = false,
    allowedActions: Set<Action> = setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK),
    handle: (PlayerInteractEvent, ItemTriggerContext) -> Unit
): InteractTriggerEffect {
    return object : InteractTriggerEffect(tag, useOffHand, allowedActions) {
        override fun handle(event: PlayerInteractEvent, context: ItemTriggerContext) {
            handle(event, context)
        }
    }
}