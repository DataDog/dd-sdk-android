/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic

import android.util.Log
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.observability.ObservabilityLogger
import com.datadog.benchmark.sample.ui.LogPayloadSize
import com.datadog.benchmark.sample.ui.createLogAttributes
import com.datadog.benchmark.sample.ui.logsheavytraffic.di.LogsHeavyTrafficScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed interface LogsHeavyTrafficScreenAction {
    data class VisibleItemsChanged(val items: Set<String>) : LogsHeavyTrafficScreenAction
    data class ChangeLogMessage(val newLogMessage: String) : LogsHeavyTrafficScreenAction
    data class SelectLogLevel(val logLevel: Int) : LogsHeavyTrafficScreenAction
    data class SelectPayloadSize(val payloadSize: LogPayloadSize) : LogsHeavyTrafficScreenAction
    object IncreaseLogsPerImage : LogsHeavyTrafficScreenAction
    object DecreaseLogsPerImage : LogsHeavyTrafficScreenAction
    object OpenSettings : LogsHeavyTrafficScreenAction
    object CloseSettings : LogsHeavyTrafficScreenAction
}

internal data class LogsHeavyTrafficScreenState(
    val imageUrls: List<String>,
    val loggingConfig: LoggingConfig,
    val visibleItems: Set<String> = emptySet()
) {
    data class LoggingConfig(
        val logMessage: String,
        val logLevel: Int,
        val payloadSize: LogPayloadSize,
        val logsPerImage: Int
    ) {
        companion object {
            val INITIAL = LoggingConfig(
                logLevel = Log.INFO,
                payloadSize = LogPayloadSize.Small,
                logsPerImage = 10,
                logMessage = "Hello from Android Benchmarking App!"
            )
        }
    }

    companion object {
        val INITIAL = LogsHeavyTrafficScreenState(
            imageUrls = List(1000) { "https://picsum.photos/800/600?random=$it" },
            loggingConfig = LoggingConfig.INITIAL
        )
    }
}

@LogsHeavyTrafficScope
internal class LogsHeavyTrafficViewModel @Inject constructor(
    private val logger: ObservabilityLogger,
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
    private val defaultDispatcher: CoroutineDispatcher,
    private val navigationManager: LogsHeavyTrafficNavigationManager,
    private val viewModelScope: CoroutineScope
) {
    private val actions = Channel<LogsHeavyTrafficScreenAction>(UNLIMITED)

    private val _logsHeavyTrafficState: StateFlow<LogsHeavyTrafficScreenState> = actions
        .receiveAsFlow()
        .scan(LogsHeavyTrafficScreenState.INITIAL, ::processAction)
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = LogsHeavyTrafficScreenState.INITIAL
        )

    val logsHeavyTrafficState: StateFlow<LogsHeavyTrafficScreenState> = _logsHeavyTrafficState

    fun dispatch(action: LogsHeavyTrafficScreenAction) {
        actions.trySend(action)
    }

    private fun processAction(
        prev: LogsHeavyTrafficScreenState,
        action: LogsHeavyTrafficScreenAction
    ): LogsHeavyTrafficScreenState {
        return when (action) {
            is LogsHeavyTrafficScreenAction.ChangeLogMessage -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(logMessage = action.newLogMessage)
            )
            LogsHeavyTrafficScreenAction.DecreaseLogsPerImage -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(
                    logsPerImage = (prev.loggingConfig.logsPerImage - LOGS_PER_IMAGE_STEP)
                        .coerceAtLeast(1)
                )
            )
            LogsHeavyTrafficScreenAction.IncreaseLogsPerImage -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(
                    logsPerImage = prev.loggingConfig.logsPerImage + LOGS_PER_IMAGE_STEP
                )
            )
            is LogsHeavyTrafficScreenAction.SelectLogLevel -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(logLevel = action.logLevel)
            )
            is LogsHeavyTrafficScreenAction.SelectPayloadSize -> prev.copy(
                loggingConfig = prev.loggingConfig.copy(payloadSize = action.payloadSize)
            )
            is LogsHeavyTrafficScreenAction.VisibleItemsChanged -> {
                val newItems = action.items - prev.visibleItems
                if (newItems.isNotEmpty()) {
                    repeat(newItems.size) {
                        logger.logConfig(prev.loggingConfig)
                    }
                }
                prev.copy(visibleItems = action.items)
            }
            LogsHeavyTrafficScreenAction.CloseSettings -> {
                viewModelScope.launch { navigationManager.closeSettings() }
                prev
            }
            LogsHeavyTrafficScreenAction.OpenSettings -> {
                viewModelScope.launch { navigationManager.openSettings() }
                prev
            }
        }
    }
}

private fun ObservabilityLogger.logConfig(config: LogsHeavyTrafficScreenState.LoggingConfig) {
    repeat(config.logsPerImage) {
        log(
            priority = config.logLevel,
            message = config.logMessage,
            attributes = config.payloadSize.createLogAttributes()
        )
    }
}

private const val LOGS_PER_IMAGE_STEP = 10
