package gg.shaded.core.bukkit.config.delegate

import gg.shaded.core.bukkit.config.ConfigLoader
import gg.shaded.core.bukkit.config.ConfigPath
import gg.shaded.core.bukkit.config.serialization.ConfigReader.fetchValue
import gg.shaded.core.util.data.Cached
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A delegate for reading config properties with support for caching, validation, and annotations
 *
 * This delegate fetches config values based on the provided [ConfigPath] annotation
 * and validates them if a [validator] is supplied. It also caches the fetched value for
 * subsequent access, improving performance for frequently accessed properties
 *
 * **Usage:**
 * Properties using this delegate must be annotated with [ConfigPath], specifying the config path
 * Example:
 * ```
 * @ConfigPath("test.no-default", comments = ["Example no-default strings"])
 * val noDefaultList: List<String>? by ConfigReadDelegate()
 * ```
 *
 * **Key Features:**
 * - **Caching:** Once a value is fetched, it is cached for faster access
 * - **Validation:** Supports optional validation logic via a [validator] function
 * - **Annotation-Based Pathing:** Requires the [ConfigPath] annotation to locate the config value
 * - **Transformer:** Supports a custom transformation for reading config values.
 *    - `deserialize`: A function that transforms the raw value from the config file into the expected property type (`T`).
 *    These transformers enable seamless handling of complex data types and custom formats.
 *
 * **Requirements:**
 * - Properties using this delegate must allow nullable return values
 * - The associated config class must have its data loaded through [ConfigLoader]
 *
 * @param T The type of the config property. Allows nullable returns
 * * @param deserialize Optional transformer to deserialize more complex data
 * @param validator An optional validation function to check the retrieved value
 * @throws IllegalArgumentException If the property does not have a [ConfigPath] annotation or fails validation
 * @throws IllegalStateException If the config data for the class is not loaded
 */
open class ConfigReadDelegate<T : Any>(
    private val deserialize: ((Any?) -> T?)? = null,
    private val validator: ((T?) -> Unit)? = null
) : ReadOnlyProperty<Any, T?>, Cached<T> {

    override var cachedValue: T? = null
    override var isCached = false

    /**
     * Retrieves the value for the delegated config property
     *
     * @throws IllegalArgumentException If the property lacks the [ConfigPath] annotation
     * @throws IllegalStateException If the config data for the class is not loaded
     * @throws IllegalArgumentException If the retrieved value fails validation or type checks
     */
    override operator fun getValue(thisRef: Any, property: KProperty<*>): T? {
        // return cached value if available
        if (isCached) {
            return cachedValue!!
        }

        val configClass = thisRef::class
        val configSection = ConfigLoader.getConfig(configClass)
            ?: ConfigLoader.getConfigSection(configClass)
            ?: error("Config or section for class ${configClass.simpleName} is not loaded")

        val annotation = property.annotations.find { it is ConfigPath } as? ConfigPath
            ?: error("Property ${property.name} must has @ConfigPath annotation to use ConfigReadDelegate")

        val value = try {
            fetchValue(configSection, annotation.path, property.returnType, deserialize = deserialize)
        } catch (e: Exception) {
            println("Failed to fetch ${property.name}: ${e.message}")
            null
        }

        validator?.invoke(value)
        value?.let { cache(it) }

        return value
    }


}