package com.itsjackson.script.sdk.framework

import com.itsjackson.script.sdk.util.GameTick
import com.itsjackson.script.sdk.util.Logger
import org.rspeer.commons.logging.Log
import org.rspeer.game.adapter.scene.Npc
import org.rspeer.game.adapter.scene.SceneObject
import org.rspeer.game.script.Task

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
    var wait: CombinedWait<T, S, C>?
    var lastStatus: TaskStatus
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
        val gameTick = GameTick.now()
        var lastStatus = TaskStatus.RUNNING

        for (task in tasks) {
            if (lastStatus == TaskStatus.FAILURE) {
                break
            }

            if (task.interrupted) {
                continue
            }

            if (task.lastStatus == TaskStatus.SUCCESS) {
                continue
            }

            // Continue running
            lastStatus = task.execute(combined)
            task.lastTickExecution = gameTick
            task.lastStatus = lastStatus
        }
        resetTaskInterruptions(tasks)
        return lastStatus
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

internal class Wait(
    private val maxTicks: Int,
    private val conditions: List<() -> Boolean>,
    private val onTimeout: (() -> Unit)? = null,
    private val onComplete: (() -> Unit)? = null
) {
    private var startTick: Int = -1
    private var active = false
    private var completed = false
    private var timedOut = false

    fun start(currentTick: Int) {
        startTick = currentTick
        active = true
        completed = false
        timedOut = false
    }

    fun reset() {
        startTick = -1
        active = false
        completed = false
        timedOut = false
    }

    fun isActive(): Boolean = active
    fun hasCompleted(): Boolean = completed
    fun hasTimedOut(): Boolean = timedOut

    fun check(currentTick: Int): Boolean {
        if (!active) {
            return false
        }

        val elapsed = currentTick - startTick
        val timeout = elapsed >= maxTicks

        val done = conditions.any { it() }

        if (done || timeout) {
            active = false
            completed = done
            timedOut = timeout

            if (done) {
                onComplete?.invoke()
            }
            if (timeout) {
                onTimeout?.invoke()
            }
        }

        // Return true while still waiting
        return !done && !timeout
    }
}

class CombinedWait<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider>(
    private val maxTicks: Int,
    private val conditions: List<(Combined<T, S, C>) -> Boolean>,
    private val onTimeout: ((Combined<T, S, C>) -> Unit)? = null,
    private val onComplete: ((Combined<T, S, C>) -> Unit)? = null
) {
    private var wait: Wait? = null

    fun start(currentTick: Int, combined: Combined<T, S, C>) {
        wait = Wait(
            maxTicks,
            conditions.map { { it(combined) } },
            { onTimeout?.invoke(combined) },
            { onComplete?.invoke(combined) }
        ).apply {
            start(currentTick)
        }
    }

    fun check(currentTick: Int): Boolean = wait?.check(currentTick) ?: false
    fun isActive(): Boolean = wait?.isActive() ?: false
    fun hasCompleted(): Boolean = wait?.hasCompleted() ?: false
    fun hasTimedOut(): Boolean = wait?.hasTimedOut() ?: false
    fun reset() = wait?.reset()
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
            currentState.tasks.forEach {
                it.interrupted = false
                it.lastStatus = TaskStatus.RUNNING
                it.wait?.reset()
            }

            globalTasks.forEach {
                it.interrupted = false
                it.lastStatus = TaskStatus.RUNNING
                it.wait?.reset()
            }

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
        init: ContextData<T, S, C>.() -> Unit = { }
    ) {
        contextData = ContextData(context, service, config).apply(init)
    }

    fun globalTask(init: TaskBuilder<T, S, C>.() -> Unit) {
        globalTasks.add(TaskBuilder<T, S, C>().apply(init).build())
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
            parameters ?: StateTreeParameters(),
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

    fun task(init: TaskBuilder<T, S, C>.() -> Unit) {
        tasks.add(TaskBuilder<T, S, C>().apply(init).build())
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
class WaitBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    var maxTicks: Int = 0

    private val conditions = mutableListOf<(Combined<T, S, C>) -> Boolean>()
    private var onTimeout: ((Combined<T, S, C>) -> Unit)? = null
    private var onComplete: ((Combined<T, S, C>) -> Unit)? = null

    fun maxTicks(ticks: Int) {
        maxTicks = ticks
    }

    fun until(predicate: (Combined<T, S, C>) -> Boolean) {
        conditions.add(predicate)
    }

    fun onTimeout(block: (Combined<T, S, C>) -> Unit) {
        onTimeout = block
    }

    fun onComplete(block: (Combined<T, S, C>) -> Unit) {
        onComplete = block
    }

    fun build(): CombinedWait<T, S, C> {
        return CombinedWait(maxTicks, conditions.toList(), onTimeout, onComplete)
    }
}

@StateTreeDsl
class TaskBuilder<T : IContextProvider, S : IServiceProvider, C : IConfigurationProvider> {
    private var name: String = ""
    private var actionFunc: (Combined<T, S, C>) -> Boolean? = { null }
    private var wait: CombinedWait<T, S, C>? = null

    fun name(name: String) {
        this.name = name
    }

    fun action(block: (Combined<T, S, C>) -> Boolean?) {
        this.actionFunc = block
    }

    fun wait(block: WaitBuilder<T, S, C>.() -> Unit) {
        this.wait = WaitBuilder<T, S, C>().apply(block).build()
    }

    fun build(): ITask<T, S, C> {
        return buildTask(name, wait) { combined, _ ->
            val wait = wait ?: return@buildTask if (actionFunc(combined) == true) TaskStatus.SUCCESS else TaskStatus.FAILURE
            val now = GameTick.now()

            // If we're already waiting
            if (wait.isActive()) {
                Log.info("Waiting active: ($name)")

                if (wait.check(now)) {
                    return@buildTask TaskStatus.RUNNING
                }

                // Wait completed or timed out
                return@buildTask when {
                    wait.hasCompleted() -> {
                        wait.reset()
                        TaskStatus.SUCCESS
                    }

                    wait.hasTimedOut() -> {
                        wait.reset()
                        TaskStatus.FAILURE
                    }

                    else -> TaskStatus.RUNNING
                }
            }

            // If not waiting yet, perform the action
            if (actionFunc(combined) == true) {
                wait.start(now, combined)
                TaskStatus.RUNNING
            } else {
                TaskStatus.FAILURE
            }
        }
    }
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
    wait: CombinedWait<T, S ,C>? = null,
    executeFunc: (Combined<T, S, C>, ITask<T, S, C>) -> TaskStatus
): ITask<T, S, C> {
    return object : ITask<T, S, C> {
        override val name: String = name
        override var wait: CombinedWait<T, S, C>? = wait
        override var lastStatus: TaskStatus = TaskStatus.RUNNING
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