/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import androidx.annotation.AnyThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.rum.tracking.MixedViewTrackingStrategy
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent
import java.util.Locale
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent.ViewTrackingStrategy as VTS

@Suppress("TooManyFunctions")
internal class TelemetryEventHandler(
    internal val sdkCore: InternalSdkCore,
    internal val eventSampler: Sampler,
    internal val configurationExtraSampler: Sampler = RateBasedSampler(DEFAULT_CONFIGURATION_SAMPLE_RATE),
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    private val maxEventCountPerSession: Int = MAX_EVENTS_PER_SESSION
) : RumSessionListener {

    private var trackNetworkRequests = false

    private val eventIDsSeenInCurrentSession = mutableSetOf<TelemetryEventId>()
    private var totalEventsSeenInCurrentSession = 0

    @AnyThread
    @Suppress("LongMethod")
    fun handleEvent(
        wrappedEvent: RumRawEvent.TelemetryEventWrapper,
        writer: DataWriter<Any>
    ) {
        val event = wrappedEvent.event
        if (!canWrite(event)) return

        eventIDsSeenInCurrentSession.add(event.identity)
        totalEventsSeenInCurrentSession++
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.withWriteContext { datadogContext, eventBatchWriter ->
            val timestamp = wrappedEvent.eventTime.timestamp + datadogContext.time.serverTimeOffsetMs
            val telemetryEvent: Any? = when (event) {
                is InternalTelemetryEvent.Log.Debug -> {
                    createDebugEvent(
                        datadogContext = datadogContext,
                        timestamp = timestamp,
                        message = event.message,
                        additionalProperties = event.additionalProperties
                    )
                }

                is InternalTelemetryEvent.Metric -> {
                    createDebugEvent(
                        datadogContext = datadogContext,
                        timestamp = timestamp,
                        message = event.message,
                        additionalProperties = event.additionalProperties
                    )
                }

                is InternalTelemetryEvent.Log.Error -> {
                    sessionEndedMetricDispatcher.onSdkErrorTracked(
                        sessionId = datadogContext.rumContext().sessionId,
                        errorKind = event.kind
                    )
                    createErrorEvent(
                        datadogContext = datadogContext,
                        timestamp = timestamp,
                        message = event.message,
                        stack = event.stacktrace,
                        kind = event.kind,
                        additionalProperties = event.additionalProperties
                    )
                }

                is InternalTelemetryEvent.Configuration -> {
                    createConfigurationEvent(
                        datadogContext,
                        timestamp,
                        event
                    )
                }

                is InternalTelemetryEvent.ApiUsage -> {
                    createApiUsageEvent(
                        datadogContext = datadogContext,
                        timestamp = timestamp,
                        event = event
                    )
                }

                is InternalTelemetryEvent.InterceptorInstantiated -> {
                    trackNetworkRequests = true
                    null
                }
            }
            if (telemetryEvent != null) {
                writer.write(eventBatchWriter, telemetryEvent, EventType.TELEMETRY)
            }
        }
    }

    override fun onSessionStarted(sessionId: String, isDiscarded: Boolean) {
        eventIDsSeenInCurrentSession.clear()
        totalEventsSeenInCurrentSession = 0
    }

    // region private

    @Suppress("ReturnCount")
    private fun canWrite(event: InternalTelemetryEvent): Boolean {
        if (!eventSampler.sample()) return false

        if (event is InternalTelemetryEvent.Configuration && !configurationExtraSampler.sample()) {
            return false
        }

        val eventIdentity = event.identity

        if (event !is InternalTelemetryEvent.Metric && eventIDsSeenInCurrentSession.contains(eventIdentity)) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { ALREADY_SEEN_EVENT_MESSAGE.format(Locale.US, eventIdentity) }
            )
            return false
        }

        if (totalEventsSeenInCurrentSession >= maxEventCountPerSession) {
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
                additionalProperties = resolvedAdditionalProperties,
                device = TelemetryDebugEvent.Device(
                    architecture = datadogContext.deviceInfo.architecture,
                    brand = datadogContext.deviceInfo.deviceBrand,
                    model = datadogContext.deviceInfo.deviceModel
                ),
                os = TelemetryDebugEvent.Os(
                    build = datadogContext.deviceInfo.deviceBuildId,
                    version = datadogContext.deviceInfo.osVersion,
                    name = datadogContext.deviceInfo.osName
                )
            )
        )
    }

    @Suppress("LongParameterList")
    private fun createErrorEvent(
        datadogContext: DatadogContext,
        timestamp: Long,
        message: String,
        stack: String?,
        kind: String?,
        additionalProperties: Map<String, Any?>?
    ): TelemetryErrorEvent {
        val rumContext = datadogContext.rumContext()
        val resolvedAdditionalProperties = additionalProperties?.toMutableMap() ?: mutableMapOf()

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
                additionalProperties = resolvedAdditionalProperties,
                error = if (stack != null || kind != null) {
                    TelemetryErrorEvent.Error(
                        stack = stack,
                        kind = kind
                    )
                } else {
                    null
                },
                device = TelemetryErrorEvent.Device(
                    architecture = datadogContext.deviceInfo.architecture,
                    brand = datadogContext.deviceInfo.deviceBrand,
                    model = datadogContext.deviceInfo.deviceModel
                ),
                os = TelemetryErrorEvent.Os(
                    build = datadogContext.deviceInfo.deviceBuildId,
                    version = datadogContext.deviceInfo.osVersion,
                    name = datadogContext.deviceInfo.osName
                )
            )
        )
    }

    @Suppress("LongMethod")
    private fun createConfigurationEvent(
        datadogContext: DatadogContext,
        timestamp: Long,
        event: InternalTelemetryEvent.Configuration
    ): TelemetryConfigurationEvent {
        val traceFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
        val sessionReplayFeatureContext =
            sdkCore.getFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME)
        val sessionReplaySampleRate = sessionReplayFeatureContext[SESSION_REPLAY_SAMPLE_RATE_KEY]
            as? Long
        val startRecordingImmediately =
            sessionReplayFeatureContext[SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY] as? Boolean
        val sessionReplayImagePrivacy =
            sessionReplayFeatureContext[SESSION_REPLAY_IMAGE_PRIVACY_KEY] as? String
        val sessionReplayTouchPrivacy =
            sessionReplayFeatureContext[SESSION_REPLAY_TOUCH_PRIVACY_KEY] as? String
        val sessionReplayTextAndInputPrivacy =
            sessionReplayFeatureContext[SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY] as? String
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
        val traceContext = sdkCore.getFeatureContext(Feature.TRACING_FEATURE_NAME)
        val tracerApi = resolveTracerApi(traceContext)
        val openTelemetryApiVersion = resolveOpenTelemetryApiVersion(tracerApi, traceContext)
        val useTracing = (traceFeature != null && tracerApi != null)
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
                device = TelemetryConfigurationEvent.Device(
                    architecture = datadogContext.deviceInfo.architecture,
                    brand = datadogContext.deviceInfo.deviceBrand,
                    model = datadogContext.deviceInfo.deviceModel
                ),
                os = TelemetryConfigurationEvent.Os(
                    build = datadogContext.deviceInfo.deviceBuildId,
                    version = datadogContext.deviceInfo.osVersion,
                    name = datadogContext.deviceInfo.osName
                ),
                configuration = TelemetryConfigurationEvent.Configuration(
                    sessionSampleRate = rumConfig?.sampleRate?.toLong(),
                    telemetrySampleRate = rumConfig?.telemetrySampleRate?.toLong(),
                    useProxy = event.useProxy,
                    trackFrustrations = rumConfig?.trackFrustrations,
                    useLocalEncryption = event.useLocalEncryption,
                    viewTrackingStrategy = viewTrackingStrategy,
                    trackBackgroundEvents = rumConfig?.backgroundEventTracking,
                    trackInteractions = rumConfig?.userActionTracking != null,
                    trackErrors = event.trackErrors,
                    trackNativeLongTasks = rumConfig?.longTaskTrackingStrategy != null,
                    batchSize = event.batchSize,
                    batchUploadFrequency = event.batchUploadFrequency,
                    mobileVitalsUpdatePeriod = rumConfig?.vitalsMonitorUpdateFrequency?.periodInMs,
                    useTracing = useTracing,
                    tracerApi = tracerApi?.name,
                    tracerApiVersion = openTelemetryApiVersion,
                    trackNetworkRequests = trackNetworkRequests,
                    sessionReplaySampleRate = sessionReplaySampleRate,
                    imagePrivacyLevel = sessionReplayImagePrivacy,
                    touchPrivacyLevel = sessionReplayTouchPrivacy,
                    textAndInputPrivacyLevel = sessionReplayTextAndInputPrivacy,
                    startRecordingImmediately = startRecordingImmediately,
                    batchProcessingLevel = event.batchProcessingLevel.toLong()
                )
            )
        )
    }

    private fun createApiUsageEvent(
        datadogContext: DatadogContext,
        timestamp: Long,
        event: InternalTelemetryEvent.ApiUsage
    ): TelemetryUsageEvent? {
        val rumContext = datadogContext.rumContext()
        return when (event) {
            is InternalTelemetryEvent.ApiUsage.AddViewLoadingTime -> {
                TelemetryUsageEvent(
                    dd = TelemetryUsageEvent.Dd(),
                    date = timestamp,
                    source = TelemetryUsageEvent.Source.tryFromSource(
                        datadogContext.source,
                        sdkCore.internalLogger
                    ) ?: TelemetryUsageEvent.Source.ANDROID,
                    service = datadogContext.service,
                    version = datadogContext.sdkVersion,
                    application = TelemetryUsageEvent.Application(rumContext.applicationId),
                    session = TelemetryUsageEvent.Session(rumContext.sessionId),
                    view = rumContext.viewId?.let { TelemetryUsageEvent.View(it) },
                    action = rumContext.actionId?.let { TelemetryUsageEvent.Action(it) },
                    telemetry = TelemetryUsageEvent.Telemetry(
                        additionalProperties = event.additionalProperties,
                        device = TelemetryUsageEvent.Device(
                            architecture = datadogContext.deviceInfo.architecture,
                            brand = datadogContext.deviceInfo.deviceBrand,
                            model = datadogContext.deviceInfo.deviceModel
                        ),
                        os = TelemetryUsageEvent.Os(
                            build = datadogContext.deviceInfo.deviceBuildId,
                            version = datadogContext.deviceInfo.osVersion,
                            name = datadogContext.deviceInfo.osName
                        ),
                        usage = TelemetryUsageEvent.Usage.AddViewLoadingTime(
                            overwritten = event.overwrite,
                            noView = event.noView,
                            noActiveView = event.noActiveView
                        )
                    )
                )
            }
        }
    }

    private fun isGlobalTracerRegistered(): Boolean {
        // We don't reference io.opentracing from RUM directly, so using reflection for this.
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

    private fun isOpenTelemetryRegistered(traceContext: Map<String, Any?>): Boolean {
        return traceContext[IS_OPENTELEMETRY_ENABLED_CONTEXT_KEY] as? Boolean ?: false
    }

    private fun resolveTracerApi(traceContext: Map<String, Any?>): TracerApi? {
        return when {
            isOpenTelemetryRegistered(traceContext) -> TracerApi.OpenTelemetry
            isGlobalTracerRegistered() -> TracerApi.OpenTracing
            else -> null
        }
    }

    private fun resolveOpenTelemetryApiVersion(tracerApi: TracerApi?, traceContext: Map<String, Any?>): String? {
        return if (tracerApi == TracerApi.OpenTelemetry) {
            traceContext[OPENTELEMETRY_API_VERSION_CONTEXT_KEY] as? String
        } else {
            null
        }
    }

    private fun DatadogContext.rumContext(): RumContext {
        val rumContext = featuresContext[Feature.RUM_FEATURE_NAME].orEmpty()
        return RumContext.fromFeatureContext(rumContext)
    }

    // endregion

    internal enum class TracerApi {
        OpenTelemetry,
        OpenTracing
    }

    companion object {
        const val MAX_EVENTS_PER_SESSION = 100
        const val DEFAULT_CONFIGURATION_SAMPLE_RATE = 20f
        const val ALREADY_SEEN_EVENT_MESSAGE =
            "Already seen telemetry event with identity=%s, rejecting."
        const val MAX_EVENT_NUMBER_REACHED_MESSAGE =
            "Max number of telemetry events per session reached, rejecting."
        const val TELEMETRY_SERVICE_NAME = "dd-sdk-android"
        internal const val IS_OPENTELEMETRY_ENABLED_CONTEXT_KEY = "is_opentelemetry_enabled"
        internal const val OPENTELEMETRY_API_VERSION_CONTEXT_KEY = "opentelemetry_api_version"
        internal const val SESSION_REPLAY_SAMPLE_RATE_KEY = "session_replay_sample_rate"
        internal const val SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY = "session_replay_text_and_input_privacy"
        internal const val SESSION_REPLAY_IMAGE_PRIVACY_KEY = "session_replay_image_privacy"
        internal const val SESSION_REPLAY_TOUCH_PRIVACY_KEY = "session_replay_touch_privacy"
        internal const val SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY =
            "session_replay_start_immediate_recording"
    }
}
