/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.android.log.Logger
import com.datadog.benchmark.sample.ui.LogPayloadSize
import com.datadog.benchmark.sample.ui.createLogAttributes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

internal sealed interface LogsHeavyTrafficScreenAction{
    data class VisibleItemsChanged(val items: Set<String>): LogsHeavyTrafficScreenAction
    data class ChangeLogMessage(val newLogMessage: String) : LogsHeavyTrafficScreenAction
    data class SelectLogLevel(val logLevel: Int) : LogsHeavyTrafficScreenAction
    data class SelectPayloadSize(val payloadSize: LogPayloadSize) : LogsHeavyTrafficScreenAction
    object IncreaseLogsPerImage : LogsHeavyTrafficScreenAction
    object DecreaseLogsPerImage : LogsHeavyTrafficScreenAction
}

internal data class LogsHeavyTrafficScreenState(
    val imageUrls: List<String>,
    val loggingConfig: LoggingConfig,
) {
    data class LoggingConfig(
        val logMessage: String,
        val logLevel: Int,
        val payloadSize: LogPayloadSize,
        val logsPerImage: Int,
    ) {
        companion object {
            val INITIAL = LoggingConfig(
                logLevel = Log.INFO,
                payloadSize = LogPayloadSize.Small,
                logsPerImage = 5,
                logMessage = "Hello from Android Benchmarking App!"
            )
        }
    }

    companion object {
        val INITIAL = LogsHeavyTrafficScreenState(
            imageUrls = List(1000) { "https://picsum.photos/800/600?random=${it}" },
            loggingConfig = LoggingConfig.INITIAL
        )
    }
}

internal class LogsHeavyTrafficViewModel(
    private val logger: Logger,
    private val defaultDispatcher: CoroutineDispatcher,
): ViewModel() {

    private val actions = Channel<LogsHeavyTrafficScreenAction>(UNLIMITED)

    private val statesPipeline: StateFlow<LogsHeavyTrafficScreenState> = actions
        .receiveAsFlow()
        .scan(LogsHeavyTrafficScreenState.INITIAL, ::processAction)
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = LogsHeavyTrafficScreenState.INITIAL
        )

    init {
        actions
            .receiveAsFlow()
            .filterIsInstance<LogsHeavyTrafficScreenAction.VisibleItemsChanged>()
            .zipWithNext()
            .onEach { (prev, cur) ->
                val prevSet: Set<String> = prev?.items ?: emptySet()
                val newItems = cur.items - prevSet
                if (newItems.isNotEmpty()) {
                    logger.logConfig(statesPipeline.value.loggingConfig)
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(viewModelScope)
    }

    fun states(): StateFlow<LogsHeavyTrafficScreenState> {
        return statesPipeline
    }

    fun dispatch(action: LogsHeavyTrafficScreenAction) {
        actions.trySend(action)
    }

    private fun processAction(
        prev: LogsHeavyTrafficScreenState, action: LogsHeavyTrafficScreenAction
    ): LogsHeavyTrafficScreenState {
        return when (action) {
            is LogsHeavyTrafficScreenAction.ChangeLogMessage -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(logMessage = action.newLogMessage)
            )
            LogsHeavyTrafficScreenAction.DecreaseLogsPerImage -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(
                    logsPerImage = (prev.loggingConfig.logsPerImage - 1).coerceAtLeast(1)
                )
            )
            LogsHeavyTrafficScreenAction.IncreaseLogsPerImage -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(
                    logsPerImage = prev.loggingConfig.logsPerImage + 1
                )
            )
            is LogsHeavyTrafficScreenAction.SelectLogLevel -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(logLevel = action.logLevel)
            )
            is LogsHeavyTrafficScreenAction.SelectPayloadSize -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(payloadSize = action.payloadSize)
            )
            is LogsHeavyTrafficScreenAction.VisibleItemsChanged -> {
                prev
            }
        }
    }
}

private fun Logger.logConfig(config: LogsHeavyTrafficScreenState.LoggingConfig) {
    repeat(config.logsPerImage) {
        log(
            priority = config.logLevel,
            message = config.logMessage,
            attributes = config.payloadSize.createLogAttributes()
        )
    }
}

// TODO WAHAHA move to utils?
private fun <T> Flow<T>.zipWithNext(): Flow<Pair<T?, T>> = flow {
    var prev: T? = null
    collect {
        emit(prev to it)
        prev = it
    }
}
