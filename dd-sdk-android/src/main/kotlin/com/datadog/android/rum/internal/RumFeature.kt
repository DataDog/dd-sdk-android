/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.VitalsUpdateFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.thread.LoggingScheduledThreadPoolExecutor
import com.datadog.android.core.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.debug.UiRumDebugListener
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashEventHandler
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.rum.internal.ndk.NdkCrashEventHandler
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
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooManyFunctions")
internal class RumFeature(
    private val sdkCore: SdkCore,
    private val coreFeature: CoreFeature,
    private val ndkCrashEventHandler: NdkCrashEventHandler = DatadogNdkCrashEventHandler()
) : FeatureEventReceiver {
    internal var dataWriter: DataWriter<Any> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    internal var samplingRate: Float = 0f
    internal var telemetrySamplingRate: Float = 0f
    internal var telemetryConfigurationSamplingRate: Float = 0f
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
    internal var jankStatsActivityLifecycleListener: Application.ActivityLifecycleCallbacks? = null

    internal var vitalExecutorService: ScheduledExecutorService = NoOpScheduledExecutorService()
    internal lateinit var anrDetectorExecutorService: ExecutorService
    internal lateinit var anrDetectorRunnable: ANRDetectorRunnable
    internal lateinit var anrDetectorHandler: Handler
    internal lateinit var appContext: Context

    // region SdkFeature

    fun initialize(context: Context, configuration: Configuration.Feature.RUM) {
        dataWriter = createDataWriter(configuration)

        samplingRate = configuration.samplingRate
        telemetrySamplingRate = configuration.telemetrySamplingRate
        telemetryConfigurationSamplingRate = configuration.telemetryConfigurationSamplingRate
        backgroundEventTracking = configuration.backgroundEventTracking
        trackFrustrations = configuration.trackFrustrations
        rumEventMapper = configuration.rumEventMapper
        appContext = context.applicationContext

        configuration.viewTrackingStrategy?.let { viewTrackingStrategy = it }
        configuration.userActionTrackingStrategy?.let { actionTrackingStrategy = it }
        configuration.longTaskTrackingStrategy?.let { longTaskTrackingStrategy = it }

        initializeVitalMonitors(configuration.vitalsMonitorUpdateFrequency)

        initializeANRDetector()

        registerTrackingStrategies(context)

        sdkCore.setEventReceiver(RUM_FEATURE_NAME, this)

        initialized.set(true)
    }

    fun stop() {
        sdkCore.removeEventReceiver(RUM_FEATURE_NAME)

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

    private fun createDataWriter(
        configuration: Configuration.Feature.RUM
    ): DataWriter<Any> {
        return RumDataWriter(
            serializer = MapperSerializer(
                configuration.rumEventMapper,
                RumEventSerializer()
            ),
            fileWriter = BatchFileReaderWriter.create(
                internalLogger,
                coreFeature.localDataEncryption
            ),
            internalLogger = internalLogger,
            lastViewEventFile = DatadogNdkCrashHandler.getLastViewEventFile(coreFeature.storageDir)
        )
    }

    // endregion

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

        if (event["type"] == "jvm_crash") {
            addJvmCrash(event)
        } else if (event["type"] == "ndk_crash") {
            ndkCrashEventHandler.handleEvent(event, sdkCore, dataWriter)
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"])
            )
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
        jankStatsActivityLifecycleListener = JankStatsActivityLifecycleListener(
            frameRateVitalMonitor,
            internalLogger
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
        val throwable = crashEvent["throwable"] as? Throwable
        val message = crashEvent["message"] as? String

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

    // endregion

    companion object {
        internal val startupTimeNs: Long = System.nanoTime()

        internal const val RUM_FEATURE_NAME = "rum"
        internal const val VIEW_TIMESTAMP_OFFSET_IN_MS_KEY = "view_timestamp_offset"
        internal const val UNSUPPORTED_EVENT_TYPE =
            "RUM feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "RUM feature received an event with unknown value of \"type\" property=%s."
        internal const val JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a JVM crash event" +
                " where one or more mandatory (throwable, message) fields" +
                " are either missing or have wrong type."
    }
}
