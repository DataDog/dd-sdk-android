/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import java.util.Locale

internal class TelemetryEventHandler(
    internal val sdkVersion: String,
    private val sourceProvider: RumEventSourceProvider,
    private val timeProvider: TimeProvider,
    internal val eventSampler: Sampler
) : RumSessionListener {

    private val seenInCurrentSession = mutableSetOf<EventIdentity>()

    @WorkerThread
    fun handleEvent(event: RumRawEvent.SendTelemetry, writer: DataWriter<Any>) {
        if (!canWrite(event)) return

        seenInCurrentSession.add(event.identity)

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

    override fun onSessionStarted(sessionId: String, isDiscarded: Boolean) {
        seenInCurrentSession.clear()
    }

    // region private

    private fun canWrite(event: RumRawEvent.SendTelemetry): Boolean {
        if (!eventSampler.sample()) return false

        val eventIdentity = event.identity

        if (seenInCurrentSession.contains(eventIdentity)) {
            sdkLogger.i(ALREADY_SEEN_EVENT_MESSAGE.format(Locale.US, eventIdentity))
            return false
        }

        if (seenInCurrentSession.size == MAX_EVENTS_PER_SESSION) {
            sdkLogger.i(MAX_EVENT_NUMBER_REACHED_MESSAGE)
            return false
        }

        return true
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
            service = TELEMETRY_SERVICE_NAME,
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
            service = TELEMETRY_SERVICE_NAME,
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

    private val RumRawEvent.SendTelemetry.identity: EventIdentity
        get() {
            val throwableClass = if (throwable != null) throwable::class.java else null
            return EventIdentity(message, throwableClass)
        }

    internal data class EventIdentity(val message: String, val throwableClass: Class<*>?)

    // endregion

    companion object {
        const val MAX_EVENTS_PER_SESSION = 100
        const val ALREADY_SEEN_EVENT_MESSAGE =
            "Already seen telemetry event with identity=%s, rejecting."
        const val MAX_EVENT_NUMBER_REACHED_MESSAGE =
            "Max number of telemetry events per session reached, rejecting."
        const val TELEMETRY_SERVICE_NAME = "dd-sdk-android"
    }
}
