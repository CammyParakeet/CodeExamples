package UITools

import PhantomManager
import java.util.*

/**
 * Simple Implementation of [Button]
 */

class TextButton(
    private var origin: Location,
    override val dataManager: PhantomManager?,
    private val onClick: (Player, Button) -> Unit,
) : Button {

    override val uniqueId: UUID = UUID.randomUUID()
    constructor(origin: Location, dataManager: PhantomManager?, onClick: (Player, Button) -> Unit, viewers: List<UUID>) : this(origin, dataManager, onClick) {
        addViewers(viewers)
    }

    val textElement = Element.create<Element.Text> {
        location = origin
        manager = dataManager
    }
    val buttonElement = Element.create<Element.Model> {
        location = origin
        manager = dataManager
    }
    val interaction = Hitbox(origin, dataManager).register()

    var defaultTextScale = Vector3f(0F)
        set(value) {
            if (field != value) {
                field = value
                textElement?.setScale(value)
            }
        }
    var defaultButtonScale = Vector3f(0F)
        set(value) {
            if (field != value) {
                field = value
                buttonElement?.setScale(value)
            }
        }

    init {
        dataManager?.let { Bukkit.getPluginManager().registerEvents(this, it.owningPlugin) }

        // template
        textElement?.apply {
            interpolationDelay = 0
            interpolationDurationTransform = 2
            setTranslate(-0.025F, 0F, 0.05F)
            defaultTextScale = setScale(1.25F)
            shadowed = true
            text = Component.text("Test Button")
            backgroundColor = Color.fromARGB(0)
        }
        buttonElement?.apply {
            interpolationDelay = 0
            interpolationDurationTransform = 2
            setTranslate(0F, 0.2F, 0F)
            defaultButtonScale = setScale(2F, .5F, 0.05F)
            baseItem = ItemStack(Material.GRAY_CONCRETE)
        }

        interaction.width = 1.5F
        interaction.height = .4F
    }

    override var scale: Float = 1.0F
        set(value) { if (field != value) {
            field = value
            if (value != 1.0F) {
                textElement?.setScale(defaultTextScale.x * value, defaultTextScale.y * value, defaultTextScale.z * value)
                buttonElement?.setScale(defaultButtonScale.x * value, defaultButtonScale.y * value, defaultButtonScale.z * value)
            } else {
                textElement?.setScale(defaultTextScale)
                buttonElement?.setScale(defaultButtonScale)
            }
        } }
    override val phantoms: List<Phantom>
        get() {
            val elements = mutableListOf<Phantom>()
            textElement?.let { elements.add(it) }
            buttonElement?.let { elements.add(it) }
            elements.add(interaction)
            return elements
        }

    override fun spawn() {
        textElement?.spawn()
        buttonElement?.spawn()
        interaction.spawn()
    }

    override fun addViewer(uuid: UUID) {
        textElement?.spawnFor(uuid)
        buttonElement?.spawnFor(uuid)
        interaction.spawnFor(uuid)
    }

    override fun addViewers(ids: List<UUID>) {
        ids.forEach { addViewer(it) }
    }

    override fun removeViewer(uuid: UUID) {
        textElement?.removeFor(uuid)
        buttonElement?.removeFor(uuid)
        interaction.removeFor(uuid)
    }

    override fun removeViewers(ids: List<UUID>) {
        ids.forEach { removeViewer(it) }
    }

    override fun remove() {
        textElement?.remove()
        buttonElement?.remove()
        interaction.remove()
    }

    override fun scale(sX: Float, sY: Float, sZ: Float) {
        TODO("Not yet implemented")
    }

    /**
     * Warning - does not move the interaction
     */
    override fun translate(dX: Float, dY: Float, dZ: Float) {
        buttonElement?.translate(dX, dY, dZ)
        textElement?.translate(dX, dY, dZ)
    }

    override fun rotate(angle: Float) {
        TODO("Not yet implemented")
    }

    fun getButtonText(): String {
        return (textElement?.text as TextComponent).content()
    }

    @EventHandler
    fun onPlayerClick(event: PlayerInteractPhantomHitbox) {
        if (event.phantom.entityId == interaction.entityId) {
            onClick.invoke(event.player, this)
        }
    }

}