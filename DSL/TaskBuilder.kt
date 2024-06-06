package DSL

/**
 * A builder class for scheduling Bukkit Tasks.
 * It supports scheduling both synchronous and asynchronous tasks, with options for immediate execution
 * or delayed execution. Tasks can be stacked to create sequences of actions that occur with cumulative delays.
 *
 * @property plugin The [JavaPlugin] instance within which the tasks are to be scheduled.
 * @author Cammy
 * @since 1.0
 */
class TaskBuilder(private val plugin: JavaPlugin)  {

    private val scheduler = plugin.server.scheduler

    private val tasks = mutableListOf<Pair<Long, () -> Unit>>()
    private val stackedTasks = mutableListOf<Pair<Long, () -> Unit>>()
    private val asyncTasks = mutableListOf<Pair<Long, () -> Unit>>()
    private val stackedAsyncTasks = mutableListOf<Pair<Long, () -> Unit>>()

    private var totalStackedDelay = 0L
    private var totalStackedDelaySync = 0L
    private var totalStackedDelayAsync = 0L

    /**
     * Schedules a synchronous task with an optional delay.
     *
     * Note: [stackedTask] will not stack with *this* if [stack] is true
     *
     * @param delay The delay in ticks before the task is executed. Default is 0 (immediate execution).
     * @param stack Whether this [task] will add to the [totalStackedDelay]
     * @param action The task to execute, represented as a lambda function.
     * @return Returns the [TaskBuilder] instance for chaining method calls.
     */
    fun task(delay: Long = 0, stack: Boolean = false, action: () -> Unit): TaskBuilder {
        val actualDelay = if (stack) {
            totalStackedDelaySync += delay
            totalStackedDelaySync
        } else delay

        tasks.add(actualDelay to action)
        return this
    }

    /**
     * Schedules a stacked synchronous task. Stacked tasks have their delays accumulated,
     * creating a sequence of tasks that execute one after another.
     *
     * Note: This accumulates both total and sync stacks
     *
     * @param delay The delay in ticks to add to the total stacked delay before executing this task.
     * @param global Whether this will run on the total stack or sync stack
     * @param action The task to execute.
     * @return Returns the [TaskBuilder] instance for chaining method calls.
     */
    fun stackedTask(delay: Long = 0, global: Boolean = false, action: () -> Unit): TaskBuilder  {
        totalStackedDelay += delay
        totalStackedDelaySync += delay
        val actualDelay = if (global) totalStackedDelay else totalStackedDelaySync

        stackedTasks.add(actualDelay to action)
        return this
    }

    /**
     * Schedules an asynchronous task with an optional delay.
     *
     * Note: [asyncStackedTask] will not stack with *this* if [stack] is true
     *
     * @param delay The delay in ticks before the task is executed. Default is 0 (immediate execution).
     * @param stack Whether this [task] will add to the [totalStackedDelay]
     * @param action The task to execute asynchronously.
     * @return Returns the [TaskBuilder] instance for chaining method calls.
     */
    fun asyncTask(delay: Long = 0, stack: Boolean = false, action: () -> Unit): TaskBuilder  {
        val actualDelay = if (stack) {
            totalStackedDelayAsync += delay
            totalStackedDelayAsync
        } else delay

        asyncTasks.add(actualDelay to action)
        return this
    }

    /**
     * Schedules a stacked asynchronous task. Stacked tasks have their delays accumulated,
     * creating a sequence of tasks that execute one after another asynchronously.
     *
     * Note: This accumulates both total and async stacks
     *
     * @param delay The delay in ticks to add to the total stacked delay before executing this task.
     * @param global Whether this will run on the total stack or async stack
     * @param action The task to execute asynchronously.
     * @return Returns the [TaskBuilder] instance for chaining method calls.
     */
    fun asyncStackedTask(delay: Long = 0, global: Boolean = false, action: () -> Unit): TaskBuilder {
        totalStackedDelayAsync += delay
        totalStackedDelay += delay
        val actualDelay = if (global) totalStackedDelay else totalStackedDelayAsync

        stackedAsyncTasks.add(actualDelay to action)
        return this
    }

    /**
     * Schedules both sync and async tasks scheduled for the same server tick.
     *
     * Stacked tasks have their delays accumulated,
     * creating a sequence of tasks that execute one after another.
     *
     * @param delay The delay in ticks to add to the total stacked delay before executing this task.
     * @param stack Whether this [multiTask] will add to the [totalStackedDelay]
     * @param runSync The task to execute synchronously.
     * @param runAsync The task to execute asynchronously.
     * @return Returns the [TaskBuilder] instance for chaining method calls.
     */
    fun multiTask(delay: Long = 0, stack: Boolean = false, runSync: () -> Unit, runAsync: () -> Unit): TaskBuilder {
        val actualDelay = if (stack) {
            totalStackedDelay += delay
            totalStackedDelay
        } else delay

        tasks.add(actualDelay to runSync)
        asyncTasks.add(actualDelay to runAsync)
        return this
    }


    /**
     * Schedules all configured synchronous tasks (both immediate and stacked) to be executed
     * in accordance to their defined delays.
     */
    fun schedule() {
        tasks.forEach { (delay, action) ->
            scheduler.runTaskLater(plugin, Runnable(action), delay)
        }

        stackedTasks.forEach { (delay, action) ->
            scheduler.runTaskLater(plugin, Runnable(action), delay)
        }
    }

    /**
     * Schedules all configured asynchronous tasks (both immediate and stacked) to be executed
     * in accordance to their defined delays.
     */
    fun scheduleAsync() {
        asyncTasks.forEach { (delay, action) ->
            scheduler.runTaskLaterAsynchronously(plugin, Runnable(action), delay)
        }

        stackedAsyncTasks.forEach { (delay, action) ->
            scheduler.runTaskLaterAsynchronously(plugin, Runnable(action), delay)
        }
    }

}

/**
 * Schedules synchronous tasks configured using the provided [TaskBuilder] block.
 *
 * @param plugin The [JavaPlugin] instance within which the tasks are to be scheduled.
 * @param block The configuration block for the [TaskBuilder].
 */
fun scheduleTasks(plugin: JavaPlugin, block: TaskBuilder.() -> Unit) {
    TaskBuilder(plugin).apply(block).schedule()
}

/**
 * Schedules asynchronous tasks configured using the provided [TaskBuilder] block.
 *
 * @param plugin The [JavaPlugin] instance within which the tasks are to be scheduled.
 * @param block The configuration block for the [TaskBuilder].
 */
fun scheduleAsyncTasks(plugin: JavaPlugin, block: TaskBuilder.() -> Unit) {
    TaskBuilder(plugin).apply(block).scheduleAsync()
}

/**
 * Schedules both synchronous and asynchronous tasks configured using the provided [TaskBuilder] block.
 *
 * @param plugin The [JavaPlugin] instance within which the tasks are to be scheduled.
 * @param block The configuration block for the [TaskBuilder].
 */
fun scheduleSyncAndAsync(plugin: JavaPlugin, block: TaskBuilder.() -> Unit) {
    val builder = TaskBuilder(plugin).apply(block)
    builder.schedule()
    builder.scheduleAsync()
}