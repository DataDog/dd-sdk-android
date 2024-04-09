/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor

internal typealias EventOutcomeAction = (rumMonitor: AdvancedRumMonitor) -> Unit

internal class WriteOperation(
    private val sdkCore: FeatureSdkCore,
    private val rumDataWriter: DataWriter<Any>,
    private val eventSource: (DatadogContext) -> Any
) {
    private val advancedRumMonitor = GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor
    private var onError: EventOutcomeAction = NO_OP_EVENT_OUTCOME_ACTION
    private var onSuccess: EventOutcomeAction = NO_OP_EVENT_OUTCOME_ACTION

    /**
     * Invoked if write operation failed. Invocation is done on the worker thread.
     */
    fun onError(action: EventOutcomeAction): WriteOperation {
        onError = action
        return this
    }

    /**
     * Invoked if write operation failed. Invocation is done on the worker thread.
     */
    fun onSuccess(action: EventOutcomeAction): WriteOperation {
        onSuccess = action
        return this
    }

    fun submit() {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                if (rumDataWriter is NoOpDataWriter) {
                    sdkCore.internalLogger.log(
                        level = InternalLogger.Level.INFO,
                        target = InternalLogger.Target.USER,
                        messageBuilder = { WRITE_OPERATION_IGNORED }
                    )
                    advancedRumMonitor?.let { onError(it) }
                } else {
                    try {
                        val event = eventSource(datadogContext)

                        @Suppress("ThreadSafety") // called in a worker thread context
                        val isSuccess = rumDataWriter.write(eventBatchWriter, event)
                        if (isSuccess) {
                            advancedRumMonitor?.let {
                                onSuccess(it)
                            }
                        } else {
                            notifyEventWriteFailure()
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        notifyEventWriteFailure(e)
                    }
                }
            }
    }

    private fun notifyEventWriteFailure(exception: Exception? = null) {
        val targets = mutableListOf(InternalLogger.Target.USER).apply {
            // if no exception, no need to notify telemetry, probably we already handled failure
            // internally and sent it to telemetry
            if (exception != null) add(InternalLogger.Target.TELEMETRY)
        }
        sdkCore.internalLogger.log(
            level = InternalLogger.Level.ERROR,
            targets = targets,
            messageBuilder = { WRITE_OPERATION_FAILED_ERROR },
            throwable = exception
        )

        advancedRumMonitor?.let {
            if (onError == NO_OP_EVENT_OUTCOME_ACTION) {
                sdkCore.internalLogger.log(
                    level = InternalLogger.Level.WARN,
                    target = InternalLogger.Target.MAINTAINER,
                    { NO_ERROR_CALLBACK_PROVIDED_WARNING }
                )
            }
            onError(it)
        }
    }

    internal companion object {
        const val WRITE_OPERATION_IGNORED = "Write operation ignored, session is expired or RUM feature is disabled."
        const val WRITE_OPERATION_FAILED_ERROR = "Write operation failed."
        const val NO_ERROR_CALLBACK_PROVIDED_WARNING =
            "Write operation failed, but no onError callback was provided."
        val NO_OP_EVENT_OUTCOME_ACTION: EventOutcomeAction = {}
    }
}

internal fun FeatureSdkCore.newRumEventWriteOperation(
    rumDataWriter: DataWriter<Any>,
    eventSource: (DatadogContext) -> Any
): WriteOperation {
    return WriteOperation(this, rumDataWriter, eventSource)
}
