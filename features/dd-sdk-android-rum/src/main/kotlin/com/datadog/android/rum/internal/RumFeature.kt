/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import com.datadog.android.core.internal.system.DefaultBuildSdkVersionProvider
import com.datadog.android.core.internal.thread.LoggingScheduledThreadPoolExecutor
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.debug.UiRumDebugListener
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyApi29
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyLegacy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashEventHandler
import com.datadog.android.rum.internal.ndk.NdkCrashEventHandler
import com.datadog.android.rum.internal.net.RumRequestFactory
import com.datadog.android.rum.internal.storage.NoOpDataWriter
import com.datadog.android.rum.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.CPUVitalReader
import com.datadog.android.rum.internal.vitals.JankStatsActivityLifecycleListener
import com.datadog.android.rum.internal.vitals.MemoryVitalReader
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.internal.vitals.VitalObserver
import com.datadog.android.rum.internal.vitals.VitalReader
import com.datadog.android.rum.internal.vitals.VitalReaderRunnable
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.telemetry.internal.Telemetry
import com.datadog.android.telemetry.internal.TelemetryCoreConfiguration
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.android.v2.core.storage.DataWriter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * RUM feature class, which needs to be registered with Datadog SDK instance.
 */
@Suppress("TooManyFunctions")
internal class RumFeature constructor(
    private val sdkCore: FeatureSdkCore,
    internal val applicationId: String,
    internal val configuration: Configuration,
    private val ndkCrashEventHandlerFactory: (InternalLogger) -> NdkCrashEventHandler = {
        DatadogNdkCrashEventHandler(it)
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

    internal var vitalExecutorService: ScheduledExecutorService = NoOpScheduledExecutorService()
    internal lateinit var anrDetectorExecutorService: ExecutorService
    internal lateinit var anrDetectorRunnable: ANRDetectorRunnable
    internal lateinit var anrDetectorHandler: Handler
    internal lateinit var appContext: Context
    internal lateinit var telemetry: Telemetry

    private val ndkCrashEventHandler by lazy { ndkCrashEventHandlerFactory(sdkCore.internalLogger) }

    // region Feature

    override val name: String = Feature.RUM_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        this.appContext = appContext
        this.telemetry = Telemetry(sdkCore)

        dataWriter = createDataWriter(
            configuration,
            sdkCore as InternalSdkCore
        )

        sampleRate = if (sdkCore.isDeveloperModeEnabled) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                DEVELOPER_MODE_SAMPLE_RATE_CHANGED_MESSAGE
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

        initializeANRDetector()

        registerTrackingStrategies(appContext)

        sdkCore.setEventReceiver(name, this)

        initialized.set(true)
    }

    override val requestFactory: RequestFactory by lazy {
        RumRequestFactory(configuration.customEndpointUrl, sdkCore.internalLogger)
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
        anrDetectorExecutorService.shutdownNow()
        anrDetectorRunnable.stop()
        vitalExecutorService = NoOpScheduledExecutorService()
    }

    // endregion

    private fun createDataWriter(
        configuration: Configuration,
        sdkCore: InternalSdkCore
    ): DataWriter<Any> {
        return RumDataWriter(
            serializer = MapperSerializer(
                RumEventMapper(
                    sdkCore,
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
            sdkCore = sdkCore
        )
    }

    // region FeatureEventReceiver

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName)
            )
            return
        }

        when (event["type"]) {
            "jvm_crash" -> addJvmCrash(event)
            "ndk_crash" -> ndkCrashEventHandler.handleEvent(event, sdkCore, dataWriter)
            "logger_error" -> addLoggerError(event)
            "logger_error_with_stacktrace" -> addLoggerErrorWithStacktrace(event)
            "web_view_ingested_notification" -> {
                (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.sendWebViewEvent()
            }

            "telemetry_error" -> logTelemetryError(event)
            "telemetry_debug" -> logTelemetryDebug(event)
            "telemetry_configuration" -> logTelemetryConfiguration(event)
            "flush_and_stop_monitor" -> {
                (GlobalRumMonitor.get(sdkCore) as? DatadogRumMonitor)?.let {
                    it.stopKeepAliveCallback()
                    it.drainExecutorService()
                }
            }

            else -> {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"])
                )
            }
        }
    }

    // endregion

    // region Internal

    @AnyThread
    internal fun enableDebugging(advancedRumMonitor: AdvancedRumMonitor) {
        if (!initialized.get()) {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                "$RUM_FEATURE_NOT_YET_INITIALIZED Cannot enable RUM debugging."
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
        vitalExecutorService = LoggingScheduledThreadPoolExecutor(1, sdkCore.internalLogger)

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
            frameRateVitalMonitor,
            DefaultBuildSdkVersionProvider(),
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
        anrDetectorHandler = Handler(Looper.getMainLooper())
        anrDetectorRunnable = ANRDetectorRunnable(sdkCore, anrDetectorHandler)
        anrDetectorExecutorService = Executors.newSingleThreadExecutor()
        anrDetectorExecutorService.executeSafe(
            "ANR detection",
            sdkCore.internalLogger,
            anrDetectorRunnable
        )
    }

    private fun addJvmCrash(crashEvent: Map<*, *>) {
        val throwable = crashEvent[EVENT_THROWABLE_PROPERTY] as? Throwable
        val message = crashEvent[EVENT_MESSAGE_PROPERTY] as? String

        if (throwable == null || message == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS
            )
            return
        }

        (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)?.addCrash(
            message,
            RumErrorSource.SOURCE,
            throwable
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
                LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS
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
                LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS
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

    private fun logTelemetryError(telemetryEvent: Map<*, *>) {
        val message = telemetryEvent[EVENT_MESSAGE_PROPERTY] as? String
        if (message == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                TELEMETRY_MISSING_MESSAGE_FIELD
            )
            return
        }
        val throwable = telemetryEvent[EVENT_THROWABLE_PROPERTY] as? Throwable
        val stack = telemetryEvent[EVENT_STACKTRACE_PROPERTY] as? String
        val kind = telemetryEvent["kind"] as? String

        if (throwable != null) {
            telemetry.error(message, throwable)
        } else {
            telemetry.error(message, stack, kind)
        }
    }

    private fun logTelemetryDebug(telemetryEvent: Map<*, *>) {
        val message = telemetryEvent[EVENT_MESSAGE_PROPERTY] as? String
        if (message == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                TELEMETRY_MISSING_MESSAGE_FIELD
            )
            return
        }
        telemetry.debug(message)
    }

    private fun logTelemetryConfiguration(event: Map<*, *>) {
        TelemetryCoreConfiguration.fromEvent(event, sdkCore.internalLogger)?.let {
            (GlobalRumMonitor.get(sdkCore) as? AdvancedRumMonitor)
                ?.sendConfigurationTelemetryEvent(it)
        }
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
        val vitalsMonitorUpdateFrequency: VitalsUpdateFrequency,
        val additionalConfig: Map<String, Any>
    )

    internal companion object {

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
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE,
            additionalConfig = emptyMap()
        )

        internal val startupTimeNs: Long = System.nanoTime()

        internal const val EVENT_MESSAGE_PROPERTY = "message"
        internal const val EVENT_THROWABLE_PROPERTY = "throwable"
        internal const val EVENT_ATTRIBUTES_PROPERTY = "attributes"
        internal const val EVENT_STACKTRACE_PROPERTY = "stacktrace"

        internal const val VIEW_TIMESTAMP_OFFSET_IN_MS_KEY = "view_timestamp_offset"
        internal const val UNSUPPORTED_EVENT_TYPE =
            "RUM feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "RUM feature received an event with unknown value of \"type\" property=%s."
        internal const val JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a JVM crash event" +
                " where one or more mandatory (throwable, message) fields" +
                " are either missing or have a wrong type."
        internal const val LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a log event" +
                " where mandatory message field is either missing or has a wrong type."
        internal const val LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a log event with stacktrace" +
                " where mandatory message field is either missing or has a wrong type."
        internal const val TELEMETRY_MISSING_MESSAGE_FIELD = "RUM feature received a telemetry" +
            " event, but mandatory message field is either missing or has a wrong type."
        internal const val DEVELOPER_MODE_SAMPLE_RATE_CHANGED_MESSAGE =
            "Developer mode enabled, setting RUM sample rate to 100%."
        internal const val RUM_FEATURE_NOT_YET_INITIALIZED =
            "RUM feature is not initialized yet, you need to register it with a" +
                " SDK instance by calling SdkCore#registerFeature method."

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
    }
}
