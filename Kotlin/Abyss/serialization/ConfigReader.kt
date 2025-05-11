package gg.shaded.core.bukkit.config.serialization

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.serialization.ConfigurationSerializable
import kotlin.reflect.*
import kotlin.reflect.full.isSubclassOf

object ConfigReader {

    /**
     * Fetches the value from the given config section at the given path
     *
     * Handles deserialization for complex objects, including [ConfigSerializable] & [ConfigurationSerializable] objects.
     * Falls back to the default value if deserialization
     * fails or the raw value is incompatible with the expected type.
     *
     * #### Logic:
     * 1. If a custom deserialization transformer is provided via `deserialize`:
     *    - The transformer is invoked with the raw value retrieved from the config.
     *    - If the transformer fails, it logs the error and returns `null`.
     * 2. If the default value is a list and the raw value is also a list:
     *    - Attempts to deserialize each item in the list if the items implement `ConfigSerializable`
     * 3. If the default value is a single `ConfigSerializable` object:
     *    - Attempts to deserialize the raw value into the default value's class
     * 4. If the raw value matches the type of the default value:
     *    - Directly casts and returns the raw value
     * 5. Otherwise:
     *    - Returns the default value
     *
     * @param configSection The yaml section to fetch the value from
     * @param path The path within the yaml to fetch the value from
     * @return The deserialized or raw value, or the default value if it fails
     * @throws ClassCastException If the raw value cannot be cast to the expected type
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fetchValue(
        configSection: ConfigurationSection,
        path: String,
        returnType: KType,
        genericTypeMap: Map<KTypeParameter, KTypeProjection> = emptyMap(),
        deserialize: ((Any?) -> T?)? = null
    ): T? {
        if (!configSection.contains(path)) return null

        // If a custom transformer is supplied - attempt it
        deserialize?.let {
            try {
                return it.invoke(configSection.get(path))
            } catch (e: Exception) {
                println("Failed custom deserializer transform - cause: ${e.message}")
                return null
            }
        }

        val expectedClass = returnType.classifier as? KClass<*>
            ?: throw NullPointerException("Failed to detect expected class for path: $path | return type: $returnType")

        return when {
            expectedClass == Boolean::class -> configSection.getBoolean(path) as? T
            expectedClass == Byte::class -> configSection.getInt(path).toByte() as? T
            expectedClass == Short::class -> configSection.getInt(path).toShort() as? T
            expectedClass == Int::class -> configSection.getInt(path) as? T
            expectedClass == Double::class -> configSection.getDouble(path) as? T
            expectedClass == Float::class -> configSection.getDouble(path).toFloat() as? T
            expectedClass == Long::class -> configSection.getLong(path) as? T
            expectedClass == String::class -> configSection.getString(path) as? T
            expectedClass == Char::class -> {
                val rawVal = configSection.getString(path)
                if (rawVal?.length == 1) rawVal.first() as? T else null
            }

            // Handle Enums
            expectedClass.java.isEnum -> handleEnum(rawValue = configSection.get(path), expectedClass) as? T

            // Handle Bukkit Serializable
            ConfigurationSerializable::class.java.isAssignableFrom(expectedClass.java) -> {
                configSection.get(path) as? T
            }

            // Handle ConfigSerializable
            ConfigSerializable::class.java.isAssignableFrom(expectedClass.java) -> {
                return ConfigSerializable.deserialize(rawValue = configSection.get(path), returnType, expectedClass) as? T
            }

            // Handle Collections
            expectedClass.isSubclassOf(Collection::class) -> {
                handleCollection(configSection, path, returnType, genericTypeMap) as? T
            }

            else -> error("Unsupported type for path: $path - $expectedClass")
        }
    }

    /**
     * Handles the deserialization of collections from a [ConfigurationSection]
     *
     * Supports both primitive and complex elements in the collection. Primitive elements
     * are directly retrieved using Bukkit's `getList` methods, while complex elements
     * (eg, [ConfigSerializable] or [ConfigurationSerializable] objects) are recursively
     * deserialized using appropriate logic.
     *
     * #### Supported Collection Types:
     * - [List]
     * - [Set]
     *
     * @param configSection The configuration section containing the serialized collection
     * @param path The path within the config section to fetch the collection
     * @param returnType The expected type of the collection, including the element type
     * @return The deserialized collection, or an empty collection if deserialization fails
     * @throws Exception If the collection cannot be retrieved or deserialized
     */
    private fun handleCollection(
        configSection: ConfigurationSection,
        path: String,
        returnType: KType,
        genericTypeMap: Map<KTypeParameter, KTypeProjection> = emptyMap()
    ): Collection<Any> {
        val expectedClass = returnType.classifier as? KClass<*>
            ?: error("Expected type classifier is null for $returnType")

        val elementType = returnType.arguments.firstOrNull()?.type
            ?: error("Element type is null for collection: $returnType")

        val resolvedElementType = resolveGenericType(elementType, genericTypeMap)

        val deserializedCollection = when (resolvedElementType.classifier) {
            // Primitive elements
            Boolean::class -> configSection.getBooleanList(path)
            Byte::class -> configSection.getByteList(path)
            Short::class -> configSection.getShortList(path)
            Char::class -> configSection.getCharacterList(path)
            Int::class -> configSection.getIntegerList(path)
            Double::class -> configSection.getDoubleList(path)
            Float::class -> configSection.getFloatList(path)
            Long::class -> configSection.getLongList(path)
            String::class -> configSection.getStringList(path)

            // Complex elements
            else -> {
                val rawList = configSection.get(path) as? Collection<*> ?: return emptyList()
                rawList.mapNotNull { item ->
                    ConfigSerializable.deserializeValue(item, resolvedElementType)
                }
            }
        }

        return when (expectedClass) {
            List::class -> deserializedCollection.toList()
            Set::class -> deserializedCollection.toSet()
            else -> error("Unsupported collection type: $returnType")
        }
    }

    /**
     * Safely handles deserialization of an enum return type
     */
    fun handleEnum(rawValue: Any?, expectedClass: KClass<*>): Any {
        val rawString = rawValue as? String
            ?: error("Expected a String for enum type $expectedClass but got ${rawValue?.javaClass?.kotlin}")
        return expectedClass.java.enumConstants.find { (it as Enum<*>).name.uppercase() == rawString.uppercase() }
            ?: error("Invalid value '$rawString' for enum type $expectedClass")
    }

}