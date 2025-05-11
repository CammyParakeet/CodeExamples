package gg.shaded.core.bukkit.config.serialization

import gg.shaded.core.bukkit.config.ConfigField
import gg.shaded.core.bukkit.config.serialization.ConfigReader.fetchValue
import gg.shaded.core.bukkit.config.serialization.ConfigReader.handleEnum
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.configuration.serialization.ConfigurationSerialization
import kotlin.reflect.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Interface for dynamically serializing and deserializing config objects
 *
 * Classes implementing this interface can be automatically serialized into a map representation
 * and deserialized back into objects, using properties annotated with [ConfigField]
 *
 * Is a dynamic improvement on [org.bukkit.configuration.serialization.ConfigurationSerializable]
 */
interface ConfigSerializable {

    /**
     * Serializes the implementing object into a map representation
     *
     * Only properties annotated with [ConfigField] will be included in the serialized output
     *
     * @return A map containing serialized properties of the object
     */
    fun serialize(): Map<String, Any> {
        return this::class.memberProperties
            .filter { it.findAnnotation<ConfigField>() != null }
            .sortedBy { it.findAnnotation<ConfigField>()?.order ?: Int.MAX_VALUE }
            .mapNotNull { property -> // Use mapNotNull to skip null values
                property.isAccessible = true
                val value = property.getter.call(this)?.toConfigCompatible() // Get and transform the value
                if (value != null) property.name to value else null // Skip if value is null
            }
            .toMap()
    }

    companion object {

        /**
         * Deserializes a raw value into an instance of the specified class
         *
         * Handles deserialization of both simple and complex objects, supporting generic type parameters.
         * Uses the primary constructor of the target class to reconstruct the object, mapping the provided
         * raw data to the constructor parameters.
         *
         * #### Supported Raw Value Types:
         * - `ConfigurationSection`: Recursively fetches and deserializes values from a YAML config section
         * - `Map<*, *>`: Maps raw key-value pairs to constructor parameters
         *
         * @param rawValue The raw value to deserialize. Can be a `ConfigurationSection` or `Map<*, *>`
         * @param returnType The full Kotlin type of the target object, including generics
         * @param expectedClass The class of the target object
         * @return The deserialized object or `null` if deserialization fails
         * @throws IllegalArgumentException If type validation fails
         */
        fun deserialize(
            rawValue: Any?,
            returnType: KType,
            expectedClass: KClass<*>
        ): Any? {
            if (rawValue == null) return null

            val genericTypeMap = if (expectedClass.typeParameters.isNotEmpty()) {
                expectedClass.typeParameters.zip(returnType.arguments).toMap()
            } else emptyMap()

            // Validate types as supported by this system
            for ((typeParam, typeProjection) in genericTypeMap) {
                val typeArg = typeProjection.type
                if (typeArg == null || !isTypeSupported(typeArg)) {
                    error("Unsupported type argument: ${typeParam.name} in ${expectedClass.simpleName}")
                }
            }

            val constructor = expectedClass.primaryConstructor
                ?: error("No primary constructor found for ${expectedClass.simpleName}")

            val params = when (rawValue) {
                is ConfigurationSection -> mapConstructorParams(rawValue, constructor, genericTypeMap)
                is Map<*, *> -> mapConstructorParams(rawValue, constructor, genericTypeMap)
                else -> error("Invalid item type for ConfigSerializable: $rawValue")
            }.filterValues { it != null }

            return try {
                val constructed = constructor.callBy(params)
                constructed
            } catch (e: Exception) {
                println("Failed to deserialize ${expectedClass.simpleName}: ${e.message}")
                null
            }
        }

        /**
         * Maps raw data to the parameters of a class's primary constructor
         *
         * Handles recursive deserialization of constructor parameters, including resolving generic types
         * and handling supported raw value types
         *
         * #### Logic:
         * 1. Resolves each constructor parameter type using `resolveGenericType` and `genericTypeMap`
         * 2. Fetches the raw value from the source for each parameter
         * 3. Deserializes the raw value into the expected type
         *
         * @param source The source of the raw data. Can be a `ConfigurationSection` or `Map<*, *>`
         * @param constructor The primary constructor of the target class
         * @param genericTypeMap A map of generic type parameters to their resolved types
         * @return A map of constructor parameters to their deserialized values
         * @throws IllegalArgumentException If a required parameter is missing or deserialization fails
         */
        private fun mapConstructorParams(
            source: Any,
            constructor: KFunction<*>,
            genericTypeMap: Map<KTypeParameter, KTypeProjection>
        ) : Map<KParameter, Any?> {
            return try {
                constructor.parameters.associateWith { param ->
                    val resolvedType = if (genericTypeMap.isNotEmpty()) {
                        resolveGenericType(param.type, genericTypeMap)
                    } else param.type

                    val paramName = param.name ?: error("Unnamed parameter in ${constructor.returnType}")

                    when (source) {
                        is ConfigurationSection -> fetchValue(source, paramName, resolvedType, genericTypeMap)
                        is Map<*, *> -> deserializeValue(source[paramName], resolvedType, param.isOptional, genericTypeMap)
                        else -> error("Unsupported source type for parameter mapping: ${source::class}")
                    }
                }
            } catch (e: Exception) {
                println("Failed to map constructor parameters from ${constructor.name} for ${constructor.returnType} - source: $source")
                emptyMap()
            }
        }

        /**
         * Determines whether a given type is supported for deserialization
         *
         * #### Supported Types:
         * - Primitives
         * - Enums
         * - Collections with supported element types
         * - Classes implementing `ConfigSerializable` or `ConfigurationSerializable`
         *
         * @param type The `KType` to check
         * @return `true` if the type is supported; `false` otherwise
         */
        private fun isTypeSupported(type: KType): Boolean {
            val expectedClass = type.classifier as? KClass<*> ?: return false

            return when {
                // Handle primitives types
                expectedClass == Int::class || expectedClass == Double::class ||
                        expectedClass == Float::class || expectedClass == Long::class ||
                        expectedClass == Byte::class || expectedClass == Short::class -> true

                // Handle Java's Number class and subclasses
                Number::class.java.isAssignableFrom(expectedClass.java) -> true

                // Handle other supported types
                expectedClass == Boolean::class -> true
                expectedClass == String::class -> true
                expectedClass == Char::class -> true
                expectedClass.java.isEnum -> true
                ConfigurationSerializable::class.java.isAssignableFrom(expectedClass.java) -> true
                ConfigSerializable::class.java.isAssignableFrom(expectedClass.java) -> true
                expectedClass.isSubclassOf(Collection::class) -> {
                    val elementType = type.arguments.firstOrNull()?.type
                    elementType != null && isTypeSupported(elementType)
                }
                else -> false
            }
        }

        /**
         * Deserializes a raw value into a specific type
         *
         * Handles conversions for various types and ensures
         * compatibility with nullable and optional fields. Supports deserialization for [ConfigurationSerializable]
         * and custom serializable objects marked with [ConfigSerializable]
         *
         * @param rawValue The raw value to deserialize
         * @param expectedType The type to deserialize the value into
         * @param isOptional Whether the field being deserialized is optional
         * @return The deserialized value, or null if the value is null and optional/nullable
         * @throws IllegalArgumentException If the raw value is incompatible with the expected type
         */
        @Suppress("UNCHECKED_CAST")
        fun deserializeValue(
            rawValue: Any?,
            expectedType: KType,
            isOptional: Boolean = false,
            genericTypeMap: Map<KTypeParameter, KTypeProjection> = emptyMap()
        ) : Any? {
            val expectedClass = expectedType.classifier as? KClass<*>
                ?: error("Expected type classifier is null for $expectedType")

            return when {
                rawValue == null && (isOptional || expectedType.isMarkedNullable) -> return null
                rawValue == null -> error("Missing required param for $expectedClass - type: $expectedType")

                // Dynamic enum handling
                expectedClass.java.isEnum -> handleEnum(rawValue, expectedClass)

                expectedClass == Int::class -> (rawValue as? Number)?.toInt()
                expectedClass == Double::class -> (rawValue as? Number)?.toDouble()
                expectedClass == Float::class -> (rawValue as? Number)?.toFloat()
                expectedClass == Long::class -> (rawValue as? Number)?.toLong()
                expectedClass == Byte::class -> (rawValue as? Number)?.toByte()
                expectedClass == Short::class -> (rawValue as? Number)?.toShort()
                expectedClass == Char::class -> {
                    when (rawValue) {
                        is Char -> rawValue
                        is String -> rawValue.singleOrNull()
                        else -> error("Cannot deserialize value $rawValue as Char")
                    }
                }
                expectedClass == Boolean::class -> {
                    when (rawValue) {
                        is Boolean -> rawValue
                        is String -> {
                            val normalized = rawValue.lowercase()
                            normalized == "true"
                        }
                        is Number -> rawValue.toInt() != 0
                        else -> false
                    }
                }

                // Exact matches
                expectedClass.java.isInstance(rawValue) -> rawValue

                // Bukkit Objects
                ConfigurationSerializable::class.java.isAssignableFrom(expectedClass.java) -> {
                    ConfigurationSerialization.deserializeObject(rawValue as Map<String, Any>)
                }

                // Config Serializable Objects
                ConfigSerializable::class.java.isAssignableFrom(expectedClass.java) -> {
                    deserialize(rawValue, expectedType, expectedClass)
                }

                // Collection Objects
                expectedClass.isSubclassOf(Collection::class) -> {
                    val collection = rawValue as? Collection<*>
                        ?: error("Cannot retrieve collection from rawValue: $rawValue")
                    val elementType = expectedType.arguments.firstOrNull()?.type
                        ?: error("Cannot determine element type for list - rawValue: $rawValue")

                    when (expectedClass) {
                        List::class -> {
                            deserializeCollection<Any, List<Any?>>(collection, elementType, genericTypeMap) { list -> list.toList() }
                        }
                        Set::class -> {
                            deserializeCollection<Any, Set<Any?>>(collection, elementType, genericTypeMap) { list -> list.toSet() }
                        }
                        else -> error("Unsupported collection type: $expectedClass")
                    }
                }

                else -> error("Unhandled value for ${expectedClass.simpleName}: $rawValue as ${rawValue.javaClass.kotlin}")
            }
        }


        /**
         * Deserializes a collection of elements into a specified target collection type
         *
         * Supports recursive deserialization for complex elements (eg, [ConfigSerializable]
         * or [ConfigurationSerializable] objects) and converts the collection into the correct
         * type using the provided `targetFactory`.
         *
         * @param rawCollection The raw collection to be deserialized
         * @param elementType The expected type of the elements in the collection
         * @param targetFactory A factory function to convert the deserialized elements into the target collection type
         * @return The deserialized collection or an empty collection if the raw collection is null
         * @throws Exception If any element in the collection fails to deserialize
         */
        private fun <T : Any, C : Collection<Any?>> deserializeCollection(
            rawCollection: Collection<*>?,
            elementType: KType,
            genericTypeMap: Map<KTypeParameter, KTypeProjection> = emptyMap(),
            targetFactory: (Collection<Any?>) -> C,
        ): C {
            if (rawCollection == null) return targetFactory(emptyList())

            val resolvedElementType = resolveGenericType(elementType, genericTypeMap)

            val deserializedElements = rawCollection.mapNotNull { item ->
                try {
                    deserializeValue(item, resolvedElementType)
                } catch (e: Exception) {
                    println("Failed to deserialized collection element: ${e.message}")
                    null
                }
            }

            return targetFactory(deserializedElements)
        }

        /**
         * Converts an object into a config-compatible format
         *
         * Supported types include:
         * - [ConfigSerializable]: Serialized into a map
         * - Other types: Returned as-is
         *
         * @return The config-compatible representation of the object
         */
        fun Any?.toConfigCompatible(): Any? {
            return when (this) {
                null -> null
                is ConfigSerializable -> this.serialize()
                is Collection<*> -> this.toList().map { it.toConfigCompatible() } // collections
                is Map<*, *> -> this.mapKeys { it.key.toString() }.mapValues { it.value.toConfigCompatible() } // maps
                is Char -> this.toString()
                is Enum<*> -> this.name
                else -> this
            }
        }

    }

}