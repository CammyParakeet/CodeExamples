import java.util.*

/**
 *  This code showcase is an interface
 *  for Packet based entities
 *
 *  Contains some default functions`
 */

interface Phantom {

    var manager: PhantomManager?
    val glowForList: MutableSet<UUID>
    val viewers: MutableSet<UUID>

    val uniqueId: UUID
    val entityId: Int
    var entityType: EntityType
    val entityData: PhantomData
    var location: Location?
    val isAlive: Boolean
        get() = viewers.isNotEmpty()

    val requiresUpdate: Boolean
        get() = entityData.requiresUpdate


    fun spawn() {
        viewers.toList().forEach(this::spawnFor)
    }

    fun spawnFor(playerId: UUID) {
        Bukkit.getPlayer(playerId)?.let(this::spawnFor)
    }

    fun spawnFor(player: Player) {
        PhantomPacketFactory.createSpawnPhantomPacket(this)?.let {
            PacketUtil.sendPacket(player, it)
            viewers.add(player.uniqueId)
            //PacketUtil.sendPacket(player, PhantomPacketFactory.createUpdatePhantomPacket(this))
        } ?: {
            Logger.error("Cannot spawn element with a null location: $uniqueId")
        }
    }

    fun removeFor(playerId: UUID) {
        Bukkit.getPlayer(playerId)?.let(this::removeFor) ?: viewers.remove(playerId)
    }

    fun removeFor(player: Player) {
        PacketUtil.sendPacket(player, PhantomPacketFactory.createRemovePhantomPacket(this))
        viewers.remove(player.uniqueId)
    }

    fun remove() {
        viewers.toList().forEach(this::removeFor)
    }

    fun teleport(loc: Location) {
        location = loc
        viewers.forEach { viewer -> Bukkit.getPlayer(viewer)?.let { player ->
            PhantomPacketFactory.createTeleportPhantomPacket(this)?.let { PacketUtil.sendPacket(player, it) }
        } }
    }

    fun toggleGlow(playerId: UUID, glowing: Boolean) {
        glowForList.apply { if (glowing) add(playerId) else remove(playerId) }
    }

    fun <T: Entity> addPassenger(entity: T) {
        //TODO
    }

}