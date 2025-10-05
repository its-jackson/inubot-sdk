package com.itsjackson.framework

import com.itsjackson.util.Logger
import org.rspeer.game.Game
import org.rspeer.game.adapter.scene.Npc
import org.rspeer.game.adapter.scene.SceneObject

sealed interface IStateTreeData

interface IConfigurationProvider
interface IServiceProvider
interface IContextProvider

data class StateTreeParameters(
    private val parameters: MutableMap<String, Any?>
) : IStateTreeData, MutableMap<String, Any?> by parameters {
    constructor() : this(mutableMapOf())

    override fun put(key: String, value: Any?) {
        parameters[key] = value
    }

    override fun get(key: String): Any? {
        return parameters[key]
    }

    fun getBool(key: String): Boolean? {
        return parameters[key] as? Boolean
    }

    fun getString(key: String): String? {
        return parameters[key] as? String
    }

    fun getInt(key: String): Int? {
        return parameters[key] as? Int
    }

    fun getDouble(key: String): Double? {
        return parameters[key] as? Double
    }

    fun getLong(key: String): Long? {
        return parameters[key] as? Long
    }

    fun getNpc(key: String): Npc? {
        return parameters[key] as? Npc
    }

    fun getSceneObject(key: String): SceneObject? {
        return parameters[key] as? SceneObject
    }
}

fun StateTreeParameters.getBoolNotNull(key: String): Boolean {
    return getBool(key) ?: false
}

fun StateTreeParameters.getIntNotNull(key: String): Int {
    return getInt(key) ?: -1
}

data class ContextData<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider>(
    val context: T,
    val service: S,
    val config: C
) : IStateTreeData

data class Combined<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider>(
    val parameters: StateTreeParameters,
    val data: ContextData<T, S, C>
) : IStateTreeData

interface ITransition<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> : IStateTreeData {
    val targetState: IState<T, S, C>?
    val conditions: Array<(Combined<T, S, C>) -> Boolean>
    val operation: LogicalOperation

    fun check(combined: Combined<T, S, C>): Boolean {
        return operation.compute(conditions, combined)
    }
}

interface IEnterCondition<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> : IStateTreeData {
    val conditions: Array<(Combined<T, S, C>) -> Boolean>
    val operation: LogicalOperation

    fun check(combined: Combined<T, S, C>): Boolean {
        return operation.compute(conditions, combined)
    }
}

interface IEvaluator<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> : IStateTreeData {
    fun onStart(combined: Combined<T, S, C>)
    fun onTick(combined: Combined<T, S, C>)
    fun onStop(combined: Combined<T, S, C>)
}

interface IState<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    val name: String
    val enterConditions: Array<IEnterCondition<T, S, C>>
    val tasks: Array<ITask<T, S, C>>
    val transitions: Array<ITransition<T, S, C>>
    val childStates: Array<IState<T, S, C>>

    fun onEnter(combined: Combined<T, S, C>)
    fun onExit(combined: Combined<T, S, C>)
}

enum class TaskStatus {
    RUNNING,
    SUCCESS,
    FAILURE
}

interface ITask<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    val name: String
    var lastTickExecution: Int
    var interrupted: Boolean

    fun execute(combined: Combined<T, S, C>): TaskStatus
    fun interrupt()
}

internal class TaskManager<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    private val logger = Logger("Task Manager")

    fun executeTasks(
        tasks: Array<ITask<T, S, C>>,
        combined: Combined<T, S, C>
    ): TaskStatus {
        var status = TaskStatus.FAILURE

        for (task in tasks) {
            if (task.interrupted) {
                continue
            }

            val gameTick = Game.getServerTick()
            task.lastTickExecution = gameTick

            status = task.execute(combined)
        }

        resetTaskInterruptions(tasks)

        return status // Return the last task executed status
    }

    fun interruptTasks(tasks: Array<ITask<T, S, C>>) {
        tasks.forEachIndexed { index, task ->
            if (!task.interrupted) {
                task.interrupt()
                logger.info("Interrupted task($index), last execution -> ${task.lastTickExecution}")
            }
        }
    }

    private fun resetTaskInterruptions(tasks: Array<ITask<T, S, C>>) {
        tasks.forEach {
            if (it.interrupted) {
                it.interrupted = false
            }
        }
    }
}

enum class LogicalOperation {
    AND {
        override fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> compute(
            conditions: Array<(Combined<T, S, C>) -> Boolean>,
            combined: Combined<T, S, C>
        ): Boolean {
            return conditions.all { it.invoke(combined) }
        }
    },

    NAND {
        override fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> compute(
            conditions: Array<(Combined<T, S, C>) -> Boolean>,
            combined: Combined<T, S, C>
        ): Boolean {
            return !conditions.all { it.invoke(combined) }
        }
    },

    OR {
        override fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> compute(
            conditions: Array<(Combined<T, S, C>) -> Boolean>,
            combined: Combined<T, S, C>
        ): Boolean {
            return conditions.any { it.invoke(combined) }
        }
    },

    NOR {
        override fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> compute(
            conditions: Array<(Combined<T, S, C>) -> Boolean>,
            combined: Combined<T, S, C>
        ): Boolean {
            return !conditions.any { it.invoke(combined) }
        }
    },

    XOR {
        override fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> compute(
            conditions: Array<(Combined<T, S, C>) -> Boolean>,
            combined: Combined<T, S, C>
        ): Boolean {
            return conditions.count { it.invoke(combined) } == 1
        }
    };

    open fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> compute(
        conditions: Array<(Combined<T, S, C>) -> Boolean>,
        combined: Combined<T, S, C>
    ): Boolean {
        // Default implementation; should not be used
        return false
    }
}

class StateTree<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> internal constructor(
    private val root: IState<T, S, C>,
    private val globalTasks: Array<ITask<T, S, C>>,
    private val parameters: StateTreeParameters,
    private val contextData: ContextData<T, S, C>,
    private val evaluators: Array<IEvaluator<T, S, C>>,
    private val globalTransitions: Array<ITransition<T, S, C>>
) {
    private var currentState: IState<T, S, C> = root
    private val combined = Combined(parameters, contextData)
    private val logger = Logger("State Tree")
    private val taskManager = TaskManager<T, S, C>()

    private var _isShutdown = false

    val isShutdown
        get() = _isShutdown

    val currentStateName: String
        get() = currentState.name

    val currentTaskNames: List<String>
        get() = currentState.tasks.map { it.name }.toList()

    fun start() {
        logger.info("Starting up")

        evaluators.forEach {
            it.onStart(combined)
        }
    }

    fun shutdown() {
        if (_isShutdown) {
            return
        }

        logger.info("Shutting down")

        evaluators.forEach {
            it.onStop(combined)
        }

        _isShutdown = true
    }

    fun tick(): TaskStatus {
        evaluators.forEach {
            it.onTick(combined)
        }

        globalTransitions.firstOrNull {
            it.check(combined)
        }?.let {
            triggerGlobalTransition(it.targetState)
        }

        taskManager.executeTasks(globalTasks, combined)

        // Evaluate current state and its children for any transition
        val nextState = evaluateStateTransitions(currentState, combined)
        if (nextState !== currentState) {
            exitState(currentState, combined)
            currentState = nextState ?: root
            enterState(currentState, combined)
        }

        return taskManager.executeTasks(currentState.tasks, combined)
    }

    private fun evaluateStateTransitions(
        state: IState<T, S, C>,
        combined: Combined<T, S, C>
    ): IState<T, S, C>? {
        // Evaluate all child states first
        state.childStates.forEach { childState ->
            if (childState.enterConditions.any { it.check(combined) }) {
                val nextState = evaluateStateTransitions(childState, combined)
                return nextState ?: childState
            }
        }

        // Evaluate all transitions and target states
        state.transitions.forEach { transition ->
            if (!transition.check(combined)) {
                return@forEach
            }

            val targetState = transition.targetState ?: return null
            if (targetState.enterConditions.none { it.check(combined) }) {
                return@forEach
            }

            targetState.childStates.forEach { childState ->
                if (childState.enterConditions.any { it.check(combined) }) {
                    val nextState = evaluateStateTransitions(childState, combined)
                    return nextState ?: childState
                }
            }
            return targetState
        }

        return state // No valid transitions or child states found
    }

    private fun enterState(
        state: IState<T, S, C>,
        combined: Combined<T, S, C>
    ) {
        logger.info("Entering -> ${state.name}")
        state.onEnter(combined)
    }

    private fun exitState(
        state: IState<T, S, C>,
        combined: Combined<T, S, C>
    ) {
        logger.info("Exiting -> ${state.name}")
        state.onExit(combined)
    }

    private fun triggerGlobalTransition(targetState: IState<T, S, C>?) {
        // Don't need to transition if the state hasn't changed
        if (targetState === currentState) {
            return
        }

        logger.info("Global transition triggered")

        taskManager.interruptTasks(currentState.tasks)
        exitState(currentState, combined)
        currentState = targetState ?: root
        enterState(currentState, combined)
    }
}

@DslMarker
annotation class StateTreeDsl

@StateTreeDsl
class StateTreeBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    private var root: IState<T, S, C>? = null
    private var parameters: StateTreeParameters? = null
    private var contextData: ContextData<T, S, C>? = null
    private val globalTasks = mutableListOf<ITask<T, S, C>>()
    private val evaluators = mutableListOf<IEvaluator<T, S, C>>()
    private val globalTransitions = mutableListOf<ITransition<T, S, C>>()

    fun root(
        name: String = "Root",
        init: StateBuilder<T, S, C>.() -> Unit
    ) {
        root = StateBuilder<T, S, C>(name).apply(init).build()
    }

    fun parameters(init: StateTreeParameters.() -> Unit) {
        parameters = StateTreeParameters().apply(init)
    }

    fun context(
        context: T,
        service: S,
        config: C,
        init: ContextData<T, S, C>.() -> Unit
    ) {
        contextData = ContextData(context, service, config).apply(init)
    }

    fun globalTask(name: String = "", action: (Combined<T, S, C>, ITask<T, S, C>) -> TaskStatus) {
        globalTasks.add(buildTask(name, action))
    }

    fun globalTasks(init: TaskListBuilder<T, S, C>.() -> Unit) {
        globalTasks.addAll(TaskListBuilder<T, S, C>().apply(init).build())
    }

    fun globalEvaluator(init: EvaluatorBuilder<T, S, C>.() -> Unit) {
        evaluators.add(EvaluatorBuilder<T, S, C>().apply(init).build())
    }

    fun globalTransition(init: TransitionBuilder<T, S, C>.() -> Unit) {
        globalTransitions.add(TransitionBuilder<T, S, C>().apply(init).build())
    }

    fun build(): StateTree<T, S, C> {
        return StateTree(
            root ?: throw IllegalStateException("Root state must be defined"),
            globalTasks.toTypedArray(),
            parameters ?: throw IllegalStateException("Parameters must be defined"),
            contextData ?: throw IllegalStateException("Context must must be defined"),
            evaluators.toTypedArray(),
            globalTransitions.toTypedArray()
        )
    }
}

@StateTreeDsl
class StateBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider>(
    private val name: String
) {
    private val childStates = mutableListOf<IState<T, S, C>>()
    private val enterConditions = mutableListOf<IEnterCondition<T, S, C>>()
    private val tasks = mutableListOf<ITask<T, S, C>>()
    private val transitions = mutableListOf<ITransition<T, S, C>>()
    private var onEnterFunc: (Combined<T, S, C>) -> Unit = { }
    private var onExitFunc: (Combined<T, S, C>) -> Unit = { }

    fun state(name: String, init: StateBuilder<T, S, C>.() -> Unit): IState<T, S, C> {
        val state = StateBuilder<T, S, C>(name).apply(init).build()
        childStates.add(state)
        return state
    }

    fun enterWhen(init: EnterConditionBuilder<T, S, C>.() -> Unit) {
        enterConditions.add(EnterConditionBuilder<T, S, C>().apply(init).build())
    }

    fun enter(
        operation: LogicalOperation = LogicalOperation.AND,
        condition: (Combined<T, S, C>) -> Boolean
    ) {
        enterConditions.add(buildEnterCondition(operation, condition))
    }

    fun enter(
        operation: LogicalOperation = LogicalOperation.AND,
        conditions: Array<(Combined<T, S, C>) -> Boolean>
    ) {
        enterConditions.add(buildEnterCondition(operation, conditions))
    }

    fun task(name: String = "", action: (Combined<T, S, C>, ITask<T, S, C>) -> TaskStatus) {
        tasks.add(buildTask(name, action))
    }

    fun task(name: String = "", action: () -> TaskStatus) {
        tasks.add(
            buildTask(name) { combined, task ->
                action.invoke()
            }
        )
    }

    fun taskRunning(name: String = "", action: () -> Unit) {
        tasks.add(
            buildTask(name) { combined, task ->
                action.invoke()
                TaskStatus.RUNNING
            }
        )
    }

    fun tasks(init: TaskListBuilder<T, S, C>.() -> Unit) {
        tasks.addAll(TaskListBuilder<T, S, C>().apply(init).build())
    }

    fun transitionWhen(init: TransitionBuilder<T, S, C>.() -> Unit) {
        transitions.add(TransitionBuilder<T, S, C>().apply(init).build())
    }

    fun transition(
        operation: LogicalOperation = LogicalOperation.AND,
        targetState: IState<T, S, C>? = null,
        condition: (Combined<T,S,C>) -> Boolean,
    ) {
        transitions.add(buildTransition(targetState, operation, condition))
    }

    fun transition(
        operation: LogicalOperation = LogicalOperation.AND,
        conditions: Array<(Combined<T,S,C>) -> Boolean>,
        targetState: IState<T, S, C>? = null,
    ) {
        transitions.add(buildTransition(targetState, operation, conditions))
    }

    fun onEnter(action: (Combined<T, S, C>) -> Unit) {
        onEnterFunc = action
    }

    fun onExit(action: (Combined<T, S, C>) -> Unit) {
        onExitFunc = action
    }

    fun build(): IState<T, S, C> {
        return buildState(
            name = name,
            enterConditions = enterConditions.takeIf { it.isNotEmpty() }?.toTypedArray() ?: arrayOf(buildEnterCondition { true }),
            tasks = tasks.toTypedArray(),
            transitions = transitions.toTypedArray(),
            childStates = childStates.toTypedArray(),
            onEnterFunc = onEnterFunc,
            onExitFunc = onExitFunc
        )
    }
}

@StateTreeDsl
class TaskListBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    private val tasks = mutableListOf<ITask<T, S, C>>()

    fun task(name: String = "", action: (Combined<T, S, C>, ITask<T, S, C>) -> TaskStatus) {
        tasks.add(buildTask(name, action))
    }

    operator fun String.invoke(action: (Combined<T, S, C>, ITask<T, S, C>) -> TaskStatus) {
        task(this, action)
    }

    fun build(): List<ITask<T, S, C>> = tasks
}

@StateTreeDsl
class EnterConditionBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    private var operation: LogicalOperation = LogicalOperation.AND
    private val conditions = mutableListOf<(Combined<T, S, C>) -> Boolean>()

    fun op(op: LogicalOperation) {
        operation = op
    }

    fun condition(predicate: (Combined<T, S, C>) -> Boolean) {
        conditions.add(predicate)
    }

    operator fun invoke(predicate: (Combined<T, S, C>) -> Boolean) {
        condition(predicate)
    }

    fun build(): IEnterCondition<T, S, C> {
        return buildEnterCondition(operation, conditions.toTypedArray())
    }
}

@StateTreeDsl
class TransitionBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    private var targetState: IState<T, S, C>? = null
    private var operation: LogicalOperation = LogicalOperation.AND
    private val conditions = mutableListOf<(Combined<T, S, C>) -> Boolean>()

    fun to(state: IState<T, S, C>) {
        targetState = state
    }

    fun op(op: LogicalOperation) {
        operation = op
    }

    fun condition(predicate: (Combined<T, S, C>) -> Boolean) {
        conditions.add(predicate)
    }

    operator fun invoke(predicate: (Combined<T, S, C>) -> Boolean) {
        condition(predicate)
    }

    fun build(): ITransition<T, S, C> {
        return buildTransition(targetState, operation, conditions.toTypedArray())
    }
}

@StateTreeDsl
class EvaluatorBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    private var onStartFunc: (Combined<T, S, C>) -> Unit = { }
    private var onTickFunc: (Combined<T, S, C>) -> Unit = { }
    private var onStopFunc: (Combined<T, S, C>) -> Unit = { }

    fun onStart(action: (Combined<T, S, C>) -> Unit) {
        onStartFunc = action
    }

    fun onTick(action: (Combined<T, S, C>) -> Unit) {
        onTickFunc = action
    }

    fun onStop(action: (Combined<T, S, C>) -> Unit) {
        onStopFunc = action
    }

    fun build(): IEvaluator<T, S, C> {
        return buildEvaluator(onStartFunc, onTickFunc, onStopFunc)
    }
}

fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> stateTree(
    init: StateTreeBuilder<T, S, C>.() -> Unit
): StateTree<T, S, C> {
    return StateTreeBuilder<T, S, C>().apply(init).build()
}

fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> evaluator(
    init: EvaluatorBuilder<T, S, C>.() -> Unit
): IEvaluator<T, S, C> {
    return EvaluatorBuilder<T, S, C>().apply(init).build()
}

internal fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> buildTask(
    name: String = "",
    executeFunc: (Combined<T, S, C>, ITask<T, S, C>) -> TaskStatus
): ITask<T, S, C> {
    return object : ITask<T, S, C> {
        override val name: String = name
        override var lastTickExecution: Int = 0
        override var interrupted: Boolean = false

        override fun execute(combined: Combined<T, S, C>): TaskStatus {
            return executeFunc.invoke(combined, this)
        }

        override fun interrupt() {
            interrupted = true
        }
    }
}

internal fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> buildEvaluator(
    onStartFunc: (Combined<T, S, C>) -> Unit = { },
    onTickFunc: (Combined<T, S, C>) -> Unit = { },
    onStopFunc: (Combined<T, S, C>) -> Unit = { }
): IEvaluator<T, S, C> {
    return object : IEvaluator<T, S, C> {
        override fun onStart(combined: Combined<T, S, C>) = onStartFunc(combined)
        override fun onTick(combined: Combined<T, S, C>) = onTickFunc(combined)
        override fun onStop(combined: Combined<T, S, C>) = onStopFunc(combined)
    }
}

internal fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> buildTransition(
    targetState: IState<T, S, C>? = null,
    logicalOperation: LogicalOperation = LogicalOperation.AND,
    condition: (Combined<T, S, C>) -> Boolean,
): ITransition<T, S, C> {
    return buildTransition(targetState, logicalOperation, arrayOf(condition))
}

internal fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> buildTransition(
    targetState: IState<T, S, C>? = null,
    logicalOperation: LogicalOperation = LogicalOperation.AND,
    conditions: Array<(Combined<T, S, C>) -> Boolean>
): ITransition<T, S, C> {
    return object : ITransition<T, S, C> {
        override val targetState: IState<T, S, C>? = targetState
        override val operation: LogicalOperation = logicalOperation
        override val conditions: Array<(Combined<T, S, C>) -> Boolean> = conditions
    }
}

internal fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> buildEnterCondition(
    logicalOperation: LogicalOperation = LogicalOperation.AND,
    condition: (Combined<T, S, C>) -> Boolean,
): IEnterCondition<T, S, C> {
    return buildEnterCondition(logicalOperation, arrayOf(condition))
}

internal fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> buildEnterCondition(
    logicalOperation: LogicalOperation = LogicalOperation.AND,
    conditions: Array<(Combined<T, S, C>) -> Boolean>,
): IEnterCondition<T, S, C> {
    return object : IEnterCondition<T, S, C> {
        override val conditions: Array<(Combined<T, S, C>) -> Boolean> = conditions
        override val operation: LogicalOperation = logicalOperation
    }
}

internal fun <T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> buildState(
    name: String,
    enterConditions: Array<IEnterCondition<T, S, C>>,
    tasks: Array<ITask<T, S, C>>,
    transitions: Array<ITransition<T, S, C>>,
    childStates: Array<IState<T, S, C>> = emptyArray(),
    onEnterFunc: (Combined<T, S, C>) -> Unit = { },
    onExitFunc: (Combined<T, S, C>) -> Unit = { }
): IState<T, S, C> {
    return object : IState<T, S, C> {
        override val name: String = name
        override val enterConditions: Array<IEnterCondition<T, S, C>> = enterConditions
        override val tasks: Array<ITask<T, S, C>> = tasks
        override val transitions: Array<ITransition<T, S, C>> = transitions
        override val childStates: Array<IState<T, S, C>> = childStates

        override fun onEnter(combined: Combined<T, S, C>) = onEnterFunc(combined)
        override fun onExit(combined: Combined<T, S, C>) = onExitFunc(combined)
    }
}