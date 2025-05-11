package gg.shaded.core.bukkit.config.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import kotlin.reflect.KClass

/**
 * Event triggered when a config is loaded
 *
 * @param T The type of the configuration class
 * @property configClass The [KClass] of the reloaded config
 * @property configInstance The instance of the reloaded config object, populated with updated values
 */
open class ConfigLoadEvent<T : Any>(
    val configClass: KClass<T>,
    val configInstance: T
) : Event() {

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlerList
        }
    }

    override fun getHandlers(): HandlerList {
        return handlerList
    }


}