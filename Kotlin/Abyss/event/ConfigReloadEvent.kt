package gg.shaded.core.bukkit.config.event

import org.bukkit.event.HandlerList
import kotlin.reflect.KClass

/**
 * Event triggered when a config is reloaded
 *
 * This event is fired whenever a config class is reloaded.
 * Provides access to the reloaded config class and its instance, allowing listeners
 * to react to changes in the config at runtime.
 *
 * @param T The type of the configuration class
 * @property configClass The [KClass] of the reloaded config
 * @property configInstance The instance of the reloaded config object, populated with updated values
 */
class ConfigReloadEvent<T : Any>(
    configClass: KClass<T>,
    configInstance: T
) : ConfigLoadEvent<T>(configClass, configInstance) {

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