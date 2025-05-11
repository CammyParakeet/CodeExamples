package gg.shaded.core.bukkit.config.delegate

import gg.shaded.core.bukkit.config.ConfigPath
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.isAccessible

fun <T : Any> forEachConfigDelegate(configClass: KClass<T>, action: (ConfigDelegate<T>, KProperty1<T, *>) -> Unit) {
    val configInstance = configClass.objectInstance ?: runCatching { configClass.createInstance() }.getOrNull()
    if (configInstance != null) {
        configClass.declaredMemberProperties.forEach { property ->
            property.isAccessible = true
            if (property.findAnnotation<ConfigPath>() != null) {
                @Suppress("UNCHECKED_CAST")
                (property.getDelegate(configInstance) as? ConfigDelegate<T>)?.let { delegate -> action(delegate, property) }
            }
        }
    }
}