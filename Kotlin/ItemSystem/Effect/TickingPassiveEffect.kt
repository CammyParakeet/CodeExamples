abstract class TickingPassiveEffect(
    val tag: String? = null,
    private val match: (PlayerContext) -> Boolean,
    private val onStart: ((Player) -> Unit)? = null,
    private val onEnd: ((Player) -> Unit)? = null
) : TickingEffect {

    private val tickingPlayers = mutableMapOf<UUID, Long>()

    abstract fun getContext(player: Player): PlayerContext

    override fun startTicking(player: Player) {
        val context = getContext(player)
        if (match(context) && player.uniqueId !in tickingPlayers) {
            tickingPlayers[player.uniqueId] = 0
            onStart?.invoke(player)
        }
    }

    override fun stopTicking(player: Player) {
        if (tickingPlayers.remove(player.uniqueId) != null) {
            onEnd?.invoke(player)
        }
    }

    override fun tick(value: PlayerContext) {
        val player = value.player
        if (!player.isOnline) {
            stopTicking(player) // Clean up
            return
        }

        tickingPlayers.computeIfPresent(player.uniqueId) { _, ticks ->
            tickActive(value, ticks)
            ticks + 1
        }
    }

    abstract fun tickActive(context: PlayerContext, ticks: Long)

    fun shouldRemainActive(context: PlayerContext): Boolean {
        return match(context)
    }

    fun getActivePlayers(): List<Player> =
        tickingPlayers.keys.mapNotNull { Bukkit.getPlayer(it) }

    fun cleanup() {
        tickingPlayers.keys.removeIf { uuid ->
            Bukkit.getPlayer(uuid)?.isOnline != true
        }
    }
}

fun tickingPassiveEffect(
    tag: String? = null,
    match: (PlayerContext) -> Boolean = { ctx ->
        tag != null &&
                ctx is ArmorPassiveContext &&
                ctx.armor[EquipmentSlot.HEAD]?.hasTag(tag) == true
    },
    onStart: ((Player) -> Unit)? = null,
    onEnd: ((Player) -> Unit)? = null,
    getContext: (Player) -> PlayerContext = { player ->
        val armorMap = EquipmentSlot.entries
            .filter { it.isArmorSlot() }
            .mapNotNull { slot ->
                val item = player.inventory.getItem(slot)
                val shaded = ShadedItem.from(item)
                if (shaded != null) slot to shaded else null
            }
            .toMap()
        ArmorPassiveContext(player, armorMap)
    },
    onTick: (PlayerContext, Long) -> Unit
): TickingPassiveEffect {
    return object : TickingPassiveEffect(tag, match, onStart, onEnd) {
        override fun tickActive(context: PlayerContext, ticks: Long) = onTick(context, ticks)
        override fun getContext(player: Player) = getContext(player)
    }
}