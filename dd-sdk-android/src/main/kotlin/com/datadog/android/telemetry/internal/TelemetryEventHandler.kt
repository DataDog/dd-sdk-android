/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.tryFromSource
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.storage.DataWriter
import java.util.Locale

internal class TelemetryEventHandler(
    internal val sdkCore: SdkCore,
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

        sdkCore.getFeature(RumFeature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val telemetryEvent: Any = when (event.type) {
                    TelemetryType.DEBUG -> {
                        createDebugEvent(datadogContext, timestamp, rumContext, event.message)
                    }
                    TelemetryType.ERROR -> {
                        createErrorEvent(
                            datadogContext,
                            timestamp,
                            rumContext,
                            event.message,
                            event.stack,
                            event.kind
                        )
                    }
                }

                writer.write(eventBatchWriter, telemetryEvent)
            }
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
        datadogContext: DatadogContext,
        timestamp: Long,
        rumContext: RumContext,
        message: String
    ): TelemetryDebugEvent {
        return TelemetryDebugEvent(
            dd = TelemetryDebugEvent.Dd(),
            date = timestamp,
            source = TelemetryDebugEvent.Source.tryFromSource(datadogContext.source)
                ?: TelemetryDebugEvent.Source.ANDROID,
            service = TELEMETRY_SERVICE_NAME,
            version = datadogContext.sdkVersion,
            application = TelemetryDebugEvent.Application(rumContext.applicationId),
            session = TelemetryDebugEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryDebugEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryDebugEvent.Action(it) },
            telemetry = TelemetryDebugEvent.Telemetry(
                message = message
            )
        )
    }

    @Suppress("LongParameterList")
    private fun createErrorEvent(
        datadogContext: DatadogContext,
        timestamp: Long,
        rumContext: RumContext,
        message: String,
        stack: String?,
        kind: String?
    ): TelemetryErrorEvent {
        return TelemetryErrorEvent(
            dd = TelemetryErrorEvent.Dd(),
            date = timestamp,
            source = TelemetryErrorEvent.Source.tryFromSource(datadogContext.source)
                ?: TelemetryErrorEvent.Source.ANDROID,
            service = TELEMETRY_SERVICE_NAME,
            version = datadogContext.sdkVersion,
            application = TelemetryErrorEvent.Application(rumContext.applicationId),
            session = TelemetryErrorEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryErrorEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryErrorEvent.Action(it) },
            telemetry = TelemetryErrorEvent.Telemetry(
                message = message,
                error = if (stack != null || kind != null) {
                    TelemetryErrorEvent.Error(
                        stack = stack,
                        kind = kind
                    )
                } else {
                    null
                }
            )
        )
    }

    private val RumRawEvent.SendTelemetry.identity: EventIdentity
        get() {
            return EventIdentity(message, kind)
        }

    internal data class EventIdentity(val message: String, val kind: String?)

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
