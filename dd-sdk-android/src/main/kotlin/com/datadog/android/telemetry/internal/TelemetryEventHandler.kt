/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent

internal class TelemetryEventHandler(
    private val serviceName: String,
    private val sdkVersion: String,
    private val sourceProvider: RumEventSourceProvider,
    private val timeProvider: TimeProvider
) {

    fun handleEvent(event: RumRawEvent.SendTelemetry, writer: DataWriter<Any>) {
        val timestamp = event.eventTime.timestamp + timeProvider.getServerOffsetMillis()

        val rumContext = GlobalRum.getRumContext()

        val telemetryEvent: Any = when (event.type) {
            TelemetryType.DEBUG -> {
                createDebugEvent(timestamp, rumContext, event.message)
            }
            TelemetryType.ERROR -> {
                createErrorEvent(
                    timestamp,
                    rumContext,
                    event.message,
                    event.throwable
                )
            }
        }

        writer.write(telemetryEvent)
    }

    private fun createDebugEvent(
        timestamp: Long,
        rumContext: RumContext,
        message: String
    ): TelemetryDebugEvent {
        return TelemetryDebugEvent(
            dd = TelemetryDebugEvent.Dd(),
            date = timestamp,
            source = sourceProvider.telemetryDebugEventSource
                ?: TelemetryDebugEvent.Source.ANDROID,
            service = serviceName,
            version = sdkVersion,
            application = TelemetryDebugEvent.Application(rumContext.applicationId),
            session = TelemetryDebugEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryDebugEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryDebugEvent.Action(it) },
            telemetry = TelemetryDebugEvent.Telemetry(
                message = message
            )
        )
    }

    private fun createErrorEvent(
        timestamp: Long,
        rumContext: RumContext,
        message: String,
        throwable: Throwable?
    ): TelemetryErrorEvent {
        return TelemetryErrorEvent(
            dd = TelemetryErrorEvent.Dd(),
            date = timestamp,
            source = sourceProvider.telemetryErrorEventSource
                ?: TelemetryErrorEvent.Source.ANDROID,
            service = serviceName,
            version = sdkVersion,
            application = TelemetryErrorEvent.Application(rumContext.applicationId),
            session = TelemetryErrorEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryErrorEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryErrorEvent.Action(it) },
            telemetry = TelemetryErrorEvent.Telemetry(
                message = message,
                error = throwable?.let {
                    TelemetryErrorEvent.Error(
                        stack = it.loggableStackTrace(),
                        kind = it.javaClass.canonicalName ?: it.javaClass.simpleName
                    )
                }
            )
        )
    }
}
