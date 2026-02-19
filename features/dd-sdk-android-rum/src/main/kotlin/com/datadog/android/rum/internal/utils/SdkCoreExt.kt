/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor

internal typealias EventOutcomeAction = (rumMonitor: AdvancedRumMonitor) -> Unit

internal class WriteOperation(
    private val sdkCore: FeatureSdkCore,
    private val datadogContext: DatadogContext,
    private val writeScope: EventWriteScope,
    private val rumDataWriter: DataWriter<Any>,
    private val eventType: EventType,
    private val eventSource: () -> Any
) {
    private val logger = DdSdkAndroidRumLogger(sdkCore.internalLogger)
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
        writeScope {
            if (rumDataWriter is NoOpDataWriter) {
                logger.logWriteOperationIgnored()
                advancedRumMonitor?.let { onError(it) }
            } else {
                try {
                    val event = eventSource()
                    val isSuccess = rumDataWriter.write(it, event, eventType)
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
        if (exception != null) {
            logger.logWriteOperationFailedWithException(throwable = exception)
        } else {
            logger.logWriteOperationFailed()
        }

        advancedRumMonitor?.let {
            if (onError == NO_OP_EVENT_OUTCOME_ACTION) {
                logger.logNoErrorCallbackProvided()
            }
            onError(it)
        }
    }

    internal companion object {
        val NO_OP_EVENT_OUTCOME_ACTION: EventOutcomeAction = {}
    }
}

internal fun FeatureSdkCore.newRumEventWriteOperation(
    datadogContext: DatadogContext,
    writeScope: EventWriteScope,
    rumDataWriter: DataWriter<Any>,
    eventType: EventType = EventType.DEFAULT,
    eventSource: () -> Any
): WriteOperation {
    return WriteOperation(this, datadogContext, writeScope, rumDataWriter, eventType, eventSource)
}
