/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rummanual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal sealed interface RumManualScenarioScreenAction {
    data class SelectEventType(val newEventType: RumManualScenarioState.RumEventType) : RumManualScenarioScreenAction
    data class ChangeViewName(val newViewName: String) : RumManualScenarioScreenAction
    data class SelectActionType(val newActionType: RumActionType) : RumManualScenarioScreenAction
    data class ChangeActionUrl(val newActionUrl: String) : RumManualScenarioScreenAction
    object IncreaseEventsPerBatch : RumManualScenarioScreenAction
    object DecreaseEventsPerBatch : RumManualScenarioScreenAction
    object IncreaseEventsInterval : RumManualScenarioScreenAction
    object DecreaseEventsInterval : RumManualScenarioScreenAction
    data class SetIsRepeating(val isRepeating: Boolean) : RumManualScenarioScreenAction
    data class ChangeResourceUrl(val newResourceUrl: String) : RumManualScenarioScreenAction
    data class ChangeErrorMessage(val newErrorMessage: String) : RumManualScenarioScreenAction
    object SendClicked : RumManualScenarioScreenAction
    object CancelClicked : RumManualScenarioScreenAction
    object OneEventSent : RumManualScenarioScreenAction
    data class EventSendingTaskFinished(
        val task: RumManualScenarioState.EventSendingTask
    ) : RumManualScenarioScreenAction
}

internal data class RumManualScenarioState(
    val config: Config,
    val eventsSent: Int,
    val sendingTask: Pair<EventSendingTask, Job>?
) {
    class EventSendingTask(val config: Config)

    data class Config(
        val eventType: RumEventType,
        val viewName: String,
        val actionType: RumActionType,
        val actionUrl: String,
        val resourceUrl: String,
        val errorMessage: String,
        val eventsPerBatch: Int,
        val eventsIntervalSeconds: Int,
        val isRepeating: Boolean
    )

    enum class RumEventType {
        VIEW,
        ACTION,
        RESOURCE,
        ERROR
    }

    companion object {
        val INITIAL = RumManualScenarioState(
            eventsSent = 0,
            config = Config(
                eventType = RumEventType.VIEW,
                viewName = "FooFragment",
                actionType = RumActionType.TAP,
                actionUrl = "actionEventTitle",
                resourceUrl = "https://api.shopist.io/checkout.json",
                errorMessage = "Android benchmark debug error message",
                eventsPerBatch = 5,
                eventsIntervalSeconds = 2,
                isRepeating = false
            ),
            sendingTask = null
        )
    }
}

internal class RumManualScenarioViewModel(
    private val rumMonitor: RumMonitor,
    private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val actions = Channel<RumManualScenarioScreenAction>(UNLIMITED)

    val rumManualScenarioState: StateFlow<RumManualScenarioState> = actions
        .receiveAsFlow()
        .scan(RumManualScenarioState.INITIAL, ::processAction)
        .onStart {
            startRootRumView()
        }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = RumManualScenarioState.INITIAL
        )

    fun dispatch(action: RumManualScenarioScreenAction) {
        actions.trySend(action)
    }

    private fun processAction(
        prev: RumManualScenarioState,
        action: RumManualScenarioScreenAction
    ): RumManualScenarioState {
        return prev.copy(
            config = processConfig(prev.config, action),
            eventsSent = processEventsSent(prev, action),
            sendingTask = processSendingTask(prev, action)
        )
    }

    private fun processSendingTask(
        prev: RumManualScenarioState,
        action: RumManualScenarioScreenAction
    ): Pair<RumManualScenarioState.EventSendingTask, Job>? {
        return when (action) {
            is RumManualScenarioScreenAction.SendClicked -> {
                if (prev.sendingTask == null) {
                    val newTask = RumManualScenarioState.EventSendingTask(prev.config)
                    val newJob = launchSendingJob(newTask)

                    newTask to newJob
                } else {
                    null
                }
            }
            is RumManualScenarioScreenAction.CancelClicked -> {
                if (prev.sendingTask != null) {
                    val (_, job) = prev.sendingTask
                    job.cancel()
                }
                null
            }
            is RumManualScenarioScreenAction.EventSendingTaskFinished -> {
                if (prev.sendingTask != null) {
                    val (task, _) = prev.sendingTask
                    if (task === action.task) {
                        null
                    } else {
                        prev.sendingTask
                    }
                } else {
                    null
                }
            }
            else -> prev.sendingTask
        }
    }

    private fun processConfig(
        prev: RumManualScenarioState.Config,
        action: RumManualScenarioScreenAction
    ): RumManualScenarioState.Config {
        return when (action) {
            is RumManualScenarioScreenAction.SelectEventType -> prev.copy(eventType = action.newEventType)
            is RumManualScenarioScreenAction.ChangeViewName -> prev.copy(viewName = action.newViewName)
            is RumManualScenarioScreenAction.SelectActionType -> prev.copy(actionType = action.newActionType)
            is RumManualScenarioScreenAction.ChangeActionUrl -> prev.copy(actionUrl = action.newActionUrl)
            is RumManualScenarioScreenAction.IncreaseEventsPerBatch ->
                prev.copy(eventsPerBatch = prev.eventsPerBatch + EVENTS_PER_BATCH_STEP)
            is RumManualScenarioScreenAction.DecreaseEventsPerBatch ->
                prev.copy(eventsPerBatch = (prev.eventsPerBatch - EVENTS_PER_BATCH_STEP).coerceAtLeast(1))
            is RumManualScenarioScreenAction.IncreaseEventsInterval ->
                prev.copy(eventsIntervalSeconds = prev.eventsIntervalSeconds + EVENTS_INTERVAL_STEP)
            is RumManualScenarioScreenAction.DecreaseEventsInterval ->
                prev.copy(
                    eventsIntervalSeconds = (prev.eventsIntervalSeconds - EVENTS_INTERVAL_STEP)
                        .coerceAtLeast(1)
                )
            is RumManualScenarioScreenAction.SetIsRepeating -> prev.copy(isRepeating = action.isRepeating)
            is RumManualScenarioScreenAction.ChangeResourceUrl -> prev.copy(resourceUrl = action.newResourceUrl)
            is RumManualScenarioScreenAction.ChangeErrorMessage -> prev.copy(errorMessage = action.newErrorMessage)
            else -> prev
        }
    }

    private fun launchSendingJob(task: RumManualScenarioState.EventSendingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            if (task.config.isRepeating) {
                while (true) {
                    sendBatch(task)
                    delay(task.config.eventsIntervalSeconds.seconds)
                }
            } else {
                sendBatch(task)
            }
            dispatch(RumManualScenarioScreenAction.EventSendingTaskFinished(task))
        }
    }

    private suspend fun sendBatch(task: RumManualScenarioState.EventSendingTask) {
        repeat(task.config.eventsPerBatch) {
            withContext(NonCancellable) {
                sendOneEvent(task.config)
                dispatch(RumManualScenarioScreenAction.OneEventSent)
            }
            yield()
        }
    }

    private suspend fun sendOneEvent(config: RumManualScenarioState.Config) {
        when (config.eventType) {
            RumManualScenarioState.RumEventType.VIEW -> {
                withNewRumView(config) {
                    delay(200.milliseconds)
                }
            }
            RumManualScenarioState.RumEventType.ACTION -> {
                rumMonitor.addAction(
                    type = config.actionType,
                    name = config.actionUrl
                )
                delay(100.milliseconds)
            }
            RumManualScenarioState.RumEventType.RESOURCE -> {
                rumMonitor.startResource(
                    key = RUM_RESOURCE_KEY,
                    method = RumResourceMethod.GET,
                    url = config.resourceUrl
                )
                delay(100.milliseconds)
                rumMonitor.stopResource(
                    key = RUM_RESOURCE_KEY,
                    statusCode = 200,
                    size = 1024,
                    kind = RumResourceKind.IMAGE
                )
            }
            RumManualScenarioState.RumEventType.ERROR -> {
                rumMonitor.addError(
                    message = config.errorMessage,
                    source = RumErrorSource.SOURCE,
                    throwable = null
                )
                delay(100.milliseconds)
            }
        }
    }

    private suspend fun withNewRumView(
        config: RumManualScenarioState.Config,
        block: suspend () -> Unit
    ) {
        val key = RumViewKey()

        rumMonitor.startView(key = key, name = config.viewName)
        block()
        rumMonitor.stopView(key = key)

        startRootRumView()
    }

    private fun startRootRumView() {
        rumMonitor.startView(key = RootRumViewKey, name = ROOT_RUM_VIEW_NAME)
    }

    private fun processEventsSent(
        prev: RumManualScenarioState,
        action: RumManualScenarioScreenAction
    ): Int {
        return when (action) {
            is RumManualScenarioScreenAction.OneEventSent -> prev.eventsSent + 1
            else -> prev.eventsSent
        }
    }
}

private const val EVENTS_PER_BATCH_STEP = 5
private const val EVENTS_INTERVAL_STEP = 1
private const val RUM_RESOURCE_KEY = "/resource/1"
private const val ROOT_RUM_VIEW_NAME = "RootRumView"

private class RumViewKey
private object RootRumViewKey
