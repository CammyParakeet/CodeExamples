package Extensions

object LocationUtils {

    /**
     * Bypasses the need to use World#Spawn for Entities
     */
    fun <T : Entity> Location.spawn(entityClass: Class<T>, init: T.() -> Unit = {}): T {
        return world.spawn(this, entityClass, Consumer(init))
    }

}