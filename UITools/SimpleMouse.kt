package UITools

import PhantomManager
import java.util.*
import kotlin.math.abs

/**
 * Simple Mouse uses the remote camera trick
 * and player head rotation mapping to simulate
 * a 2d mouse plane
 * - Viewspace refers to relative coords
 * - Worldspace refers to minecraft absolute coords
 *
 * This is also a proof of concept class
 */

class SimpleMouse(
    private val viewer: UUID,
    private val dataManager: PhantomManager?,
    private val originLocation: Location
) : Listener {

    private val scheduler = Bukkit.getScheduler()
    private val precisionThreshold: Float = 1E-6F
    private val movementThreshold: Float = 0.001F

    val uniqueId = UUID.randomUUID()
    var currentLocation: Location = originLocation
        set(value) {
            if (value != field) {
                field = value
                mouseDisplay?.teleport(value)
            }
        }

    // Viewspace
    var planeUp: Vector = Vector()
    var planeRight: Vector = Vector()
    var currentX: Float = 0.0F // Between -105 and 105
    var currentY: Float = 0.0F // Between -90 and 90
    var currentDepth: Vector = Vector()

    private var yScaleFactor: Float = .012F
    private var xScaleFactor: Float = .0076F

    // Scroll wheel event of some kind will determine this
    var currentGUIScale: Int = 70 // Depth between 30 and 110 (FOV)
        set(value) {
            field = value
            updateViewspaceDepth()
        }


    private val mouseDisplay = Element.create<Element.Model> {
        location = originLocation
        manager = dataManager
        baseItem = ItemStack(Material.IRON_SWORD)
        interpolationDelay = 0
        interpolationDurationTeleport = 1
        billboard = Display.Billboard.CENTER
        setScale(0.1F)
    }


    private fun clampSmallVector(vector: Vector): Vector {
        return Vector(
            if (abs(vector.x) < precisionThreshold) 0.0 else vector.x,
            if (abs(vector.y) < precisionThreshold) 0.0 else vector.y,
            if (abs(vector.z) < precisionThreshold) 0.0 else vector.z
        )
    }

    private fun createViewspacePlanes() {
        val up = Vector(0.0, 1.0, 0.0)
        planeUp = clampSmallVector(originLocation.clone().direction.crossProduct(up).normalize())
        planeRight = clampSmallVector(originLocation.clone().direction.crossProduct(planeUp).normalize())
    }

    private fun updateViewspaceDepth() {
        currentDepth = originLocation.toVector().add(originLocation.direction.multiply((currentGUIScale / 50)))
    }

    private fun updateMousePosition() {
        Bukkit.getWorld("world")?.let {
            // Calculate new position in viewspace
            val dX = currentX * xScaleFactor
            val dY = currentY * yScaleFactor

            val viewspaceXMovement = if (abs(dX) > movementThreshold) planeRight.clone().multiply(dX) else Vector()
            val viewspaceYMovement = if (abs(dY) > movementThreshold) planeUp.clone().multiply(dY) else Vector()

            // Translate viewspace to worldspace
            val worldMovement = viewspaceXMovement.add(viewspaceYMovement)
            val newLocation = currentDepth.clone().add(worldMovement).toLocation(it)

            currentLocation = newLocation
        }
    }

    init {
        createViewspacePlanes()
        updateViewspaceDepth()

        // wait 1 tick before setting new depth position and spawning
        scheduler.runTaskLater(ArcadePlugin.instance, Runnable {
            currentLocation = currentDepth.toLocation(Bukkit.getWorld("World")!!)
            Bukkit.getPluginManager().registerEvents(this, ArcadePlugin.instance)
            spawn()
        }, 1L)
    }

    @EventHandler
    fun onMouseInput(input: RemoteMouseInputEvent) {
        if (input.xRot == currentX && input.yRot == currentY) return

        currentX = input.xRot
        currentY = input.yRot

        updateMousePosition()
    }

    fun spawn() {
        mouseDisplay?.spawnFor(viewer)
    }

    fun remove() {
        mouseDisplay?.remove()
    }

}