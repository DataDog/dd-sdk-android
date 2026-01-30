/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logscustom

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.benchmark.sample.observability.ObservabilityLogger
import com.datadog.benchmark.sample.ui.LogPayloadSize
import com.datadog.benchmark.sample.ui.createLogAttributes
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
import kotlin.time.Duration.Companion.seconds

internal sealed interface LogsScreenAction {
    data class ChangeLogMessage(val newLogMessage: String) : LogsScreenAction
    data class SelectLogLevel(val logLevel: Int) : LogsScreenAction
    data class SelectPayloadSize(val payloadSize: LogPayloadSize) : LogsScreenAction
    object IncreaseLoggingSpeed : LogsScreenAction
    object DecreaseLoggingSpeed : LogsScreenAction
    object IncreaseLoggingInterval : LogsScreenAction
    object DecreaseLoggingInterval : LogsScreenAction
    data class SetRepeatLogging(val repeatLogging: Boolean) : LogsScreenAction
    object LogButtonClicked : LogsScreenAction
    data class LogJobFinished(val loggingTask: LogsScreenState.LoggingTask) : LogsScreenAction
}

internal data class LogsScreenState(
    val config: LoggingConfig,
    val loggingTask: Pair<LoggingTask, Job>?
) {
    internal class LoggingTask(val config: LoggingConfig)

    data class LoggingConfig(
        val logMessage: String,
        val logLevel: Int,
        val payloadSize: LogPayloadSize,
        val logsPerBatch: Int,
        val loggingIntervalSeconds: Int,
        val repeatLogging: Boolean
    ) {
        companion object {
            val INITIAL = LoggingConfig(
                logLevel = Log.INFO,
                payloadSize = LogPayloadSize.Small,
                logsPerBatch = 10,
                loggingIntervalSeconds = 5,
                repeatLogging = false,
                logMessage = "Hello from Android Benchmarking App!"
            )
        }
    }

    companion object {
        val INITIAL = LogsScreenState(
            config = LoggingConfig.INITIAL,
            loggingTask = null
        )
    }
}

internal class LogsScreenViewModel(
    private val logger: ObservabilityLogger,
    private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val actions: Channel<LogsScreenAction> = Channel(capacity = UNLIMITED)

    private val _logsScreenState: StateFlow<LogsScreenState> = actions
        .receiveAsFlow()
        .scan(LogsScreenState.INITIAL, ::processAction)
        .flowOn(defaultDispatcher)
        .stateIn(scope = viewModelScope, started = SharingStarted.Lazily, initialValue = LogsScreenState.INITIAL)

    val logsScreenState: StateFlow<LogsScreenState> = _logsScreenState

    fun dispatch(action: LogsScreenAction) {
        actions.trySend(action)
    }

    private fun processAction(prev: LogsScreenState, action: LogsScreenAction): LogsScreenState {
        return when (action) {
            is LogsScreenAction.ChangeLogMessage -> prev.modifyConfig { copy(logMessage = action.newLogMessage) }
            LogsScreenAction.DecreaseLoggingInterval -> prev.modifyConfig {
                val newValue = (loggingIntervalSeconds - LOGGING_INTERVAL_STEP).coerceAtLeast(1)
                copy(loggingIntervalSeconds = newValue)
            }

            LogsScreenAction.DecreaseLoggingSpeed -> prev.modifyConfig {
                copy(logsPerBatch = (logsPerBatch - LOGS_PER_BATCH_STEP).coerceAtLeast(1))
            }

            LogsScreenAction.IncreaseLoggingInterval -> prev.modifyConfig {
                copy(loggingIntervalSeconds = loggingIntervalSeconds + LOGGING_INTERVAL_STEP)
            }

            LogsScreenAction.IncreaseLoggingSpeed -> prev.modifyConfig {
                copy(logsPerBatch = logsPerBatch + LOGS_PER_BATCH_STEP)
            }
            is LogsScreenAction.SelectLogLevel -> prev.modifyConfig { copy(logLevel = action.logLevel) }
            is LogsScreenAction.SelectPayloadSize -> prev.modifyConfig { copy(payloadSize = action.payloadSize) }
            is LogsScreenAction.SetRepeatLogging -> prev.modifyConfig { copy(repeatLogging = action.repeatLogging) }
            LogsScreenAction.LogButtonClicked -> {
                if (prev.loggingTask != null) {
                    val (_, job) = prev.loggingTask
                    job.cancel()
                    prev.copy(loggingTask = null)
                } else {
                    val task = LogsScreenState.LoggingTask(prev.config)
                    prev.copy(
                        loggingTask = task to launchLoggingJob(task)
                    )
                }
            }

            is LogsScreenAction.LogJobFinished -> {
                if (prev.loggingTask != null) {
                    val (loggingTask, _) = prev.loggingTask
                    if (loggingTask === action.loggingTask) {
                        prev.copy(loggingTask = null)
                    } else {
                        prev
                    }
                } else {
                    prev
                }
            }
        }
    }

    private fun launchLoggingJob(loggingTask: LogsScreenState.LoggingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            if (loggingTask.config.repeatLogging) {
                while (true) {
                    logger.logConfig(loggingTask.config)
                    delay(loggingTask.config.loggingIntervalSeconds.seconds)
                }
            } else {
                logger.logConfig(loggingTask.config)
            }
            dispatch(LogsScreenAction.LogJobFinished(loggingTask))
        }
    }
}

private fun ObservabilityLogger.logConfig(config: LogsScreenState.LoggingConfig) {
    repeat(config.logsPerBatch) {
        log(
            priority = config.logLevel,
            message = config.logMessage,
            attributes = config.payloadSize.createLogAttributes()
        )
    }
}

private fun LogsScreenState.modifyConfig(
    block: LogsScreenState.LoggingConfig.() -> LogsScreenState.LoggingConfig
): LogsScreenState {
    return copy(
        config = config.block()
    )
}

private const val LOGS_PER_BATCH_STEP = 10
private const val LOGGING_INTERVAL_STEP = 1
