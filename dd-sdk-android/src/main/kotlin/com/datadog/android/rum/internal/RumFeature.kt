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
import android.view.Choreographer
import androidx.annotation.FloatRange
import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.VitalsUpdateFrequency
import com.datadog.android.core.internal.thread.LoggingScheduledThreadPoolExecutor
import com.datadog.android.core.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.core.internal.utils.telemetry
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
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
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashEventHandler
import com.datadog.android.rum.internal.ndk.NdkCrashEventHandler
import com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.vitals.AggregatingVitalMonitor
import com.datadog.android.rum.internal.vitals.CPUVitalReader
import com.datadog.android.rum.internal.vitals.MemoryVitalReader
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalFrameCallback
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
import com.datadog.android.telemetry.internal.TelemetryCoreConfiguration
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.android.v2.core.storage.DataWriter
import com.datadog.android.v2.core.storage.NoOpDataWriter
import com.datadog.android.v2.rum.internal.net.RumRequestFactory
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.datadog.android.core.configuration.Configuration as LegacyConfiguration

/**
 * RUM feature class, which needs to be registered with Datadog SDK instance.
 */
@Suppress("TooManyFunctions")
class RumFeature internal constructor(
    internal val applicationId: String,
    internal val configuration: Configuration,
    private val ndkCrashEventHandler: NdkCrashEventHandler = DatadogNdkCrashEventHandler()
) : StorageBackedFeature, FeatureEventReceiver {

    internal constructor(
        applicationId: String,
        configuration: LegacyConfiguration.Feature.RUM
    ) : this(
        applicationId,
        Configuration(
            endpointUrl = configuration.endpointUrl,
            samplingRate = configuration.samplingRate,
            telemetrySamplingRate = configuration.telemetrySamplingRate,
            userActionTrackingStrategy = configuration.userActionTrackingStrategy,
            viewTrackingStrategy = configuration.viewTrackingStrategy,
            longTaskTrackingStrategy = configuration.longTaskTrackingStrategy,
            rumEventMapper = configuration.rumEventMapper,
            backgroundEventTracking = configuration.backgroundEventTracking,
            trackFrustrations = configuration.trackFrustrations,
            vitalsMonitorUpdateFrequency = configuration.vitalsMonitorUpdateFrequency
        )
    )

    internal var dataWriter: DataWriter<Any> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    internal var samplingRate: Float = 0f
    internal var telemetrySamplingRate: Float = 0f
    internal var backgroundEventTracking: Boolean = false
    internal var trackFrustrations: Boolean = false

    internal var viewTrackingStrategy: ViewTrackingStrategy = NoOpViewTrackingStrategy()
    internal var actionTrackingStrategy: UserActionTrackingStrategy =
        NoOpUserActionTrackingStrategy()
    internal var rumEventMapper: EventMapper<Any> = NoOpEventMapper()
    internal var longTaskTrackingStrategy: TrackingStrategy = NoOpTrackingStrategy()

    internal var cpuVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var memoryVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var frameRateVitalMonitor: VitalMonitor = NoOpVitalMonitor()

    internal var debugActivityLifecycleListener: Application.ActivityLifecycleCallbacks? = null

    internal var vitalExecutorService: ScheduledExecutorService = NoOpScheduledExecutorService()
    internal lateinit var anrDetectorExecutorService: ExecutorService
    internal lateinit var anrDetectorRunnable: ANRDetectorRunnable
    internal lateinit var anrDetectorHandler: Handler
    internal lateinit var appContext: Context
    internal lateinit var sdkCore: SdkCore

    // region Feature

    override val name: String = Feature.RUM_FEATURE_NAME

    override fun onInitialize(
        sdkCore: SdkCore,
        appContext: Context
    ) {
        this.sdkCore = sdkCore
        this.appContext = appContext

        dataWriter = createDataWriter(
            configuration,
            sdkCore as InternalSdkCore
        )

        samplingRate = configuration.samplingRate
        telemetrySamplingRate = configuration.telemetrySamplingRate
        backgroundEventTracking = configuration.backgroundEventTracking
        trackFrustrations = configuration.trackFrustrations
        rumEventMapper = configuration.rumEventMapper

        configuration.viewTrackingStrategy?.let { viewTrackingStrategy = it }
        configuration.userActionTrackingStrategy?.let { actionTrackingStrategy = it }
        configuration.longTaskTrackingStrategy?.let { longTaskTrackingStrategy = it }

        initializeVitalMonitors(configuration.vitalsMonitorUpdateFrequency)

        initializeANRDetector()

        registerTrackingStrategies(appContext)

        sdkCore.setEventReceiver(name, this)

        initialized.set(true)
    }

    override val requestFactory: RequestFactory = RumRequestFactory(configuration.endpointUrl)
    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        sdkCore.removeEventReceiver(name)

        unregisterTrackingStrategies(appContext)

        dataWriter = NoOpDataWriter()

        viewTrackingStrategy = NoOpViewTrackingStrategy()
        actionTrackingStrategy = NoOpUserActionTrackingStrategy()
        longTaskTrackingStrategy = NoOpTrackingStrategy()
        rumEventMapper = NoOpEventMapper()

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
                configuration.rumEventMapper,
                RumEventSerializer()
            ),
            sdkCore = sdkCore,
            internalLogger = internalLogger
        )
    }

    // region FeatureEventReceiver

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            internalLogger.log(
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
                GlobalRum.notifyIngestedWebViewEvent()
            }
            "telemetry_error" -> logTelemetryError(event)
            "telemetry_debug" -> logTelemetryDebug(event)
            "telemetry_configuration" -> logTelemetryConfiguration(event)
            else -> {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"])
                )
            }
        }
    }

    // endregion

    // region Internal

    internal fun enableDebugging() {
        val context = appContext
        if (context is Application) {
            debugActivityLifecycleListener = UiRumDebugListener()
            context.registerActivityLifecycleCallbacks(debugActivityLifecycleListener)
        }
    }

    internal fun disableDebugging() {
        val context = appContext
        if (debugActivityLifecycleListener != null && context is Application) {
            context.unregisterActivityLifecycleCallbacks(debugActivityLifecycleListener)
            debugActivityLifecycleListener = null
        }
    }

    private fun registerTrackingStrategies(appContext: Context) {
        actionTrackingStrategy.register(appContext)
        viewTrackingStrategy.register(appContext)
        longTaskTrackingStrategy.register(appContext)
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
        vitalExecutorService = LoggingScheduledThreadPoolExecutor(1, internalLogger)

        initializeVitalMonitor(CPUVitalReader(), cpuVitalMonitor, periodInMs)
        initializeVitalMonitor(MemoryVitalReader(), memoryVitalMonitor, periodInMs)

        val vitalFrameCallback = VitalFrameCallback(appContext, frameRateVitalMonitor) {
            initialized.get()
        }
        try {
            Choreographer.getInstance().postFrameCallback(vitalFrameCallback)
        } catch (e: IllegalStateException) {
            // This can happen if the SDK is initialized on a Thread with no looper
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                "Unable to initialize the Choreographer FrameCallback",
                e
            )
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                "It seems you initialized the SDK on a thread without a Looper: " +
                    "we won't be able to track your Views' refresh rate."
            )
        }
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
            readerRunnable
        )
    }

    private fun initializeANRDetector() {
        anrDetectorHandler = Handler(Looper.getMainLooper())
        anrDetectorRunnable = ANRDetectorRunnable(anrDetectorHandler)
        anrDetectorExecutorService = Executors.newSingleThreadExecutor()
        anrDetectorExecutorService.executeSafe("ANR detection", anrDetectorRunnable)
    }

    private fun addJvmCrash(crashEvent: Map<*, *>) {
        val throwable = crashEvent[EVENT_THROWABLE_PROPERTY] as? Throwable
        val message = crashEvent[EVENT_MESSAGE_PROPERTY] as? String

        if (throwable == null || message == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS
            )
            return
        }

        (GlobalRum.get() as? AdvancedRumMonitor)?.addCrash(
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
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                LOG_ERROR_EVENT_MISSING_MANDATORY_FIELDS
            )
            return
        }

        (GlobalRum.get() as? AdvancedRumMonitor)?.addError(
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
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                LOG_ERROR_WITH_STACKTRACE_EVENT_MISSING_MANDATORY_FIELDS
            )
            return
        }

        (GlobalRum.get() as? AdvancedRumMonitor)?.addErrorWithStacktrace(
            message,
            RumErrorSource.LOGGER,
            stacktrace,
            attributes ?: emptyMap()
        )
    }

    private fun logTelemetryError(telemetryEvent: Map<*, *>) {
        val message = telemetryEvent[EVENT_MESSAGE_PROPERTY] as? String
        if (message == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
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
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                TELEMETRY_MISSING_MESSAGE_FIELD
            )
            return
        }
        telemetry.debug(message)
    }

    private fun logTelemetryConfiguration(event: Map<*, *>) {
        TelemetryCoreConfiguration.fromEvent(event)?.let {
            (GlobalRum.get() as? AdvancedRumMonitor)
                ?.sendConfigurationTelemetryEvent(it)
        }
    }

    // endregion

    /**
     * A Builder class for a [RumFeature].
     *
     * @param applicationId your applicationId for RUM events
     */
    class Builder(private val applicationId: String) {

        private var rumConfig = DEFAULT_RUM_CONFIG

        /**
         * Sets the sampling rate for RUM Sessions.
         *
         * @param samplingRate the sampling rate must be a value between 0 and 100. A value of 0
         * means no RUM event will be sent, 100 means all sessions will be kept.
         */
        fun sampleRumSessions(@FloatRange(from = 0.0, to = 100.0) samplingRate: Float): Builder {
            rumConfig = rumConfig.copy(samplingRate = samplingRate)
            return this
        }

        /**
         * Sets the sampling rate for Internal Telemetry (info related to the work of the
         * SDK internals). Default value is 20.
         *
         * @param samplingRate the sampling rate must be a value between 0 and 100. A value of 0
         * means no telemetry will be sent, 100 means all telemetry will be kept.
         */
        fun sampleTelemetry(@FloatRange(from = 0.0, to = 100.0) samplingRate: Float): Builder {
            rumConfig = rumConfig.copy(telemetrySamplingRate = samplingRate)
            return this
        }

        /**
         * Enable the user interaction automatic tracker. By enabling this feature the SDK will intercept
         * UI interaction events (e.g.: taps, scrolls, swipes) and automatically send those as RUM UserActions for you.
         * @param touchTargetExtraAttributesProviders an array with your own implementation of the
         * target attributes provider.
         * @param interactionPredicate an interface to provide custom values for the
         * actions events properties.
         * @see [ViewAttributesProvider]
         * @see [InteractionPredicate]
         */
        @JvmOverloads
        fun trackInteractions(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider> = emptyArray(),
            interactionPredicate: InteractionPredicate = NoOpInteractionPredicate()
        ): Builder {
            val strategy = provideUserTrackingStrategy(
                touchTargetExtraAttributesProviders,
                interactionPredicate
            )
            rumConfig = rumConfig.copy(userActionTrackingStrategy = strategy)
            return this
        }

        /**
         * Disable the user interaction automatic tracker.
         */
        fun disableInteractionTracking(): Builder {
            rumConfig = rumConfig.copy(
                userActionTrackingStrategy = NoOpUserActionTrackingStrategy()
            )
            return this
        }

        /**
         * Sets the automatic view tracking strategy used by the SDK.
         * By default [ActivityViewTrackingStrategy] will be used.
         * @param strategy as the [ViewTrackingStrategy]
         * Note: If [null] is passed, the RUM Monitor will let you handle View events manually.
         * This means that you should call [RumMonitor.startView] and [RumMonitor.stopView]
         * yourself. A view should be started when it becomes visible and interactive
         * (equivalent to `onResume`) and be stopped when it's paused (equivalent to `onPause`).
         * @see [com.datadog.android.rum.tracking.ActivityViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.FragmentViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.MixedViewTrackingStrategy]
         * @see [com.datadog.android.rum.tracking.NavigationViewTrackingStrategy]
         */
        fun useViewTrackingStrategy(strategy: ViewTrackingStrategy?): Builder {
            rumConfig = rumConfig.copy(viewTrackingStrategy = strategy)
            return this
        }

        /**
         * Enable long operations on the main thread to be tracked automatically.
         * Any long running operation on the main thread will appear as Long Tasks in Datadog
         * RUM Explorer
         * @param longTaskThresholdMs the threshold in milliseconds above which a task running on
         * the Main thread [Looper] is considered as a long task (default 100ms). Setting a
         * value less than or equal to 0 disables the long task tracking
         */
        @JvmOverloads
        fun trackLongTasks(longTaskThresholdMs: Long = DEFAULT_LONG_TASK_THRESHOLD_MS): Builder {
            val strategy = if (longTaskThresholdMs > 0) {
                MainLooperLongTaskStrategy(longTaskThresholdMs)
            } else {
                null
            }
            rumConfig = rumConfig.copy(longTaskTrackingStrategy = strategy)
            return this
        }

        /**
         * Sets the [ViewEventMapper] for the RUM [ViewEvent]. You can use this interface implementation
         * to modify the [ViewEvent] attributes before serialisation.
         *
         * @param eventMapper the [ViewEventMapper] implementation.
         */
        fun setRumViewEventMapper(eventMapper: ViewEventMapper): Builder {
            rumConfig = rumConfig.copy(
                rumEventMapper = getRumEventMapper().copy(viewEventMapper = eventMapper)
            )
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ResourceEvent]. You can use this interface implementation
         * to modify the [ResourceEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumResourceEventMapper(eventMapper: EventMapper<ResourceEvent>): Builder {
            rumConfig = rumConfig.copy(
                rumEventMapper = getRumEventMapper().copy(resourceEventMapper = eventMapper)
            )
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ActionEvent]. You can use this interface implementation
         * to modify the [ActionEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumActionEventMapper(eventMapper: EventMapper<ActionEvent>): Builder {
            rumConfig = rumConfig.copy(
                rumEventMapper = getRumEventMapper().copy(actionEventMapper = eventMapper)
            )
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [ErrorEvent]. You can use this interface implementation
         * to modify the [ErrorEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumErrorEventMapper(eventMapper: EventMapper<ErrorEvent>): Builder {
            rumConfig = rumConfig.copy(
                rumEventMapper = getRumEventMapper().copy(errorEventMapper = eventMapper)
            )
            return this
        }

        /**
         * Sets the [EventMapper] for the RUM [LongTaskEvent]. You can use this interface implementation
         * to modify the [LongTaskEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setRumLongTaskEventMapper(eventMapper: EventMapper<LongTaskEvent>): Builder {
            rumConfig = rumConfig.copy(
                rumEventMapper = getRumEventMapper().copy(longTaskEventMapper = eventMapper)
            )
            return this
        }

        @Suppress("FunctionMaxLength")
        internal fun setTelemetryConfigurationEventMapper(
            eventMapper: EventMapper<TelemetryConfigurationEvent>
        ): Builder {
            rumConfig = rumConfig.copy(
                rumEventMapper = getRumEventMapper()
                    .copy(telemetryConfigurationMapper = eventMapper)
            )
            return this
        }

        /**
         * Enables/Disables tracking RUM event when no Activity is in foreground.
         *
         * By default, background events are not tracked. Enabling this feature might increase the
         * number of sessions tracked and impact your billing.
         *
         * @param enabled whether background events should be tracked in RUM.
         */
        fun trackBackgroundRumEvents(enabled: Boolean): Builder {
            rumConfig = rumConfig.copy(backgroundEventTracking = enabled)
            return this
        }

        /**
         * Enables/Disables tracking of frustration signals.
         *
         * By default frustration signals are tracked. Currently the SDK supports detecting
         * error taps which occur when an error follows a user action tap.
         *
         * @param enabled whether frustration signals should be tracked in RUM.
         */
        fun trackFrustrations(enabled: Boolean): Builder {
            rumConfig = rumConfig.copy(trackFrustrations = enabled)
            return this
        }

        /**
         * Allows to specify the frequency at which to update the mobile vitals
         * data provided in the RUM [ViewEvent].
         * @param frequency as [VitalsUpdateFrequency]
         * @see [VitalsUpdateFrequency]
         */
        fun setVitalsUpdateFrequency(frequency: VitalsUpdateFrequency): Builder {
            rumConfig = rumConfig.copy(vitalsMonitorUpdateFrequency = frequency)
            return this
        }

        /**
         * Let the RUM feature target your preferred Datadog's site.
         */
        fun useSite(site: DatadogSite): Builder {
            rumConfig = rumConfig.copy(endpointUrl = site.rumEndpoint())
            return this
        }

        /**
         * Let the RUM feature target a custom server.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            rumConfig = rumConfig.copy(endpointUrl = endpoint)
            return this
        }

        /**
         * Builds a [RumFeature] based on the current state of this Builder.
         */
        fun build(): RumFeature {
            return RumFeature(
                applicationId = applicationId,
                configuration = rumConfig
            )
        }

        private fun getRumEventMapper(): RumEventMapper {
            val rumEventMapper = rumConfig.rumEventMapper
            return if (rumEventMapper is RumEventMapper) {
                rumEventMapper
            } else {
                RumEventMapper()
            }
        }
    }

    internal data class Configuration(
        val endpointUrl: String,
        val samplingRate: Float,
        val telemetrySamplingRate: Float,
        val userActionTrackingStrategy: UserActionTrackingStrategy?,
        val viewTrackingStrategy: ViewTrackingStrategy?,
        val longTaskTrackingStrategy: TrackingStrategy?,
        val rumEventMapper: EventMapper<Any>,
        val backgroundEventTracking: Boolean,
        val trackFrustrations: Boolean,
        val vitalsMonitorUpdateFrequency: VitalsUpdateFrequency
    )

    internal companion object {

        internal const val DEFAULT_SAMPLING_RATE: Float = 100f
        internal const val DEFAULT_TELEMETRY_SAMPLING_RATE: Float = 20f
        internal const val DEFAULT_LONG_TASK_THRESHOLD_MS = 100L

        internal val DEFAULT_RUM_CONFIG = Configuration(
            endpointUrl = DatadogEndpoint.RUM_US1,
            samplingRate =
            DEFAULT_SAMPLING_RATE,
            telemetrySamplingRate =
            DEFAULT_TELEMETRY_SAMPLING_RATE,
            userActionTrackingStrategy =
            provideUserTrackingStrategy(
                emptyArray(),
                NoOpInteractionPredicate()
            ),
            viewTrackingStrategy = ActivityViewTrackingStrategy(false),
            longTaskTrackingStrategy = MainLooperLongTaskStrategy(
                DEFAULT_LONG_TASK_THRESHOLD_MS
            ),
            rumEventMapper = NoOpEventMapper(),
            backgroundEventTracking = false,
            trackFrustrations = true,
            vitalsMonitorUpdateFrequency = VitalsUpdateFrequency.AVERAGE
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

        private fun provideUserTrackingStrategy(
            touchTargetExtraAttributesProviders: Array<ViewAttributesProvider>,
            interactionPredicate: InteractionPredicate
        ): UserActionTrackingStrategy {
            val gesturesTracker =
                provideGestureTracker(touchTargetExtraAttributesProviders, interactionPredicate)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UserActionTrackingStrategyApi29(gesturesTracker)
            } else {
                UserActionTrackingStrategyLegacy(gesturesTracker)
            }
        }

        private fun provideGestureTracker(
            customProviders: Array<ViewAttributesProvider>,
            interactionPredicate: InteractionPredicate
        ): DatadogGesturesTracker {
            val defaultProviders = arrayOf(JetpackViewAttributesProvider())
            val providers = customProviders + defaultProviders
            return DatadogGesturesTracker(providers, interactionPredicate)
        }
    }
}
