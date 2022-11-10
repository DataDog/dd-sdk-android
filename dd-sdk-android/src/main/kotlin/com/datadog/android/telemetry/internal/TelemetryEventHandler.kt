/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import androidx.annotation.WorkerThread
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.storage.DataWriter
import io.opentracing.util.GlobalTracer
import java.util.Locale

internal class TelemetryEventHandler(
    internal val sdkCore: SdkCore,
    private val timeProvider: TimeProvider,
    internal val eventSampler: Sampler,
    internal val maxEventCountPerSession: Int = MAX_EVENTS_PER_SESSION
) : RumSessionListener {

    private var trackNetworkRequests = false

    private val seenInCurrentSession = mutableSetOf<TelemetryEventId>()

    @WorkerThread
    fun handleEvent(event: RumRawEvent.SendTelemetry, writer: DataWriter<Any>) {
        if (!canWrite(event)) return

        seenInCurrentSession.add(event.identity)

        val timestamp = event.eventTime.timestamp + timeProvider.getServerOffsetMillis()

        val rumContext = GlobalRum.getRumContext()

        sdkCore.getFeature(RumFeature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val telemetryEvent: Any? = when (event.type) {
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
                    TelemetryType.CONFIGURATION -> {
                        if (event.configuration == null) {
                            createErrorEvent(
                                datadogContext,
                                timestamp,
                                rumContext,
                                "Trying to send configuration event with null config",
                                null,
                                null
                            )
                        } else {
                            createConfigurationEvent(
                                datadogContext,
                                timestamp,
                                rumContext,
                                event.configuration
                            )
                        }
                    }
                    TelemetryType.INTERCEPTOR_SETUP -> {
                        trackNetworkRequests = true
                        null
                    }
                }

                if (telemetryEvent != null) {
                    writer.write(eventBatchWriter, telemetryEvent)
                }
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

        if (seenInCurrentSession.size >= maxEventCountPerSession) {
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

    private fun createConfigurationEvent(
        datadogContext: DatadogContext,
        timestamp: Long,
        rumContext: RumContext,
        configuration: Configuration?
    ): TelemetryConfigurationEvent {
        val coreConfig = configuration?.coreConfig
        val traceConfig = configuration?.tracesConfig
        val rumConfig = configuration?.rumConfig
        val crashConfig = configuration?.crashReportConfig
        val viewTrackingStrategy = when (rumConfig?.viewTrackingStrategy) {
            is ActivityViewTrackingStrategy -> TelemetryConfigurationEvent.ViewTrackingStrategy.ACTIVITYVIEWTRACKINGSTRATEGY
            is FragmentViewTrackingStrategy -> TelemetryConfigurationEvent.ViewTrackingStrategy.FRAGMENTVIEWTRACKINGSTRATEGY
            is MixedViewTrackingStrategy -> TelemetryConfigurationEvent.ViewTrackingStrategy.MIXEDVIEWTRACKINGSTRATEGY
            is NavigationViewTrackingStrategy -> TelemetryConfigurationEvent.ViewTrackingStrategy.NAVIGATIONVIEWTRACKINGSTRATEGY
            else -> null
        }
        return TelemetryConfigurationEvent(
            dd = TelemetryConfigurationEvent.Dd(),
            date = timestamp,
            service = TELEMETRY_SERVICE_NAME,
            source = TelemetryConfigurationEvent.Source.tryFromSource(datadogContext.source)
                ?: TelemetryConfigurationEvent.Source.ANDROID,
            version = datadogContext.sdkVersion,
            application = TelemetryConfigurationEvent.Application(rumContext.applicationId),
            session = TelemetryConfigurationEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryConfigurationEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryConfigurationEvent.Action(it) },
            experimentalFeatures = null,
            telemetry = TelemetryConfigurationEvent.Telemetry(
                TelemetryConfigurationEvent.Configuration(
                    sessionSampleRate = rumConfig?.samplingRate?.toLong(),
                    telemetrySampleRate = rumConfig?.telemetrySamplingRate?.toLong(),
                    useProxy = coreConfig?.proxy != null,
                    trackFrustrations = rumConfig?.trackFrustrations,
                    useLocalEncryption = coreConfig?.securityConfig?.localDataEncryption != null,
                    viewTrackingStrategy = viewTrackingStrategy,
                    trackBackgroundEvents = rumConfig?.backgroundEventTracking,
                    trackInteractions = rumConfig?.userActionTrackingStrategy != null,
                    trackErrors = crashConfig != null,
                    trackNativeLongTasks = rumConfig?.longTaskTrackingStrategy != null,
                    batchSize = coreConfig?.batchSize?.windowDurationMs,
                    batchUploadFrequency = coreConfig?.uploadFrequency?.baseStepMs,
                    mobileVitalsUpdatePeriod = rumConfig?.vitalsMonitorUpdateFrequency?.periodInMs,
                    useTracing = traceConfig != null && GlobalTracer.isRegistered(),
                    trackNetworkRequests = trackNetworkRequests
                )
            )
        )
    }

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
