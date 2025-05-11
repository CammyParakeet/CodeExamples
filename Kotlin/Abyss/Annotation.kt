package gg.shaded.core.bukkit.config

/**
 * Annotation for marking a class as a YAML config
 *
 * This annotation is used to specify the file name for the config
 * The file name should not include the `.yml` extension, as it will be appended automatically
 *
 * @property fileName The base name of the YAML config file (without the `.yml` extension)
 * @property expirationMillis The duration in millis until this config will be expired from the loader,
 * less than 1 is treated as infinite
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class YamlConfig(
    val fileName: String,
    val expirationMillis: Long = -1 // -1 = no expiration
)

/**
 * Annotation for marking a class as a section within a YAML config file
 *
 * This allows a specific section of a config file to be managed independently
 *
 * @property parentFile The base name of the YAML configuration file (without the `.yml` extension)
 * @property sectionPath The path to the section within the YAML file
 * @property expirationMillis The duration in millis until this config will be expired from the loader,
 * less than 1 is treated as infinite
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigSection(
    val parentFile: String,
    val sectionPath: String,
    val expirationMillis: Long = -1 // -1 = no expiration
)

/**
 * Annotation for defining config paths and comments
 *
 * Used to map a property in the config class to a specific path
 * in the YAML file. An optional comment can be provided to document the purpose of the
 * config value. Comments will be included in the YAML next to the corresponding key.
 *
 * @property path The path in the YAML file where this config value will be stored or retrieved
 * @property comments Optional comments describing the purpose of this config value
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigPath(
    val path: String,
    val comments: Array<String> = []
)

/**
 * Annotation to mark a property as a config field for dynamic serialization
 *
 * Used with the `ConfigSerializable` interface to identify
 * which fields of a class should be included in the serialization process
 *
 * Inline comments can be added to the serialized YAML output using the `comments` parameter
 *
 * #### Usage:
 * ```kotlin
 * data class ExampleDTO(
 *     @ConfigField(comments = ["Name of the user"]) val name: String,
 *     @ConfigField(comments = ["Users age", "Must be an integer"]) val age: Int,
 *     val ignoredField: String = "Ignored by serialization, must be optional or nullable"
 * ) : ConfigSerializable
 * ```
 * Serialized output:
 * ```yaml
 *  example:
 *    name: Cammy  # Name of the user
 *    age: 24  # Users age
 *             # Must be an integer
 *  ```
 *
 * #### Restrictions:
 *  - Should only be applied to properties of classes that implement `ConfigSerializable`
 *  - Fields without this annotation will be ignored during serialization and deserialization,
 *  however must be nullable or optional (include a default for construction)
 *  ```kotlin
 *  data class ExampleDTO(
 *     @ConfigField(comments = ["Name of the user"]) val name: String,
 *     val otherField: String // This will fail to deserialize (cannot construct the instance)
 * ) : ConfigSerializable
 * ```
 *  - Comments are only applied when the property is directly serialized as part of a standalone `ConfigSerializable` object
 *  - Comments are ignored for nested objects or lists to avoid excessive verbosity in YAML output
 * ```kotlin
 * data class ParentDTO(
 *     @ConfigField(comments = ["This is a nested object"]) val child: ChildDTO,
 *     @ConfigField(comments = ["This is a list of nested objects"]) val children: List<ChildDTO>
 * ) : ConfigSerializable
 *
 * data class ChildDTO(
 *     @ConfigField(comments = ["Child comment!"]) val someField: String
 * ) : ConfigSerializable
 * ```
 * Serialized output: ("Child comment!" will not show up when the parent is written)
 * ```yaml
 * parent:
 *   child: # This is a nested object
 *     someField: value
 *   children: # This is a list of nested objects
 *     - someField: value1
 *     - someField: value2
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigField(val order: Int = Int.MAX_VALUE, val comments: Array<String> = [])