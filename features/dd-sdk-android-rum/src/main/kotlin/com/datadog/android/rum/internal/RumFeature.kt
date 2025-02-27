/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.JvmCrash
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.debug.UiRumDebugListener
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.domain.event.RumEventMetaDeserializer
import com.datadog.android.rum.internal.domain.event.RumEventMetaSerializer
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.domain.event.RumViewEventFilter
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyApi29
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyLegacy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.metric.slowframes.DefaultSlowFramesListener
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.metric.slowframes.NoOpSlowFramesListener
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.rum.internal.net.RumRequestFactory
import com.datadog.android.rum.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.CPUVitalReader
import com.datadog.android.rum.internal.vitals.FPSVitalListener
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListener
import com.datadog.android.rum.internal.vitals.MemoryVitalReader
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.internal.vitals.VitalObserver
import com.datadog.android.rum.internal.vitals.VitalReader
import com.datadog.android.rum.internal.vitals.VitalReaderRunnable
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.interactiontonextview.NoOpLastInteractionIdentifier
import com.datadog.android.rum.metric.interactiontonextview.TimeBasedInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.NoOpInitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.TimeBasedInitialResourceIdentifier
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * RUM feature class, which needs to be registered with Datadog SDK instance.
 */
@Suppress("TooManyFunctions")
internal class RumFeature(
    private val sdkCore: FeatureSdkCore,
    internal val applicationId: String,
    internal val configuration: Configuration,
    private val lateCrashReporterFactory: (InternalSdkCore) -> LateCrashReporter = {
        DatadogLateCrashReporter(it)
    }
) : StorageBackedFeature, FeatureEventReceiver {

    internal var dataWriter: DataWriter<Any> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    internal var sampleRate: Float = 0f
    internal var telemetrySampleRate: Float = 0f
    internal var telemetryConfigurationSampleRate: Float = 0f
    internal var backgroundEventTracking: Boolean = false
    internal var trackFrustrations: Boolean = false

    internal var viewTrackingStrategy: ViewTrackingStrategy = NoOpViewTrackingStrategy()
    internal var actionTrackingStrategy: UserActionTrackingStrategy =
        NoOpUserActionTrackingStrategy()
    internal var longTaskTrackingStrategy: TrackingStrategy = NoOpTrackingStrategy()

    internal var cpuVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var memoryVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var frameRateVitalMonitor: VitalMonitor = NoOpVitalMonitor()

    internal var debugActivityLifecycleListener =
        AtomicReference<Application.ActivityLifecycleCallbacks>(null)
    internal var jankStatsActivityLifecycleListener: Application.ActivityLifecycleCallbacks? = null
    internal var sessionListener: RumSessionListener = NoOpRumSessionListener()

    internal var vitalExecutorService: ScheduledExecutorService = NoOpScheduledExecutorService()
    private var anrDetectorExecutorService: ExecutorService? = null
    internal var anrDetectorRunnable: ANRDetectorRunnable? = null
    internal lateinit var appContext: Context
    internal var initialResourceIdentifier: InitialResourceIdentifier = NoOpInitialResourceIdentifier()
    internal var lastInteractionIdentifier: LastInteractionIdentifier? = NoOpLastInteractionIdentifier()
    internal var slowFramesListener: SlowFramesListener = NoOpSlowFramesListener()

    private val lateCrashEventHandler by lazy { lateCrashReporterFactory(sdkCore as InternalSdkCore) }

    // region Feature

    override val name: String = Feature.RUM_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        this.appContext = appContext
        initialResourceIdentifier = configuration.initialResourceIdentifier
        lastInteractionIdentifier = configuration.lastInteractionIdentifier
        slowFramesListener = DefaultSlowFramesListener()
        dataWriter = createDataWriter(
            configuration,
            sdkCore as InternalSdkCore
        )

        sampleRate = if (sdkCore.isDeveloperModeEnabled) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { DEVELOPER_MODE_SAMPLE_RATE_CHANGED_MESSAGE }
            )
            ALL_IN_SAMPLE_RATE
        } else {
            configuration.sampleRate
        }
        telemetrySampleRate = configuration.telemetrySampleRate
        telemetryConfigurationSampleRate = configuration.telemetryConfigurationSampleRate
        backgroundEventTracking = configuration.backgroundEventTracking
        trackFrustrations = configuration.trackFrustrations

        configuration.viewTrackingStrategy?.let { viewTrackingStrategy = it }
        actionTrackingStrategy = if (configuration.userActionTracking) {
            provideUserTrackingStrategy(
                configuration.touchTargetExtraAttributesProviders.toTypedArray(),
                configuration.interactionPredicate,
                sdkCore.internalLogger
            )
        } else {
            NoOpUserActionTrackingStrategy()
        }
        configuration.longTaskTrackingStrategy?.let { longTaskTrackingStrategy = it }

        initializeVitalMonitors(configuration.vitalsMonitorUpdateFrequency)

        if (configuration.trackNonFatalAnrs) {
            initializeANRDetector()
        }

        registerTrackingStrategies(appContext)

        sessionListener = configuration.sessionListener

        sdkCore.setEventReceiver(name, this)

        initialized.set(true)
    }

    override val requestFactory: RequestFactory by lazy {
        RumRequestFactory(
            customEndpointUrl = configuration.customEndpointUrl,
            viewEventFilter = RumViewEventFilter(
                eventMetaDeserializer = RumEventMetaDeserializer(sdkCore.internalLogger)
            ),
            internalLogger = sdkCore.internalLogger
        )
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        sdkCore.removeEventReceiver(name)

        unregisterTrackingStrategies(appContext)

        dataWriter = NoOpDataWriter()

        viewTrackingStrategy = NoOpViewTrackingStrategy()
        actionTrackingStrategy = NoOpUserActionTrackingStrategy()
        longTaskTrackingStrategy = NoOpTrackingStrategy()

        cpuVitalMonitor = NoOpVitalMonitor()
        memoryVitalMonitor = NoOpVitalMonitor()
        frameRateVitalMonitor = NoOpVitalMonitor()

        vitalExecutorService.shutdownNow()
        anrDetectorExecutorService?.shutdownNow()
        anrDetectorRunnable?.stop()
        vitalExecutorService = NoOpScheduledExecutorService()
        sessionListener = NoOpRumSessionListener()

        GlobalRumMonitor.unregister(sdkCore)
    }

    // endregion

    private fun createDataWriter(
        configuration: Configuration,
        sdkCore: InternalSdkCore
    ): DataWriter<Any> {
        return RumDataWriter(
            eventSerializer = MapperSerializer(
                RumEventMapper(
                    viewEventMapper = configuration.viewEventMapper,
                    errorEventMapper = configuration.errorEventMapper,
                    resourceEventMapper = configuration.resourceEventMapper,
                    actionEventMapper = configuration.actionEventMapper,
                    longTaskEventMapper = configuration.longTaskEventMapper,
                    telemetryConfigurationMapper = configuration.telemetryConfigurationMapper,
                    internalLogger = sdkCore.internalLogger
                ),
                RumEventSerializer(sdkCore.internalLogger)
            ),
            eventMetaSerializer = RumEventMetaSerializer(),
            sdkCore = sdkCore
        )
    }

    // region FeatureEventReceiver

    override fun onReceive(event: Any) {
        when (event) {
            is Map<*, *> -> handleMapLikeEvent(event)
            is JvmCrash.Rum -> addJvmCrash(event)
            is InternalTelemetryEvent -> handleTelemetryEvent(event)
            else -> {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
                )
            }
        }
    }

    // endregion

    // region Internal

    private fun handleMapLikeEvent(event: Map<*, *>) {
        when (event["type"]) {
            NDK_CRASH_BUS_MESSAGE_TYPE ->
                lateCrashEventHandler.handleNdkCrashEvent(event, dataWriter)

            LOGGER_ERROR_BUS_MESSAGE_TYPE -> addLoggerError(event)
            LOGGER_ERROR_WITH_STACK_TRACE_MESSAGE_TYPE -> addLoggerErrorWithStacktrace(event)
            WEB_VIEW_INGESTED_NOTIFICATION_MESSAGE_TYPE -> {
                (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.sendWebViewEvent()
            }

            TELEMETRY_SESSION_REPLAY_SKIP_FRAME -> addSessionReplaySkippedFrame()
            FLUSH_AND_STOP_MONITOR_MESSAGE_TYPE -> {
                (GlobalRumMonitor.get(sdkCore) as? DatadogRumMonitor)?.let {
                    it.stopKeepAliveCallback()
                    it.drainExecutorService()
                }
            }

            else -> {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"]) }
                )
            }
        }
    }

    private fun handleTelemetryEvent(event: InternalTelemetryEvent) {
        val advancedRumMonitor = GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor ?: return
        advancedRumMonitor.sendTelemetryEvent(event)
    }

    @AnyThread
    internal fun enableDebugging(advancedRumMonitor: AdvancedRumMonitor) {
        if (!initialized.get()) {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "$RUM_FEATURE_NOT_YET_INITIALIZED Cannot enable RUM debugging." }
            )
            return
        }
        val context = appContext
        synchronized(debugActivityLifecycleListener) {
            if (context is Application && debugActivityLifecycleListener.get() == null) {
                val listener = UiRumDebugListener(sdkCore, advancedRumMonitor)
                debugActivityLifecycleListener.set(listener)
                context.registerActivityLifecycleCallbacks(listener)
            }
        }
    }

    @AnyThread
    internal fun disableDebugging() {
        val context = appContext
        synchronized(debugActivityLifecycleListener) {
            if (debugActivityLifecycleListener.get() != null && context is Application) {
                val listener = debugActivityLifecycleListener.get()
                context.unregisterActivityLifecycleCallbacks(listener)
                debugActivityLifecycleListener.set(null)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    internal fun consumeLastFatalAnr(rumEventsExecutorService: ExecutorService) {
        val activityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lastKnownAnr = try {
            activityManager.getHistoricalProcessExitReasons(null, 0, 0)
                // from docs: Returns: a list of ApplicationExitInfo records matching the criteria,
                // sorted in the order from most recent to least recent.
                .firstOrNull { it.reason == ApplicationExitInfo.REASON_ANR }
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { FAILED_TO_GET_HISTORICAL_EXIT_REASONS },
                e
            )
            null
        } ?: return

        rumEventsExecutorService.submitSafe("Send fatal ANR", sdkCore.internalLogger) {
            val lastRumViewEvent = (sdkCore as InternalSdkCore).lastViewEvent
            if (lastRumViewEvent != null) {
                lateCrashEventHandler.handleAnrCrash(
                    lastKnownAnr,
                    lastRumViewEvent,
                    dataWriter
                )
            } else {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { NO_LAST_RUM_VIEW_EVENT_AVAILABLE }
                )
            }
        }
    }

    /**
     * Enables the tracking of JankStats for the given activity. This should only be necessary for the
     * initial activity of an application if Datadog is initialized after that activity is created.
     * @param activity the activity to track
     */
    internal fun enableJankStatsTracking(activity: Activity) {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall")
            jankStatsActivityLifecycleListener?.onActivityStarted(activity)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { FAILED_TO_ENABLE_JANK_STATS_TRACKING_MANUALLY },
                e
            )
        }
    }

    private fun registerTrackingStrategies(appContext: Context) {
        actionTrackingStrategy.register(sdkCore, appContext)
        viewTrackingStrategy.register(sdkCore, appContext)
        longTaskTrackingStrategy.register(sdkCore, appContext)
    }

    private fun unregisterTrackingStrategies(appContext: Context?) {
        actionTrackingStrategy.unregister(appContext)
        viewTrackingStrategy.unregister(appContext)
        longTaskTrackingStrategy.unregister(appContext)
    }

    private fun initializeVitalMonitors(frequency: VitalsUpdateFrequency) {
        if (frequency == VitalsUpdateFrequency.NEVER) {
            return
        }
        cpuVitalMonitor = AggregatingVitalMonitor()
        memoryVitalMonitor = AggregatingVitalMonitor()
        frameRateVitalMonitor = AggregatingVitalMonitor()
        initializeVitalReaders(frequency.periodInMs)
    }

    private fun initializeVitalReaders(periodInMs: Long) {
        @Suppress("UnsafeThirdPartyFunctionCall") // pool size can't be <= 0
        vitalExecutorService = sdkCore.createScheduledExecutorService("rum-vital")

        initializeVitalMonitor(
            CPUVitalReader(internalLogger = sdkCore.internalLogger),
            cpuVitalMonitor,
            periodInMs
        )
        initializeVitalMonitor(
            MemoryVitalReader(internalLogger = sdkCore.internalLogger),
            memoryVitalMonitor,
            periodInMs
        )

        jankStatsActivityLifecycleListener = JankStatsActivityLifecycleListener(
            listOf(
                FPSVitalListener(frameRateVitalMonitor),
                slowFramesListener
            ),
            sdkCore.internalLogger
        )
        (appContext as? Application)?.registerActivityLifecycleCallbacks(
            jankStatsActivityLifecycleListener
        )
    }

    private fun initializeVitalMonitor(
        vitalReader: VitalReader,
        vitalObserver: VitalObserver,
        periodInMs: Long
    ) {
        val readerRunnable = VitalReaderRunnable(
            sdkCore,
            vitalReader,
            vitalObserver,
            vitalExecutorService,
            periodInMs
        )
        vitalExecutorService.scheduleSafe(
            "Vitals monitoring",
            periodInMs,
            TimeUnit.MILLISECONDS,
            sdkCore.internalLogger,
            readerRunnable
        )
    }

    private fun initializeANRDetector() {
        val detectorRunnable = ANRDetectorRunnable(sdkCore, Handler(Looper.getMainLooper()))
        anrDetectorExecutorService = sdkCore.createSingleThreadExecutorService("rum-anr-detection")
        anrDetectorExecutorService?.executeSafe(
            "ANR detection",
            sdkCore.internalLogger,
            detectorRunnable
        )
        anrDetectorRunnable = detectorRunnable
    }

    private fun addJvmCrash(crashEvent: JvmCrash.Rum) {
        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.addCrash(
            crashEvent.message,
            RumErrorSource.SOURCE,
            crashEvent.throwable,
            crashEvent.threads
        )
    }

    private fun addLoggerError(loggerErrorEvent: Map<*, *>) {
        val throwable = loggerErrorEvent[EVENT_THROWABLE_PROPERTY] as? Throwable
        val message = loggerErrorEvent[EVENT_MESSAGE_PROPERTY] as? String

        @Suppress("UNCHECKED_CAST")
        val attributes = loggerErrorEvent[EVENT_ATTRIBUTES_PROPERTY] as? Map<String, Any?>

        if (message == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                { LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS }
            )
            return
        }

        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.addError(
            message,
            RumErrorSource.LOGGER,
            throwable,
            attributes ?: emptyMap()
        )
    }

    private fun addLoggerErrorWithStacktrace(loggerErrorEvent: Map<*, *>) {
        val stacktrace = loggerErrorEvent[EVENT_STACKTRACE_PROPERTY] as? String
        val message = loggerErrorEvent[EVENT_MESSAGE_PROPERTY] as? String

        @Suppress("UNCHECKED_CAST")
        val attributes = loggerErrorEvent[EVENT_ATTRIBUTES_PROPERTY] as? Map<String, Any?>

        if (message == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                { LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS }
            )
            return
        }

        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.addErrorWithStacktrace(
            message,
            RumErrorSource.LOGGER,
            stacktrace,
            attributes ?: emptyMap()
        )
    }

    private fun addSessionReplaySkippedFrame() {
        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.addSessionReplaySkippedFrame()
    }

    // endregion

    internal data class Configuration(
        val customEndpointUrl: String?,
        val sampleRate: Float,
        val telemetrySampleRate: Float,
        val telemetryConfigurationSampleRate: Float,
        val userActionTracking: Boolean,
        val touchTargetExtraAttributesProviders: List<ViewAttributesProvider>,
        val interactionPredicate: InteractionPredicate,
        val viewTrackingStrategy: ViewTrackingStrategy?,
        val longTaskTrackingStrategy: TrackingStrategy?,
        val viewEventMapper: EventMapper<ViewEvent>,
        val errorEventMapper: EventMapper<ErrorEvent>,
        val resourceEventMapper: EventMapper<ResourceEvent>,
        val actionEventMapper: EventMapper<ActionEvent>,
        val longTaskEventMapper: EventMapper<LongTaskEvent>,
        val telemetryConfigurationMapper: EventMapper<TelemetryConfigurationEvent>,
        val backgroundEventTracking: Boolean,
        val trackFrustrations: Boolean,
        val trackNonFatalAnrs: Boolean,
        val vitalsMonitorUpdateFrequency: VitalsUpdateFrequency,
        val sessionListener: RumSessionListener,
        val initialResourceIdentifier: InitialResourceIdentifier,
        val lastInteractionIdentifier: LastInteractionIdentifier?,
        val additionalConfig: Map<String, Any>,
        val trackAnonymousUser: Boolean
    )

    internal companion object {

        internal const val NDK_CRASH_BUS_MESSAGE_TYPE = "ndk_crash"
        internal const val LOGGER_ERROR_BUS_MESSAGE_TYPE = "logger_error"
        internal const val LOGGER_ERROR_WITH_STACK_TRACE_MESSAGE_TYPE = "logger_error_with_stacktrace"
        internal const val WEB_VIEW_INGESTED_NOTIFICATION_MESSAGE_TYPE = "web_view_ingested_notification"
        internal const val TELEMETRY_SESSION_REPLAY_SKIP_FRAME = "sr_skipped_frame"
        internal const val FLUSH_AND_STOP_MONITOR_MESSAGE_TYPE = "flush_and_stop_monitor"

        internal const val ALL_IN_SAMPLE_RATE: Float = 100f
        internal const val DEFAULT_SAMPLE_RATE: Float = 100f
        internal const val DEFAULT_TELEMETRY_SAMPLE_RATE: Float = 20f
        internal const val DEFAULT_TELEMETRY_CONFIGURATION_SAMPLE_RATE: Float = 20f
        internal const val DEFAULT_LONG_TASK_THRESHOLD_MS = 100L
        internal const val DD_TELEMETRY_CONFIG_SAMPLE_RATE_TAG =
            "_dd.telemetry.configuration_sample_rate"

        internal val DEFAULT_RUM_CONFIG = Configuration(
            customEndpointUrl = null,
            sampleRate = DEFAULT_SAMPLE_RATE,
            telemetrySampleRate = DEFAULT_TELEMETRY_SAMPLE_RATE,
            telemetryConfigurationSampleRate = DEFAULT_TELEMETRY_CONFIGURATION_SAMPLE_RATE,
            userActionTracking = true,
            touchTargetExtraAttributesProviders = emptyList(),
            interactionPredicate = NoOpInteractionPredicate(),
            viewTrackingStrategy = ActivityViewTrackingStrategy(false),
            longTaskTrackingStrategy = MainLooperLongTaskStrategy(
                DEFAULT_LONG_TASK_THRESHOLD_MS
            ),
            viewEventMapper = NoOpEventMapper(),
            errorEventMapper = NoOpEventMapper(),
            resourceEventMapper = NoOpEventMapper(),
            actionEventMapper = NoOpEventMapper(),
            longTaskEventMapper = NoOpEventMapper(),
            telemetryConfigurationMapper = NoOpEventMapper(),
            backgroundEventTracking = false,
            trackFrustrations = true,
            trackNonFatalAnrs = isTrackNonFatalAnrsEnabledByDefault(),
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE,
            sessionListener = NoOpRumSessionListener(),
            initialResourceIdentifier = TimeBasedInitialResourceIdentifier(),
            lastInteractionIdentifier = TimeBasedInteractionIdentifier(),
            additionalConfig = emptyMap(),
            trackAnonymousUser = true
        )

        internal const val EVENT_MESSAGE_PROPERTY = "message"
        internal const val EVENT_THROWABLE_PROPERTY = "throwable"
        internal const val EVENT_ATTRIBUTES_PROPERTY = "attributes"
        internal const val EVENT_STACKTRACE_PROPERTY = "stacktrace"

        internal const val UNSUPPORTED_EVENT_TYPE =
            "RUM feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "RUM feature received an event with unknown value of \"type\" property=%s."
        internal const val FAILED_TO_GET_HISTORICAL_EXIT_REASONS =
            "Couldn't get historical exit reasons"
        internal const val NO_LAST_RUM_VIEW_EVENT_AVAILABLE =
            "No last known RUM view event found, skipping fatal ANR reporting."
        internal const val LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a log event" +
                " where mandatory message field is either missing or has a wrong type."
        internal const val LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a log event with stacktrace" +
                " where mandatory message field is either missing or has a wrong type."
        internal const val DEVELOPER_MODE_SAMPLE_RATE_CHANGED_MESSAGE =
            "Developer mode enabled, setting RUM sample rate to 100%."
        internal const val RUM_FEATURE_NOT_YET_INITIALIZED =
            "RUM feature is not initialized yet, you need to register it with a" +
                " SDK instance by calling SdkCore#registerFeature method."
        internal const val FAILED_TO_ENABLE_JANK_STATS_TRACKING_MANUALLY =
            "Manually enabling JankStats tracking threw an exception."

        private fun provideUserTrackingStrategy(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider>,
            interactionPredicate: InteractionPredicate,
            internalLogger: InternalLogger
        ): UserActionTrackingStrategy {
            val gesturesTracker =
                provideGestureTracker(
                    touchTargetExtraAttributesProviders,
                    interactionPredicate,
                    internalLogger
                )
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UserActionTrackingStrategyApi29(gesturesTracker)
            } else {
                UserActionTrackingStrategyLegacy(gesturesTracker)
            }
        }

        private fun provideGestureTracker(
            customProviders: Array<ViewAttributesProvider>,
            interactionPredicate: InteractionPredicate,
            internalLogger: InternalLogger
        ): DatadogGesturesTracker {
            val defaultProviders = arrayOf(JetpackViewAttributesProvider())
            val providers = customProviders + defaultProviders
            return DatadogGesturesTracker(providers, interactionPredicate, internalLogger)
        }

        internal fun isTrackNonFatalAnrsEnabledByDefault(
            buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
        ): Boolean {
            return buildSdkVersionProvider.version < Build.VERSION_CODES.R
        }
    }
}
