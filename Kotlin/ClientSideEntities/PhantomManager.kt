/**
 * This Code Showcase is that of
 * a Packet based entity manager.
 *
 * The goal is to efficiently combine entity updates
 * (which are async safe packets) for each viewer.
 * Packet per player get bundled into a single packet to reduce network usage.
 */

class PhantomManager(val owningPlugin: Plugin) {

    val phantoms = mutableMapOf<Int, Phantom>()
    private var updateCheckTask: BukkitTask? = null

    init {
        updateCheckTask = owningPlugin.server.scheduler.runTaskTimerAsynchronously(owningPlugin, Runnable {
            sendUpdates()
        }, 0L, 1L)
    }

    private fun sendUpdates() {
        val playerUpdateMap = mutableMapOf<Player, MutableList<Packet<*>>>()

        phantoms.values.forEach { phantom ->
            if (phantom.requiresUpdate) {
                phantom.viewers.forEach { viewerId ->
                    Bukkit.getPlayer(viewerId)?.let {
                        playerUpdateMap.computeIfAbsent(it) { mutableListOf() }.add(
                            PhantomPacketFactory.createUpdatePhantomPacket(phantom)
                        )
                    } ?: phantom.removeFor(viewerId)
                }
            }
        }

        playerUpdateMap.forEach { (player, packets) ->
            PacketUtil.sendPacket(player, PhantomPacketFactory.createBundlePacket(packets))
        }
    }

    fun addPhantom(phantom:PhantomManager) {
        phantoms[phantom.entityId] = phantom
    }

    fun removePhantom(phantom:PhantomManager) {
        phantoms.remove(phantom.entityId)
        phantom.remove()
    }

    fun onDisable() {
        updateCheckTask?.cancel()
        updateCheckTask = null
        phantoms.clear()
    }


}