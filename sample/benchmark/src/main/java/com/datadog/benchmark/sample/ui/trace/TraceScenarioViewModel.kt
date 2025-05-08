/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("MagicNumber")

package com.datadog.benchmark.sample.ui.trace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.trace.api.DDTags
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface TraceScenarioScreenAction {
    data class ChangeSpanOperation(val newSpanOperation: String) : TraceScenarioScreenAction
    data class ChangeSpanResource(val newSpanResource: String) : TraceScenarioScreenAction
    data class SetIsError(val isError: Boolean) : TraceScenarioScreenAction
    object IncreaseChildrenCount : TraceScenarioScreenAction
    object DecreaseChildrenCount : TraceScenarioScreenAction
    object IncreaseDepth : TraceScenarioScreenAction
    object DecreaseDepth : TraceScenarioScreenAction
    object IncreaseChildDelay : TraceScenarioScreenAction
    object DecreaseChildDelay : TraceScenarioScreenAction
    object SendButtonClicked : TraceScenarioScreenAction
    object CancelButtonClicked : TraceScenarioScreenAction
    data class FinishTracingTask(val task: TraceScenarioScreenState.TracingTask) : TraceScenarioScreenAction
}

internal data class TraceScenarioScreenState(
    val config: TracingConfig,
    val tracingTasksMap: Map<TracingTask, Job>,
    val tracesSent: Int
) {
    class TracingTask(val config: TracingConfig)

    data class TracingConfig(
        val isError: Boolean,
        val spanOperation: String,
        val spanResource: String,
        val childrenCount: Int,
        val depth: Int,
        val childDelayMillis: Int
    )

    companion object {
        val INITIAL = TraceScenarioScreenState(
            config = TracingConfig(
                isError = false,
                spanOperation = "Android Benchmark span operation",
                spanResource = "Android Benchmark span resource",
                childrenCount = 0,
                depth = 1,
                childDelayMillis = 100
            ),
            tracingTasksMap = emptyMap(),
            tracesSent = 0
        )
    }
}

internal class TraceScenarioViewModel(
    private val tracer: Tracer,
    private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val actions = Channel<TraceScenarioScreenAction>(UNLIMITED)

    private val _traceScenarioState: StateFlow<TraceScenarioScreenState> = actions
        .receiveAsFlow()
        .scan(TraceScenarioScreenState.INITIAL, ::processAction)
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = TraceScenarioScreenState.INITIAL
        )

    val traceScenarioState: StateFlow<TraceScenarioScreenState> = _traceScenarioState

    fun dispatch(action: TraceScenarioScreenAction) {
        actions.trySend(action)
    }

    private fun processAction(
        prev: TraceScenarioScreenState,
        action: TraceScenarioScreenAction
    ): TraceScenarioScreenState {
        return prev.copy(
            config = processConfig(prev.config, action),
            tracingTasksMap = processTasks(prev, action),
            tracesSent = processTracesSent(prev, action)
        )
    }

    private fun processConfig(
        prev: TraceScenarioScreenState.TracingConfig,
        action: TraceScenarioScreenAction
    ): TraceScenarioScreenState.TracingConfig {
        return when (action) {
            is TraceScenarioScreenAction.ChangeSpanOperation -> {
                prev.copy(spanOperation = action.newSpanOperation)
            }
            is TraceScenarioScreenAction.ChangeSpanResource -> {
                prev.copy(spanResource = action.newSpanResource)
            }
            is TraceScenarioScreenAction.DecreaseChildDelay -> {
                prev.copy(
                    childDelayMillis = (prev.childDelayMillis - CHILD_DELAY_STEP).coerceAtLeast(50)
                )
            }
            is TraceScenarioScreenAction.DecreaseChildrenCount -> {
                prev.copy(childrenCount = (prev.childrenCount - CHILDREN_COUNT_STEP).coerceAtLeast(0))
            }
            is TraceScenarioScreenAction.DecreaseDepth -> {
                prev.copy(depth = (prev.depth - DEPTH_STEP).coerceAtLeast(1))
            }
            is TraceScenarioScreenAction.IncreaseChildDelay -> {
                prev.copy(childDelayMillis = prev.childDelayMillis + CHILD_DELAY_STEP)
            }
            is TraceScenarioScreenAction.IncreaseChildrenCount -> {
                prev.copy(childrenCount = prev.childrenCount + CHILDREN_COUNT_STEP)
            }
            is TraceScenarioScreenAction.IncreaseDepth -> {
                prev.copy(depth = prev.depth + DEPTH_STEP)
            }
            is TraceScenarioScreenAction.SetIsError -> {
                prev.copy(isError = action.isError)
            }
            else -> prev
        }
    }

    private fun processTasks(
        prev: TraceScenarioScreenState,
        action: TraceScenarioScreenAction
    ): Map<TraceScenarioScreenState.TracingTask, Job> {
        return when (action) {
            TraceScenarioScreenAction.SendButtonClicked -> {
                val newTask = TraceScenarioScreenState.TracingTask(prev.config)
                val newJob = launchTracingJob(newTask)

                prev.tracingTasksMap + (newTask to newJob)
            }
            TraceScenarioScreenAction.CancelButtonClicked -> {
                for ((_, job) in prev.tracingTasksMap) {
                    job.cancel()
                }

                emptyMap()
            }
            is TraceScenarioScreenAction.FinishTracingTask -> {
                prev.tracingTasksMap - action.task
            }
            else -> prev.tracingTasksMap
        }
    }

    private fun processTracesSent(prev: TraceScenarioScreenState, action: TraceScenarioScreenAction): Int {
        if (action is TraceScenarioScreenAction.FinishTracingTask) {
            if (action.task in prev.tracingTasksMap) {
                return prev.tracesSent + 1
            }
        }

        return prev.tracesSent
    }

    private fun launchTracingJob(task: TraceScenarioScreenState.TracingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val rootSpan = tracer.spanBuilder(task.config.spanOperation).apply {
                setAttribute(DDTags.RESOURCE_NAME, task.config.spanResource)
            }.startSpan()

            if (task.config.isError) {
                rootSpan.apply {
                    setStatus(StatusCode.ERROR)
                    setAttribute(DDTags.ERROR_TYPE, "simulated_error")
                    setAttribute(DDTags.ERROR_MSG, "Simulated error message")
                }
            }

            delay(task.config.childDelayMillis.milliseconds)

            doChildWork(rootSpan, task.config, task.config.depth)

            rootSpan.end()

            dispatch(TraceScenarioScreenAction.FinishTracingTask(task))
        }
    }

    private suspend fun doChildWork(parent: Span, config: TraceScenarioScreenState.TracingConfig, depthLeft: Int) {
        if (depthLeft == 0) {
            return
        }

        repeat(config.childrenCount) {
            val spanName = "${config.spanOperation} - Child $it at level ${config.depth - depthLeft + 1}"
            val span = tracer.spanBuilder(spanName)
                .setParent(Context.current().with(parent))
                .startSpan()

            delay(config.childDelayMillis.milliseconds)

            doChildWork(span, config, depthLeft - 1)

            span.end()
        }
    }
}

private const val CHILDREN_COUNT_STEP = 1
private const val CHILD_DELAY_STEP = 50
private const val DEPTH_STEP = 1
