package UITools

import PhantomManager
import java.util.*

/**
 * ** CODE EXAMPLE **
 * Base Interface for entity based UI Elements
 */

interface Button : Listener, AnimatedComponent {

    val dataManager: PhantomManager?
    val uniqueId: UUID

    fun spawn()

    fun addViewer(uuid: UUID)

    fun addViewers(ids: List<UUID>)

    fun removeViewer(uuid: UUID)

    fun removeViewers(ids: List<UUID>)

    fun remove()

    /**
     * movement
     */

    fun scale(scalar: Float) {
        scale(scalar, scalar, scalar)
    }

    fun scale(sX: Float, sY: Float, sZ: Float)

    fun translate(dX: Float, dY: Float, dZ: Float)

    fun rotate(angle: Float)

}