package DSL

object TaskBuilderUsage {


    fun test() {
        scheduleSyncAndAsync(plugin) {
            // make 0 scale
            asyncStackedTask(1) {
                wallEntities.forEach { e ->
                    e.editTransform { scale.set(0.01) }
                }
            }
            // scale width
            asyncStackedTask(1) {
                wallEntities.forEach { e ->
                    e.interpolationDuration = 10
                    e.editTransform { scale.set(3.5, 0.05, 3.5) }
                }
            }
            multiTask(5, stack = true,
                runSync = {
                    center.playCustomSound("fire.impact_big", 3.0F, 1.25F)
                    center.playCustomSound("fire.slow", 3.0F, 1.25F)
                    particlesTask.runTaskTimer(plugin, 0L, 2L)
                },
                runAsync = {
                    wallEntities.forEach { e ->
                        e.editTransform { scale.set(3.5, 3.5, 3.5) }
                    }
                }
            )
            // reset height after duration
            multiTask(duration, stack = true, runSync = {
                center.playCustomSound("fire.slow", 3.0F, 1.25F)
            }) {
                // async
                wallEntities.forEach { e ->
                    e.interpolationDelay = 0
                    e.interpolationDuration = 8
                    e.editTransform { scale.set(3.5, 0.01, 3.5) }
                }
            }
            // remove
            stackedTask(9, global = true) {
                wallEntities.forEach(ItemDisplay::remove)
                particlesTask.cancel()
            }
        }
    }


}