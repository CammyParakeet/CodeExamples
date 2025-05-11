package gg.shaded.core.bukkit.config

import gg.shaded.core.bukkit.config.delegate.ConfigDelegate
import gg.shaded.core.bukkit.config.delegate.forEachConfigDelegate
import gg.shaded.core.bukkit.config.event.ConfigLoadEvent
import gg.shaded.core.bukkit.config.event.ConfigReloadEvent
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.ref.WeakReference
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.isAccessible

/**
 * ConfigLoader is responsible for managing YAML configuration files and sections within a Bukkit plugin
 *
 * **Key Features:**
 * - **Dynamic Loading:** Supports full YAML configs (`@YamlConfig`) and specific sections (`@ConfigSection`)
 * - **Caching:** Efficiently caches values via `ConfigDelegate`, reducing redundant disk reads
 * - **Weak References:** Uses weak references to minimize memory usage, allowing garbage collection when not in use
 * - **Expiration System:** Configs can expire from memory after a defined duration, improving performance for infrequent operations
 * - **Pre-Caching:** Allows preloading of config values into memory to optimize performance before heavy tasks
 * - **Event Dispatching:** Fires events like `ConfigLoadEvent` and `ConfigReloadEvent` for plugin hooks
 *
 * **Annotations Supported:**
 * - `@YamlConfig`: Defines full YAML file configurations
 * - `@ConfigSection`: Targets specific sections within a YAML file
 * - `@ConfigPath`: Marks properties for configuration binding
 */
object ConfigLoader {

    private val configLock = Any()

    /**
     * Map of config classes to their loaded YAML config
     */
    private val loadedConfigs = mutableMapOf<KClass<*>, YamlConfiguration>()

    /**
     * Map of config section classes to their loaded YAML config sections
     */
    private val loadedSections = mutableMapOf<KClass<*>, ConfigurationSection>()

    /**
     * Map of config class expiration times
     */
    private val expirationTimes = mutableMapOf<KClass<*>, Long>()

    /**
     * Read-only view of loaded configs
     */
    val getLoadedConfigs = loadedConfigs.toMap()

    /**
     * Read-only view of loaded config sections
     */
    val getLoadedSections = loadedSections.toMap()

    /**
     * Loads or creates config files or sections based on the list of annotated classes given
     *
     * Handles both full file configurations (`@YamlConfig`) and sections within a file (`@ConfigSection`).
     * Populates default values, applies comments, and saves the file to ensure consistency
     *
     * @param plugin The plugin instance used to locate the data folder
     * @param configClasses The classes representing the configs or sections
     * @throws IllegalStateException If a class is not annotated with [YamlConfig] or [ConfigSection]
     */
    fun loadConfigs(plugin: JavaPlugin, vararg configClasses: KClass<*>) {
        synchronized(configLock) {
            configClasses.forEach { loadConfig(plugin, it) }
        }
    }

    /**
     * Loads or creates config files or sections based on the list of annotated classes given
     *
     * Handles both full file configurations (`@YamlConfig`) and sections within a file (`@ConfigSection`).
     * Populates default values, applies comments, and saves the file to ensure consistency
     *
     * @param plugin The plugin instance used to locate the data folder
     * @param configClasses The classes representing the configs or sections
     * @param expirationOverride An overriding duration in millis applied to all configs loaded in this block,
     * less than 1 is treated as infinite
     * @throws IllegalStateException If a class is not annotated with [YamlConfig] or [ConfigSection]
     */
    fun loadConfigs(plugin: JavaPlugin, expirationOverride: Long? = null, vararg configClasses: KClass<*>) {
        synchronized(configLock) {
            configClasses.forEach { loadConfig(plugin, it, expirationOverride) }
        }
    }

    /**
     * Loads or creates a config file or section based on the annotated class
     *
     * Handles both full file configurations (`@YamlConfig`) and sections within a file (`@ConfigSection`).
     * Populates default values, applies comments, and saves the file to ensure consistency
     *
     * @param plugin The plugin instance used to locate the data folder
     * @param configClass The class representing the config or section
     * @param expirationOverride An overriding duration in millis until this config will be expired from the loader,
     * less than 1 is treated as infinite
     * @return The instance of the config object, initialized with the config data
     * @throws IllegalStateException If the class is not annotated with [YamlConfig] or [ConfigSection]
     */
    fun <T : Any> loadConfig(plugin: JavaPlugin, configClass: KClass<T>, expirationOverride: Long? = null): T {
        val (configFile, configSection) = resolveConfig(plugin, configClass)

        val configInstance = populateConfigObject(configClass, configSection)

        if (configSection is YamlConfiguration) {
            configSection.save(configFile)
        } else {
            (configSection.root as? YamlConfiguration)?.save(configFile)
        }

        if (configClass.findAnnotation<YamlConfig>() != null) {
            loadedConfigs[configClass] = configSection as YamlConfiguration
        } else {
            loadedSections[configClass] = configSection
        }

        val expirationMillis = expirationOverride ?: configClass.findAnnotation<YamlConfig>()?.expirationMillis
            ?: configClass.findAnnotation<ConfigSection>()?.expirationMillis

        expirationMillis?.takeIf { it > 0 }?.let {
            expirationTimes[configClass] = System.currentTimeMillis() + it
        }

        // Call load event sync
        plugin.server.apply {
            scheduler.runTask(plugin, Runnable {
                pluginManager.callEvent(ConfigLoadEvent(configClass, configInstance))
            })
        }

        return configInstance
    }

    /**
     * Resolves the config file and section for the provided config class
     *
     * @param plugin The plugin instance used to locate the data folder
     * @param configClass The class representing the config or section
     * @return A pair containing the config file and the resolved config section
     * @throws IllegalStateException If the class is not annotated with [YamlConfig] or [ConfigSection]
     */
    private fun <T : Any> resolveConfig(
        plugin: JavaPlugin,
        configClass: KClass<T>
    ): Pair<File, ConfigurationSection> {
        val logger = plugin.slF4JLogger
        val annotatedYaml = configClass.findAnnotation<YamlConfig>()
        val annotatedSection = configClass.findAnnotation<ConfigSection>()

        val fileName: String
        val sectionPath: String?

        when {
            annotatedYaml != null -> {
                fileName = annotatedYaml.fileName
                sectionPath = null
            }
            annotatedSection != null -> {
                fileName = annotatedSection.parentFile
                sectionPath = annotatedSection.sectionPath
            }
            else -> error("Class ${configClass.simpleName} must be annotated with @YamlConfig or @ConfigSection")
        }

        val fullName = "${fileName}.yml"
        val configFile = File(plugin.dataFolder, fullName)
        if (!configFile.exists()) {
            logger.warn("Config file '$fileName' not found, creating with any default values registered in ${configClass.simpleName}")
        }

        val parentConfig = YamlConfiguration.loadConfiguration(configFile)
        val section = sectionPath?.let { parentConfig.getConfigurationSection(it) ?: parentConfig.createSection(it) }
            ?: parentConfig

        return configFile to section
    }

    /**
     * Populates the given config section with defaults and comments based on the provided class
     *
     * @param configClass The class representing the config or section
     * @param configSection The config section to populate
     * @return An instance of the config object populated with the config data
     */
    private fun <T : Any> populateConfigObject(
        configClass: KClass<T>,
        configSection: ConfigurationSection
    ): T {
        val configInstance = configClass.objectInstance ?: configClass.createInstance()
        configClass.declaredMemberProperties.forEach { property ->
            property.isAccessible = true
            val annotation = property.findAnnotation<ConfigPath>() ?: return@forEach
            val path = annotation.path
            val comments = annotation.comments

            @Suppress("UNCHECKED_CAST")
            (property.getDelegate(configInstance) as? ConfigDelegate<T>)?.let { delegate ->
                delegate.resetCache()
                delegate.getDefaultValue().let { defaultValue ->
                    if (!configSection.contains(path)) {
                        delegate.saveValue(configSection, path, defaultValue)
                    }
                }
            }

            if (comments.isNotEmpty()) {
                configSection.setInlineComments(path, comments.asList())
            }
        }
        return configInstance
    }

    /**
     * Runs through all [ConfigPath] delegates in a config instance and loads its cache
     */
    fun <T : Any> cacheConfig(configClass: KClass<T>, expirationOverride: Long? = null) {
        val expirationMillis = expirationOverride ?: configClass.findAnnotation<YamlConfig>()?.expirationMillis
        ?: configClass.findAnnotation<ConfigSection>()?.expirationMillis

        expirationMillis?.takeIf { it > 0 }?.let {
            expirationTimes[configClass] = System.currentTimeMillis() + it
        }

        val configInstance = configClass.objectInstance ?: runCatching { configClass.createInstance() }.getOrNull()
        if (configInstance != null) {
            forEachConfigDelegate(configClass) { delegate, property -> delegate.getValue(configInstance, property) }
        }
    }

    /**
     * Caches the delegate values in all the given config classes
     */
    fun cacheConfigs(vararg configClasses: KClass<*>, expirationOverride: Long? = null) {
        configClasses.forEach { cacheConfig(it, expirationOverride) }
    }

    /**
     * Reloads a config file or section
     *
     * Clears the existing cache and reloads the config from its file
     * Fires a [ConfigReloadEvent] after reloading
     *
     * @param plugin The plugin instance
     * @param configClass The class representing the config or section
     * @return The reloaded config instance
     */
    fun <T : Any> reloadConfig(plugin: JavaPlugin, configClass: KClass<T>): T {
        val configInstance = loadConfig(plugin, configClass)
        plugin.server.pluginManager.callEvent(ConfigReloadEvent(configClass, configInstance))
        return configInstance
    }

    /**
     * Gets the [ConfigurationSection] associated with a loaded config class
     *
     * @param configClass The class of the config
     * @return The [ConfigurationSection] for the given class, or null if the config is not loaded
     */
    fun <T : Any> getConfig(configClass: KClass<T>): ConfigurationSection? {
        return loadedConfigs[configClass]
    }

    /**
     * Gets the [ConfigurationSection] associated with a loaded config section class
     *
     * @param sectionKClass The class of the config section
     * @return The [ConfigurationSection] for the given class, or null if the section is not loaded
     */
    fun <T : Any> getConfigSection(sectionKClass: KClass<T>): ConfigurationSection? {
        return loadedSections[sectionKClass]
    }

    /**
     * Checks the expiration time map to find any expired configs
     * and clears their loaded yaml and caches from memory
     */
    fun checkConfigExpiry() {
        val currentTime = System.currentTimeMillis()

        expirationTimes.entries.removeIf { (configClass, expiryTime) ->
            if (currentTime >= expiryTime) {
                unloadConfig(configClass)
                true
            } else {
                false
            }
        }
    }

    fun unloadConfig(configClass: KClass<*>) {
        loadedConfigs.remove(configClass)
        loadedSections.remove(configClass)

        forEachConfigDelegate(configClass::class) { delegate, _ ->
            delegate.resetCache()
        }
    }

    /**
     * Begins an async task to check the loaded configs for their expiry time
     * Defaults to check every minute
     */
    fun scheduleExpirationCheck(plugin: JavaPlugin, period: Long = 20 * 60) {
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            checkConfigExpiry()
        }, 0L, period)
    }

}