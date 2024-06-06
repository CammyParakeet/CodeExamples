package UITools

import PhantomManager
import java.util.*

/**
 * Simple Keyboard Implementation using [TextButton]
 */

class SimpleKeyboard(var location: Location, val phantom: PhantomManager) : Listener {

    companion object {
        const val characters = "qwertyuiopasdfghjklzxcvbnm"
        const val enter = "Enter"
        const val backspace = "<-"
    }

    val keys = mutableMapOf<String, Button>()
    val UI_ID = UUID.randomUUID()

    fun addViewer(viewer: UUID) {
        keys.values.forEach { key -> key.addViewer(viewer) }
    }

    fun addViewers(viewers: List<UUID>) {
        keys.values.forEach { key -> key.addViewers(viewers) }
    }

    fun removeViewer(viewer: UUID) {
        keys.values.forEach { key -> key.removeViewer(viewer) }
    }

    fun removeViewers(viewers: List<UUID>) {
        keys.values.forEach { key -> key.removeViewers(viewers) }
    }

    fun remove() { keys.values.forEach { key -> key.remove() } }

    // yes these are hardcoded.. this was for a proof of concept >:)

    private fun createRotatedKey(keyText: String, initialOffset: Vector, isLarge: Boolean): Button {
        val yawRad = Math.toRadians(-location.yaw.toDouble())
        val pitchRad = Math.toRadians(location.pitch.toDouble())

        val rotatedOffset = initialOffset.clone()
            .rotateAroundX(pitchRad)
            .rotateAroundY(yawRad)

        val key = TextButton(location.clone().add(rotatedOffset), phantom) { player, button ->
            KeyboardKeyPressedEvent(player, keyText, UI_ID).callEvent()
            Animation.bounceAndChangeColor(button, 0.05F, 1, Material.GRAY_CONCRETE)
        }

        key.defaultTextScale = Vector3f(0.6F, 0.6F, 0.01F)
        key.defaultButtonScale = if (isLarge) Vector3f(0.4F, 0.25F, 0.01F) else Vector3f(0.25F, 0.25F, 0.01F)

        key.textElement?.apply {
            text = Component.text(keyText)
            translate(0F, .1F, 0F)
            if (isLarge) lineWidth = 100
            billboard = Display.Billboard.HORIZONTAL
        }
        key.buttonElement?.apply {
            baseItem = ItemStack(Material.LIGHT_GRAY_CONCRETE)
            billboard = Display.Billboard.HORIZONTAL
        }
        key.interaction.width = if (isLarge) 0.4F else 0.25F
        key.interaction.height = 0.33F
        key.interaction.teleport(key.interaction.location!!.clone().add(0.0, 0.0, -0.225))

        keys[keyText.uppercase()] = key
        return key
    }

    fun create(): SimpleKeyboard {
        val rowLengths = listOf(10, 9, 7)
        var rowIndex = 0
        var keyIndexInRow = 0

        characters.forEach { char ->
            if (keyIndexInRow >= rowLengths[rowIndex]) {
                rowIndex++
                keyIndexInRow = 0
            }

            val yOffset = -(rowIndex * 0.3)
            var xOffset = keyIndexInRow * 0.275
            if (rowIndex == 1) xOffset += 0.15
            if (rowIndex == 2) xOffset += 0.3

            val offset = Vector(xOffset, yOffset, 0.0)

            createRotatedKey(char.uppercase(), offset, false).apply {
                (this as TextButton).interaction.teleport(interaction.location!!.clone().add(0.0, 0.0, (rowIndex * 0.025)))
            }
            keyIndexInRow++
        }

        createRotatedKey(enter, Vector(-0.05, -0.6, 0.0), true).apply {
            (this as TextButton).defaultButtonScale = defaultButtonScale.add(Vector3f(0F, 0F, 0F))
            defaultTextScale = defaultTextScale.add(Vector3f(-0.1F, 0F, 0F))
            textElement?.translate(0.05F, 0F, 0F)
        }
        createRotatedKey(backspace, Vector((rowLengths[2] + 1.0) * 0.2875, -0.6, 0.0), true)

        return this
    }


}