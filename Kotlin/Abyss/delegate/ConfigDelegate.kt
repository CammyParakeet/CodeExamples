package gg.shaded.core.bukkit.config.delegate

import gg.shaded.core.bukkit.config.ConfigField
import gg.shaded.core.bukkit.config.ConfigLoader
import gg.shaded.core.bukkit.config.ConfigPath
import gg.shaded.core.bukkit.config.serialization.ConfigSerializable
import gg.shaded.core.bukkit.config.serialization.ConfigReader.fetchValue
import gg.shaded.core.bukkit.config.serialization.ConfigSerializable.Companion.toConfigCompatible
import gg.shaded.core.util.data.Cached
import org.bukkit.configuration.ConfigurationSection
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * A delegate for managing config properties within annotated classes
 *
 * This delegate dynamically retrieves config values from the loaded config data
 * in [ConfigLoader]. Supports default values, validation, and caching for efficient property access.
 * If the config key is not found, the provided default value is returned
 *
 * **Usage:**
 * Properties using this delegate must be annotated with [ConfigPath], specifying the config path
 * Example:
 * ```
 * @ConfigPath("test.default", comments = ["Example property with default"])
 * val defaultingString: String by ConfigDelegate("DefaultValue")
 * ```
 *
 * **Key Features:**
 * - **Default Values:** Automatically returns a default value when a config key is missing.
 * - **Caching:** Caches fetched values for efficient repeated access.
 * - **Validation:** Supports optional validation logic via a [validator] function to enforce constraints.
 * - **Annotation-Based Pathing:** Requires the [ConfigPath] annotation to determine the config key.
 * - **Transformers:** Supports custom transformations for reading and writing config values.
 *    - `serialize`: A function that transforms the property value (`T`) into a format suitable for storage in the config file.
 *    - `deserialize`: A function that transforms the raw value from the config file into the expected property type (`T`).
 *    These transformers enable seamless handling of complex data types and custom formats.
 *
 * **Requirements:**
 * - Properties using this delegate must have a default value provided during initialization
 * - The associated config class must have its data loaded through [ConfigLoader]
 *
 * @param T The type of the config property
 * @param defaultValue The default value to use if the config key is not found
 * @param serialize Optional transformer to serialize more complex data
 * @param deserialize Optional transformer to deserialize more complex data
 * @param validator An optional lambda function to validate the retrieved value. The lambda 'should' throw an exception if the value is invalid
 * @throws IllegalArgumentException If the property does not have a [ConfigPath] annotation or fails validation
 * @throws IllegalStateException If the config data for the class is not loaded
 */
open class ConfigDelegate<T : Any>(
    private val defaultValue: T,
    private val serialize: ((T) -> Any?)? = null,
    private val deserialize: ((Any?) -> T?)? = null,
    private val validator: ((T) -> Unit)? = null
): ReadOnlyProperty<Any, T>, Cached<T> {

    override var cachedValue: T? = null
    override var isCached = false


    /**
     * Saves the given value to the config section at the given path
     *
     * Handles serialization for complex objects, including lists of `ConfigSerializable` items
     * and single `ConfigSerializable` objects. Ensures that all values written to the config
     * are compatible with YAML serialization
     *
     * #### Logic:
     * 1. Value is a list:
     *    - Serializes each item in the list if it implements `ConfigSerializable`
     * 2. Value is a single `ConfigSerializable` object:
     *    - Serializes the object into a map representation, writes each element directly to support comments
     * 3. Otherwise:
     *    - Writes the raw value directly
     *
     * @param configSection The yaml section to save the value to
     * @param path The path within the section to save the value at
     * @param value The value to save, which can be a primitive,
     *  a list, Bukkit Serializable ([org.bukkit.configuration.serialization.ConfigurationSerializable]),
     *  or a `ConfigSerializable` object
     */
    open fun saveValue(configSection: ConfigurationSection, path: String, value: T) {
        when (val readyValue = serialize?.invoke(value) ?: value) {
            is ConfigSerializable -> {
                readyValue.serialize().forEach { (key, serializedValue) ->
                    val fullPath = "$path.$key"
                    configSection.set(fullPath, serializedValue)

                    // Apply any comments
                    val property = value::class.memberProperties.firstOrNull { it.name == key }
                    val comments = property?.findAnnotation<ConfigField>()?.comments
                    if (comments?.isNotEmpty() == true) {
                        configSection.setInlineComments(fullPath, comments.asList())
                    }
                }
            }

            else -> {
                configSection.set(path, value.toConfigCompatible())
            }
        }
    }

    /**
     * Retrieves the value for the delegated config property
     *
     * @throws IllegalArgumentException If the property lacks the [ConfigPath] annotation
     * @throws IllegalStateException If the config data for the class is not loaded
     * @throws IllegalArgumentException If the retrieved value fails validation or type checks
     */
    override operator fun getValue(thisRef: Any, property: KProperty<*>): T {
        // return cached value if available
        if (isCached) return cachedValue!!

        val configClass = thisRef::class
        val configSection = ConfigLoader.getConfig(configClass)
            ?: ConfigLoader.getConfigSection(configClass)
            ?: error("Config or section for class ${configClass.simpleName} is not loaded")

        val annotation = property.annotations.find { it is ConfigPath } as? ConfigPath
            ?: error("Property ${property.name} must has @ConfigPath annotation to use ConfigDelegate")

        val value = try {
            fetchValue(configSection, annotation.path, property.returnType, deserialize = deserialize) ?: defaultValue
        } catch (e: Exception) {
            println("Failed to fetch ${property.name}: ${e.message}")
            defaultValue
        }

        validator?.invoke(value)
        cache(value)

        return value
    }

    /**
     * Gets the default value for this delegates property
     */
    open fun getDefaultValue(): T {
        return defaultValue
    }

}