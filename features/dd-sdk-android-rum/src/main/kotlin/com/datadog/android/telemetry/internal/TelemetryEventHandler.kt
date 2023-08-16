/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
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
import java.util.Locale
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent.ViewTrackingStrategy as VTS

internal class TelemetryEventHandler(
    internal val sdkCore: FeatureSdkCore,
    internal val eventSampler: Sampler,
    internal val configurationExtraSampler: Sampler =
        RateBasedSampler(DEFAULT_CONFIGURATION_SAMPLE_RATE),
    private val maxEventCountPerSession: Int = MAX_EVENTS_PER_SESSION
) : RumSessionListener {

    private var trackNetworkRequests = false

    private val seenInCurrentSession = mutableSetOf<TelemetryEventId>()

    @WorkerThread
    fun handleEvent(event: RumRawEvent.SendTelemetry, writer: DataWriter<Any>) {
        if (!canWrite(event)) return

        seenInCurrentSession.add(event.identity)

        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val timestamp = event.eventTime.timestamp + datadogContext.time.serverTimeOffsetMs
                val telemetryEvent: Any? = when (event.type) {
                    TelemetryType.DEBUG -> {
                        createDebugEvent(
                            datadogContext,
                            timestamp,
                            event.message,
                            event.additionalProperties
                        )
                    }
                    TelemetryType.ERROR -> {
                        createErrorEvent(
                            datadogContext,
                            timestamp,
                            event.message,
                            event.stack,
                            event.kind
                        )
                    }
                    TelemetryType.CONFIGURATION -> {
                        val coreConfiguration = event.coreConfiguration
                        if (coreConfiguration == null) {
                            createErrorEvent(
                                datadogContext,
                                timestamp,
                                "Trying to send configuration event with null config",
                                null,
                                null
                            )
                        } else {
                            createConfigurationEvent(
                                datadogContext,
                                timestamp,
                                coreConfiguration
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

    @Suppress("ReturnCount")
    private fun canWrite(event: RumRawEvent.SendTelemetry): Boolean {
        if (!eventSampler.sample()) return false

        if (event.type == TelemetryType.CONFIGURATION && !configurationExtraSampler.sample()) {
            return false
        }

        val eventIdentity = event.identity

        if (seenInCurrentSession.contains(eventIdentity)) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { ALREADY_SEEN_EVENT_MESSAGE.format(Locale.US, eventIdentity) }
            )
            return false
        }

        if (seenInCurrentSession.size >= maxEventCountPerSession) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { MAX_EVENT_NUMBER_REACHED_MESSAGE }
            )
            return false
        }

        return true
    }

    private fun createDebugEvent(
        datadogContext: DatadogContext,
        timestamp: Long,
        message: String,
        additionalProperties: Map<String, Any?>?
    ): TelemetryDebugEvent {
        val rumContext = datadogContext.rumContext()
        val resolvedAdditionalProperties = additionalProperties?.toMutableMap() ?: mutableMapOf()
        return TelemetryDebugEvent(
            dd = TelemetryDebugEvent.Dd(),
            date = timestamp,
            source = TelemetryDebugEvent.Source.tryFromSource(
                datadogContext.source,
                sdkCore.internalLogger
            ) ?: TelemetryDebugEvent.Source.ANDROID,
            service = TELEMETRY_SERVICE_NAME,
            version = datadogContext.sdkVersion,
            application = TelemetryDebugEvent.Application(rumContext.applicationId),
            session = TelemetryDebugEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryDebugEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryDebugEvent.Action(it) },
            telemetry = TelemetryDebugEvent.Telemetry(
                message = message,
                additionalProperties = resolvedAdditionalProperties
            )
        )
    }

    @Suppress("LongParameterList")
    private fun createErrorEvent(
        datadogContext: DatadogContext,
        timestamp: Long,
        message: String,
        stack: String?,
        kind: String?
    ): TelemetryErrorEvent {
        val rumContext = datadogContext.rumContext()
        return TelemetryErrorEvent(
            dd = TelemetryErrorEvent.Dd(),
            date = timestamp,
            source = TelemetryErrorEvent.Source.tryFromSource(
                datadogContext.source,
                sdkCore.internalLogger
            ) ?: TelemetryErrorEvent.Source.ANDROID,
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
        coreConfiguration: TelemetryCoreConfiguration
    ): TelemetryConfigurationEvent {
        val traceFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
        val rumConfig = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.unwrap<RumFeature>()
            ?.configuration
        val viewTrackingStrategy = when (rumConfig?.viewTrackingStrategy) {
            is ActivityViewTrackingStrategy -> VTS.ACTIVITYVIEWTRACKINGSTRATEGY
            is FragmentViewTrackingStrategy -> VTS.FRAGMENTVIEWTRACKINGSTRATEGY
            is MixedViewTrackingStrategy -> VTS.MIXEDVIEWTRACKINGSTRATEGY
            is NavigationViewTrackingStrategy -> VTS.NAVIGATIONVIEWTRACKINGSTRATEGY
            else -> null
        }

        val rumContext = datadogContext.rumContext()

        return TelemetryConfigurationEvent(
            dd = TelemetryConfigurationEvent.Dd(),
            date = timestamp,
            service = TELEMETRY_SERVICE_NAME,
            source = TelemetryConfigurationEvent.Source.tryFromSource(
                datadogContext.source,
                sdkCore.internalLogger
            ) ?: TelemetryConfigurationEvent.Source.ANDROID,
            version = datadogContext.sdkVersion,
            application = TelemetryConfigurationEvent.Application(rumContext.applicationId),
            session = TelemetryConfigurationEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryConfigurationEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryConfigurationEvent.Action(it) },
            experimentalFeatures = null,
            telemetry = TelemetryConfigurationEvent.Telemetry(
                TelemetryConfigurationEvent.Configuration(
                    sessionSampleRate = rumConfig?.sampleRate?.toLong(),
                    telemetrySampleRate = rumConfig?.telemetrySampleRate?.toLong(),
                    useProxy = coreConfiguration.useProxy,
                    trackFrustrations = rumConfig?.trackFrustrations,
                    useLocalEncryption = coreConfiguration.useLocalEncryption,
                    viewTrackingStrategy = viewTrackingStrategy,
                    trackBackgroundEvents = rumConfig?.backgroundEventTracking,
                    trackInteractions = rumConfig?.userActionTracking != null,
                    trackErrors = coreConfiguration.trackErrors,
                    trackNativeLongTasks = rumConfig?.longTaskTrackingStrategy != null,
                    batchSize = coreConfiguration.batchSize,
                    batchUploadFrequency = coreConfiguration.batchUploadFrequency,
                    mobileVitalsUpdatePeriod = rumConfig?.vitalsMonitorUpdateFrequency?.periodInMs,
                    useTracing = traceFeature != null && isGlobalTracerRegistered(),
                    trackNetworkRequests = trackNetworkRequests
                )
            )
        )
    }

    private fun isGlobalTracerRegistered(): Boolean {
        // TODO RUMM-0000 we don't reference io.opentracing from RUM directly, so using this.
        // alternatively we can afford maybe to reference it, because it seems transitive size
        // of io.opentracing is like 30 KBs in total?
        // Would be nice to add the test with the flavor which is has no io.opentracing and test
        // for obfuscation enabled case.
        return try {
            val globalTracerClass = Class.forName("io.opentracing.util.GlobalTracer")
            return try {
                globalTracerClass.getMethod("isRegistered")
                    .invoke(null) as Boolean
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.TELEMETRY,
                    {
                        "GlobalTracer class exists in the runtime classpath, " +
                            "but there is an error invoking isRegistered method"
                    },
                    t
                )
                false
            }
        } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") t: Throwable) {
            // traces dependency is optional, so it is ok to not have such class
            // it can be also the case that our Proguard rule didn't work and class name is obfuscated
            false
        }
    }

    private fun DatadogContext.rumContext(): RumContext {
        val rumContext = featuresContext[Feature.RUM_FEATURE_NAME].orEmpty()
        return RumContext.fromFeatureContext(rumContext)
    }

    // endregion

    companion object {
        const val MAX_EVENTS_PER_SESSION = 100
        const val DEFAULT_CONFIGURATION_SAMPLE_RATE = 20f
        const val ALREADY_SEEN_EVENT_MESSAGE =
            "Already seen telemetry event with identity=%s, rejecting."
        const val MAX_EVENT_NUMBER_REACHED_MESSAGE =
            "Max number of telemetry events per session reached, rejecting."
        const val TELEMETRY_SERVICE_NAME = "dd-sdk-android"
    }
}
