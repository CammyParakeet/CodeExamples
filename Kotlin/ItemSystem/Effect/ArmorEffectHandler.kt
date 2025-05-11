import kotlin.reflect.KClass

abstract class ArmorEffectHandler<T : Event>(
    tag: String,
    eventType: KClass<T>,
    private val validSlots: Set<EquipmentSlot> = EquipmentSlot.entries.toSet(),
    private val requiredSlots: Set<EquipmentSlot>? = null
) : TagEffectHandler<T>(tag, eventType) {

    override fun findTaggedItem(player: Player, event: T, tag: String): ShadedItem? {
        val armorContents = player.inventory

        // If full set is required, enforce that first
        if (requiredSlots != null) {
            val hasAllRequired = requiredSlots.all { slot ->
                val item = armorContents.getItem(slot)
                val shaded = ShadedItem.from(item)
                shaded?.hasTag(tag) == true
            }
            if (!hasAllRequired) return null
        }

        // Otherwise return any item from valid slots that matches
        return validSlots.firstNotNullOfOrNull { slot ->
            val item = armorContents.getItem(slot)
            val shaded = ShadedItem.from(item)
            shaded?.takeIf { it.hasTag(tag) }
        }
    }
}

fun <T : Event> armorEffect(
    tag: String,
    eventType: KClass<T>,
    validSlots: Set<EquipmentSlot> = EquipmentSlot.entries.toSet(),
    requiredSlots: Set<EquipmentSlot>? = null,
    handle: (T, ItemTriggerContext) -> Unit
): ArmorTagEffectHandler<T> {
    return object : ArmorTagEffectHandler<T>(tag, eventType, validSlots, requiredSlots) {
        override fun handle(event: T, context: ItemTriggerContext) {
            handle(event, context)
        }
    }
}