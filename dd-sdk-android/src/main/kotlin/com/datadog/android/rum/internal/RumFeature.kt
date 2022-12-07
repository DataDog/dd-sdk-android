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
import android.view.Choreographer
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.VitalsUpdateFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.thread.LoggingScheduledThreadPoolExecutor
import com.datadog.android.core.internal.thread.NoOpScheduledExecutorService
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.debug.UiRumDebugListener
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
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
import com.datadog.android.rum.tracking.NoOpTrackingStrategy
import com.datadog.android.rum.tracking.NoOpViewTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class RumFeature(
    private val sdkCore: SdkCore,
    private val coreFeature: CoreFeature
) : FeatureEventReceiver {
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

    // region SdkFeature

    fun initialize(context: Context, configuration: Configuration.Feature.RUM) {
        dataWriter = createDataWriter(configuration)

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

        registerTrackingStrategies(context)

        appContext = context.applicationContext

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
            fileWriter = BatchFileReaderWriter.create(sdkLogger, coreFeature.localDataEncryption),
            internalLogger = sdkLogger,
            lastViewEventFile = DatadogNdkCrashHandler.getLastViewEventFile(coreFeature.storageDir)

        )
    }

    // endregion

    // region FeatureEventReceiver

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            devLogger.w(UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName))
            return
        }

        if (event["type"] == "crash") {
            addCrash(event)
        } else {
            devLogger.w(UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"]))
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
        vitalExecutorService = LoggingScheduledThreadPoolExecutor(1, devLogger)

        initializeVitalMonitor(CPUVitalReader(), cpuVitalMonitor, periodInMs)
        initializeVitalMonitor(MemoryVitalReader(), memoryVitalMonitor, periodInMs)

        val vitalFrameCallback = VitalFrameCallback(frameRateVitalMonitor) { initialized.get() }
        try {
            Choreographer.getInstance().postFrameCallback(vitalFrameCallback)
        } catch (e: IllegalStateException) {
            // This can happen if the SDK is initialized on a Thread with no looper
            sdkLogger.e("Unable to initialize the Choreographer FrameCallback", e)
            devLogger.w(
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

    private fun addCrash(crashEvent: Map<*, *>) {
        val throwable = crashEvent["throwable"] as? Throwable
        val message = crashEvent["message"] as? String
        val source = (crashEvent["source"] as? String)?.let { rawValue ->
            val value = RumErrorSource.values().firstOrNull { enumValue ->
                enumValue.name.equals(rawValue, ignoreCase = true)
            }
            if (value == null) {
                @Suppress("InvalidStringFormat") // compileTimeInitializer is null here
                devLogger.w(WRONG_VALUE_OF_SOURCE_PROPERTY_WARNING.format(Locale.US, rawValue))
            }
            value
        }

        if (throwable == null || message == null || source == null) {
            devLogger.w(EVENT_MISSING_MANDATORY_FIELDS)
            return
        }

        (GlobalRum.get() as? AdvancedRumMonitor)?.addCrash(
            message,
            source,
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
        internal const val EVENT_MISSING_MANDATORY_FIELDS = "RUM feature received a crash event" +
            " where one or more mandatory (throwable, message, source) fields" +
            " are either missing or have wrong type."
        internal val WRONG_VALUE_OF_SOURCE_PROPERTY_WARNING =
            "Value %s of \"source\" property cannot be matched" +
                " to the ${RumErrorSource::class.java.simpleName} values."
    }
}
