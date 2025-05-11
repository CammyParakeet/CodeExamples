# Annotated Based YAML Serialization System (ABYSS) - Kotlin

Introduces an annotated config system for YAML files in the plugins. Leverages Kotlin's reflection and Bukkits `YamlConfiguration` API to provide a simple, 
flexible, and fast management solution.
Latest changes introduce significant enhancements.

## Key Updates

### New Features

1. **Section-Based Config Loading**:
    - Use `@ConfigSection` to manage individual YAML sections as independent objects
    - Supports loading and managing parts of a single YAML file across multiple classes
    - Ensures minimal in-memory footprint while handling large YAML files

2. **Improved Default Handling**:
    - Automatically writes missing defaults for both full configs and sectional configs
    - Ensures newly created sections conform to expected defaults and structure

3. **Synchronized YAML Updates**:
    - Ensures that any missing sections or keys are dynamically written back to the YAML file during load

4. **Enhanced Deserialization**:
    - Supports advanced type handling for collections (`List`, `Set`), complex objects (`ConfigSerializable`), and nested configurations
    - Handles edge cases such as `Char`
    - All serialized and deserialized dynamically

5. **Unified Config Access**:
    - `ConfigLoader.getConfig` and `ConfigLoader.getConfigSection` provide consistent access to loaded configs and sections

6. **Modular Structure**:
    - `populateConfigObject` ensures shared logic for handling both full configs and sectional configs, reducing redundancy

---

### ConfigReadDelegate
The `ConfigReadDelegate` is a **new** read-only delegate for config properties. Unlike `ConfigDelegate`, it does not require default values. 
It is useful for cases where values are assumed to be present in the config file and should not have/need fallback defaults. 
The return type of values using a ConfigReadDelegate should be nullable.

#### Usage Example:
```kotlin
@YamlConfig("example")
object ReadOnlyConfig {

    @ConfigPath("readonly.value", ["This value is read-only and has no default"])
    val readOnlyValue: String? by ConfigReadDelegate()
}
```

Generated YAML (expected to already exist - otherwise the comment & key does not write):
```yaml
readonly:
  value: This is a read-only value  # This value is read-only and has no default
```

#### Key Differences:
- **`ConfigDelegate`**: Requires a default value and writes it to the YAML if missing
- **`ConfigReadDelegate`**: Does not require a default value and returns null if the value is missing in the YAML file

#### Access Behavior:
```kotlin
class SomePlugin : JavaPlugin() {
    override fun onEnable() {
        ConfigLoader.loadConfig(this, ReadOnlyConfig::class)

        // Access read-only value
        val value = ReadOnlyConfig.readOnlyValue
        logger.info("Read-only value: $value") // value is nullable
    }
}
```

If the `readonly.value` key is not present in the YAML file, it is returned as null. 
The key and comments will not be written

---

### `@ConfigField`

The `@ConfigField` annotation is used for dynamic serialization and deserialization of custom objects.
Properties annotated with `@ConfigField` are included in YAML serialization when the object implements `ConfigSerializable`.
Comments can be added for each field, which are written into the YAML alongside their values.

#### Usage Example:
```kotlin
data class ExampleDTO(
@ConfigField(comments = ["Test comment on object"]) val one: Int,
@ConfigField(comments = ["Test 2", "Second line on test comment"]) val two: Float,
@ConfigField val three: String,
@ConfigField val four: Byte,
val optionalThing: String = "optional?", // Not serialized without @ConfigField
val nullableThing: String? = null       // Not serialized without @ConfigField
) : ConfigSerializable
```

#### Restrictions:
- **Must Be Part of a `ConfigSerializable` Object**:
  The parent object must implement `ConfigSerializable` for the fields to be serialized or deserialized.
- **Optional and Nullable Fields**:
  Fields not annotated with `@ConfigField` must be either optional (have default values) or nullable to avoid deserialization errors.
- **Comment Handling**:
  Comments are added only for directly serialized properties, not for nested objects or lists to keep the YAML clean and readable.

---

### `ConfigSerializable`

The `ConfigSerializable` interface enables dynamic serialization and deserialization of custom objects to and from YAML. 
Paired with `@ConfigField`, it allows seamless management of complex data structures.

#### Usage in Config Classes:
You can use `ConfigSerializable` objects as properties within config classes or sections:

```kotlin
@YamlConfig("example")
object ExampleConfig {

    @ConfigPath("example.dto", ["An example DTO object"])
    val exampleDto: ExampleDTO by ConfigDelegate(
        ExampleDTO(
            one = 42,
            two = 3.14,
            three = "HelloWorld",
            four = 1
        )
    )
}
```

Generated YAML:
```yaml
example-dto: # An example DTO object
one: 42  # Test comment on object
two: 3.14  # Test 2
# Second line on test comment
three: HelloWorld
four: 1
```

---

### Nested Objects and Lists

`ConfigSerializable` works seamlessly with nested objects and lists, making it ideal for handling complex YAML structures:

```kotlin
data class ParentDTO(
    @ConfigField(comments = ["A nested object"]) 
    val child: ChildDTO,
    @ConfigField(comments = ["A list of nested objects"]) 
    val children: List<ChildDTO>
) : ConfigSerializable

data class ChildDTO(
    @ConfigField(comments = ["Field within a child"])
    val someField: String
) : ConfigSerializable
```

Generated YAML:
```yaml
parent:
child: # A nested object
someField: value
children: # A list of nested objects
- someField: value1
- someField: value2
```
Notice how the nested child does not write the comments for `someField`

---

#### Serialization and Deserialization Behavior:
- When a `ConfigSerializable` object is assigned to a `ConfigDelegate`, it automatically:
    - Writes fields annotated with `@ConfigField` into YAML.
    - Reads YAML data back into the object during deserialization.
- Nested serialization and deserialization are fully supported.

---

## Usage Examples

### Define a Full Config Class
Treated as the root of the config file

```kotlin
@YamlConfig("example")
object ExampleConfig {

    @ConfigPath("example.test1", ["This is a test string"])
    val test1: String by ConfigDelegate("Default Value") { value ->
        require(value.isNotBlank()) { "test1 cannot be blank!" }
    }

    @ConfigPath("example.test2", ["This is a test integer"])
    val test2: Int by ConfigDelegate(15) { value ->
        require(value > 0) { "test2 must be greater than 0!" }
    }
}
```

Generated YAML:
```yaml
example:
  test1: Default Value  # This is a test string
  test2: 15             # This is a test integer
```

---

### Define a Section Config Class
Treated as a section within the parent file

```kotlin
@ConfigSection(parentFile = "example", sectionPath = "nested.section")
object NestedSectionConfig {

    @ConfigPath("some-setting", ["An example nested setting"])
    val someSetting: Boolean by ConfigDelegate(true)
}
```

Generated YAML:
```yaml
example:
  nested:
    section:
      some-setting: true  # An example nested setting
```

### Load and Use Configs

```kotlin
class SomePlugin : JavaPlugin() {
    override fun onEnable() {
        // Load the full config
        ConfigLoader.loadConfig(this, ExampleConfig::class)

        // Load a section of the config
        ConfigLoader.loadSection(this, NestedSectionConfig::class)

        // Access config values
        logger.info("Test1: ${ExampleConfig.test1}")
        logger.info("Nested Setting: ${NestedSectionConfig.someSetting}")
    }
}
```

### Reload Configs During Runtime

```kotlin
CommandAPICommand("config-reload")
    .executes(CommandExecutor { sender, _ ->
        ConfigLoader.reloadConfig(this, ExampleConfig::class)
        ConfigLoader.reloadSection(this, NestedSectionConfig::class)
        sender.sendMessage("Configs reloaded!")
    })
```

### Listen for Config Reload Events

```kotlin
class ConfigListener : Listener {

    @EventHandler
    fun <T : Any> onConfigReload(event: ConfigReloadEvent<T>) {
        if (event.configClass == ExampleConfig::class) {
            Bukkit.getLogger().info("ExampleConfig reloaded: ${ExampleConfig.test1}")
        } else if (event.configClass == NestedSectionConfig::class) {
            Bukkit.getLogger().info("NestedSectionConfig reloaded: ${NestedSectionConfig.someSetting}")
        }
    }
}
```

---

### **New Generic Handling System**
The updated system automatically resolves generic types at runtime using Kotlin’s `KType` and type projection features. It maps constructor parameters, including generics, without requiring manual type resolution or explicit overrides.

#### **Key Enhancements:**
1. **Type Parameter Mapping**:
    - The system now resolves generic type arguments dynamically, allowing DTOs with parameterized types to serialize and deserialize seamlessly.
    - Supports nested generics (eg, `List<Map<String, Int>>`) and maintains compatibility across multiple levels of generic hierarchies.

2. **Fallback to Transformers for Complex Cases**:
    - While the default behavior works out of the box for most cases, custom transformers can still be defined when specific control over the serialized or deserialized format is required.

3. **Simplified DTO Definitions**:
    - You can now define DTOs with generics without needing custom transformers for each type variation. The system dynamically maps generic arguments during deserialization.

---

#### **Usage Example: DTOs with Generics**

##### **Generic DTO**
```kotlin
data class PhaseData<T : Any>(
    @ConfigField val item: T,
    @ConfigField val fraction: Double
) : ConfigSerializable
```

##### **DTO with Nested Generics**
```kotlin
data class PhaseList<T : Any, P : PhaseData<T>>(
    @ConfigField val itemPhaseList: List<P>
) : ConfigSerializable
```

##### **Configuration Object**
```kotlin
@YamlConfig("example_generics")
object GenericConfig {

    @ConfigPath("phase-data")
    val phaseData: PhaseData<Material> by ConfigDelegate(
        PhaseData(Material.DIAMOND, 0.75)
    )

    @ConfigPath("phase-list")
    val phaseList: PhaseList<Material, PhaseData<Material>> by ConfigDelegate(
        PhaseList(
            itemList = listOf(
                PhaseData(Material.STONE, 0.5),
                PhaseData(Material.GOLD_BLOCK, 0.2)
            )
        )
    )
}
```

##### **Generated YAML**
```yaml
phase-data:
  item: DIAMOND
  fraction: 0.75

phase-list:
  itemList:
  - item: STONE
    fraction: 0.5
  - item: GOLD_BLOCK
    fraction: 0.2
```

---

#### **Comparison to Custom Transformers**

While the new generic handling system covers most use cases, custom transformers remain useful for scenarios that involve:

1. **Complex Type Mappings**:
    - Example: Serializing a generic type with unconventional YAML representations, such as a `WeightedList<T>`.

2. **Interfacing with External APIs**:
    - Example: When DTOs require a format compatible with both YAML and external services or databases.

##### **Example with Transformers**
```kotlin
@ConfigPath("weighted-list")
val weightedList: WeightedList<Material> by ConfigDelegate(
    defaultValue = WeightedList<Material>().apply {
        add(Material.STONE, 10)
        add(Material.DIAMOND, 5)
    },
    serialize = { list -> list.serialize() },
    deserialize = { raw -> WeightedList.deserialize(raw as Map<String, Any>) { Material.valueOf(it as String) } }
)
```

---

### Serialize and Deserialize Transformers

The `ConfigDelegate` now supports optional `serialize` and `deserialize` transformers to handle 
custom serialization and deserialization logic for complex types, such as any non-standard data structure.

These transformers allow you to control how values are saved to and retrieved from the YAML file,
enabling the use of objects that may not natively support YAML serialization.

Of course the other option is to just implement Bukkit's ConfigurationSerializable 
and write your own serialization/deserialization there as well.
---
#### Example Typed Class:
Type T is erased at runtime so requires extra handling to serialize/deserialize
```kotlin
class WeightedList<T> {
    private val weightedItems = mutableListOf<Pair<T, Int>>()

    fun add(item: T, weight: Int): WeightedList<T> {
        if (weight <= 0) error("Weight must be positive")
        weightedItems.add(item to weight)
        return this
    }
}
```

---

### Using Serialize and Deserialize Transformers

Transformers are provided as lambda functions when creating the `ConfigDelegate`.

- **Serialize**: Converts the property value into a YAML-compatible format (e.g., Map, List, primitive types).
- **Deserialize**: Converts raw YAML data back into the expected property value type.

#### Example: Custom Serialization for `WeightedList`

```kotlin
@YamlConfig("example_config")
object ExampleConfig {

    @ConfigPath("weighted-items", ["A weighted list of materials"])
    val weightedItems: WeightedList<Material> by ConfigDelegate(
        defaultValue = WeightedList<Material>().apply {
            add(Material.STONE, 10)
            add(Material.DIAMOND, 1)
            add(Material.IRON_BLOCK, 5)
        },
        serialize = { weightedList -> weightedList.serialize() }, // Custom serialization logic
        deserialize = { rawData ->
            WeightedList.deserialize(rawData as Map<String, Any>) { item ->
                Material.valueOf(item as String) // Transform string into Material
            }
        } // Custom deserialization logic
    )
}
```

#### Generated YAML:
```yaml
example_config:
  weighted-items: # A weighted list of materials
  weightedItems:
    - item: STONE
      weight: 10
    - item: DIAMOND
      weight: 1
    - item: IRON_BLOCK
      weight: 5
```

---

### Serialize Transformer

The `serialize` transformer converts the value into a format suitable for saving in YAML.
This is necessary when the default implementation (via `ConfigSerializable`) is not sufficient.

#### Example:
```kotlin
serialize = { list: WeightedList<Material> ->
    list.serialize() // Converts WeightedList to a map
}
```

---

### Deserialize Transformer

The `deserialize` transformer reconstructs the object from the raw YAML data. 
It ensures that complex objects are correctly instantiated from their YAML representations.

#### Example:
```kotlin
deserialize = { rawData ->
    WeightedList.deserialize(rawData as Map<String, Any>) { item ->
        Material.valueOf(item as String)
    }
}
```

---

### Restrictions and Recommendations

1. **Transformers Override Defaults**:
   If `serialize` and `deserialize` are provided, they take precedence over the default serialization and deserialization logic.

2. **Consistency**:
   Ensure that `serialize` and `deserialize` are compatible with each other. The serialized output must match what the deserialization logic expects.

3. **Validation**:
   You can still provide a `validator` lambda to enforce constraints on the deserialized value.

4. **Fallbacks**:
   If the `deserialize` transformer fails or the raw data is invalid, the delegate falls back to the provided `defaultValue`.

---

## Annotation Usage

### `@YamlConfig`

Used to annotate a class as a config. The `fileName` parameter specifies the base name of the config file (without the `.yml` extension).

```kotlin
@YamlConfig("example")
object ExampleConfig
```

### `@ConfigSection`

Used to annotate a class as a specific section in a YAML config file.
The `parentFile` specifies the file name, and the `sectionPath` specifies the section in the file.

```kotlin
@ConfigSection(parentFile = "example", sectionPath = "nested.section")
object NestedSectionConfig
```

### `@ConfigPath`

Used to annotate properties with the path in the YAML file and an optional comment.

```kotlin
@ConfigPath("example.test1", ["This is the first example test - String"])
val test1: String by ConfigDelegate("Default Value")
```

### `@ConfigField`

Used to mark properties in a class for dynamic serialization and deserialization. Requires the class to implement `ConfigSerializable`. 
Optional comments can be added for better documentation in the generated YAML.

You can also optionally define the `order` to specify the sequence in which fields appear in the serialized output.

```kotlin
data class ExampleDTO(
@ConfigField(order = 1, comments = ["Name of the user"]) val name: String,
@ConfigField(order = 2, comments = ["User's age"]) val age: Int
) : ConfigSerializable
```

The `order` parameter ensures fields appear in a defined sequence. For example:

```yaml
example:
name: DefaultName  # Name of the user
age: 25            # User's age
```

## Future Enhancements
- Additional command helpers for reloading configs
- Seamless integration with Placeholder API type resolvers?
- Possibly better/more dynamic type handling - eg solve things like `WeightedList<T>` 
- Service loaders for managing multiple configs automatically
- Refactor to default to not requiring @ConfigField - this can become tedious in a large data class where all fields will be configurable/serializable
  
