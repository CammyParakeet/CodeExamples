package gg.shaded.core.bukkit.config.serialization

import kotlin.reflect.*

/**
 * Resolves a generic type into its concrete type based on a provided generic type map
 *
 * If the given type is a type parameter, resolves it to its corresponding
 * concrete type using the `genericTypeMap`. Recursively resolves generic arguments for
 * parameterized types
 *
 * #### Logic:
 * 1. Checks if the type is a `KTypeParameter` and resolves it using the `genericTypeMap`
 * 2. Recursively resolves the arguments of parameterized types
 * 3. Constructs a new `KType` with resolved arguments using `createParameterizedType`
 *
 * @param usageType The generic type to resolve
 * @param genericTypeMap A map of generic type parameters to their concrete types
 * @return The resolved `KType`
 * @throws IllegalArgumentException If the type cannot be resolved
 */
fun resolveGenericType(
    usageType: KType?,
    genericTypeMap: Map<KTypeParameter, KTypeProjection>
): KType {
    if (usageType == null) error("")

    val typeClassifier = usageType.classifier
    if (typeClassifier is KTypeParameter) {
        return genericTypeMap[typeClassifier]?.type ?: usageType
    }

    val resolvedArguments = usageType.arguments.map { arg ->
        val resolvedType = arg.type?.let { resolveGenericType(it, genericTypeMap) }
        KTypeProjection(arg.variance, resolvedType)
    }

    return createParameterizedType(typeClassifier, resolvedArguments)
}

/**
 * Creates a new `KType` instance with the specified classifier and arguments
 *
 * @param classifier The classifier for the type (eg, `List::class` for `List<T>`)
 * @param arguments The list of resolved `KTypeProjection` representing the type's arguments
 * @return A new `KType` instance representing the parameterized type
 */
fun createParameterizedType(
    classifier: KClassifier?,
    arguments: List<KTypeProjection>
): KType {
    return object : KType {
        override val annotations: List<Annotation> = emptyList()
        override val classifier: KClassifier? = classifier
        override val arguments: List<KTypeProjection> = arguments
        override val isMarkedNullable: Boolean = false
    }
}