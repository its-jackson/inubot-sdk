package com.itsjackson.script.sdk.framework

import org.rspeer.game.script.Task
import org.rspeer.game.script.TaskDescriptor

abstract class StateTreeRoutineTask<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider>(
    protected val contextProvider: T,
    protected val serviceProvider: S,
    protected val configProvider: C
) : Task() {
    protected val manifest = this::class.java.getAnnotation(TaskDescriptor::class.java)

    private var started = false

    var running = false

    private val stateTree: StateTree<T, S, C> by lazy {
        buildTree()
    }

    val state
        get() = stateTree.currentStateName

    val tasks
        get() = stateTree.currentTaskNames

    override fun execute(): Boolean {
        if (!started ) {
            started = true
            initialize()
            stateTree.start()
        }
        if (!running) {
            return false
        }
        return when (stateTree.tick()) {
            TaskStatus.RUNNING -> {
                onTaskRunning()
                true
            }
            TaskStatus.SUCCESS -> {
                onTaskSuccess()
                true
            }
            TaskStatus.FAILURE -> {
                onTaskFailure()
                false
            }
        }
    }

    /**
     * Invoked before the state tree is constructed.
     * Used for initializing any state, or can configure classes.
     */
    abstract fun initialize()

    /**
     * Constructs the primary state tree that the routine will tick (script logic controller)
     */
    abstract fun buildTree(): StateTree<T, S, C>

    open fun onTaskRunning() {

    }

    open fun onTaskSuccess() {

    }

    open fun onTaskFailure() {

    }
}