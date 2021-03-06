/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.content.Context
import android.view.Choreographer
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.internal.domain.RumFilePersistenceStrategy
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.rum.internal.net.RumOkHttpUploader
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.ViewTreeChangeTrackingStrategy
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
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object RumFeature : SdkFeature<RumEvent, Configuration.Feature.RUM>() {

    internal var samplingRate: Float = 0f
    internal var backgroundEventTracking: Boolean = false

    internal var viewTrackingStrategy: ViewTrackingStrategy = NoOpViewTrackingStrategy()
    internal var actionTrackingStrategy: UserActionTrackingStrategy =
        NoOpUserActionTrackingStrategy()
    internal var viewTreeTrackingStrategy: TrackingStrategy = ViewTreeChangeTrackingStrategy()
    internal var rumEventMapper: EventMapper<RumEvent> = NoOpEventMapper()
    internal var longTaskTrackingStrategy: TrackingStrategy = NoOpTrackingStrategy()

    internal var cpuVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var memoryVitalMonitor: VitalMonitor = NoOpVitalMonitor()
    internal var frameRateVitalMonitor: VitalMonitor = NoOpVitalMonitor()

    internal lateinit var vitalExecutorService: ScheduledThreadPoolExecutor

    // region SdkFeature

    override fun onInitialize(context: Context, configuration: Configuration.Feature.RUM) {
        samplingRate = configuration.samplingRate
        backgroundEventTracking = configuration.backgroundEventTracking
        rumEventMapper = configuration.rumEventMapper

        configuration.viewTrackingStrategy?.let { viewTrackingStrategy = it }
        configuration.userActionTrackingStrategy?.let { actionTrackingStrategy = it }
        configuration.longTaskTrackingStrategy?.let { longTaskTrackingStrategy = it }

        initializeVitalMonitors()

        registerTrackingStrategies(context)
    }

    override fun onStop() {
        unregisterTrackingStrategies(CoreFeature.contextRef.get())

        viewTrackingStrategy = NoOpViewTrackingStrategy()
        actionTrackingStrategy = NoOpUserActionTrackingStrategy()
        longTaskTrackingStrategy = NoOpTrackingStrategy()
        rumEventMapper = NoOpEventMapper()

        cpuVitalMonitor = NoOpVitalMonitor()
        memoryVitalMonitor = NoOpVitalMonitor()
        frameRateVitalMonitor = NoOpVitalMonitor()

        vitalExecutorService.shutdownNow()
    }

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.RUM
    ): PersistenceStrategy<RumEvent> {
        return RumFilePersistenceStrategy(
            CoreFeature.trackingConsentProvider,
            context,
            configuration.rumEventMapper,
            CoreFeature.persistenceExecutorService,
            sdkLogger,
            DatadogNdkCrashHandler.getLastViewEventFile(context)
        )
    }

    override fun createUploader(configuration: Configuration.Feature.RUM): DataUploader {
        return RumOkHttpUploader(
            configuration.endpointUrl,
            CoreFeature.clientToken,
            CoreFeature.okHttpClient
        )
    }

    // endregion

    // region Internal

    private fun registerTrackingStrategies(appContext: Context) {
        actionTrackingStrategy.register(appContext)
        viewTrackingStrategy.register(appContext)
        viewTreeTrackingStrategy.register(appContext)
        longTaskTrackingStrategy.register(appContext)
    }

    private fun unregisterTrackingStrategies(appContext: Context?) {
        actionTrackingStrategy.unregister(appContext)
        viewTrackingStrategy.unregister(appContext)
        viewTreeTrackingStrategy.unregister(appContext)
        longTaskTrackingStrategy.unregister(appContext)
    }

    private fun initializeVitalMonitors() {
        cpuVitalMonitor = AggregatingVitalMonitor()
        memoryVitalMonitor = AggregatingVitalMonitor()
        frameRateVitalMonitor = AggregatingVitalMonitor()

        vitalExecutorService = ScheduledThreadPoolExecutor(1)

        initializeVitalMonitor(CPUVitalReader(), cpuVitalMonitor)
        initializeVitalMonitor(MemoryVitalReader(), memoryVitalMonitor)

        val vitalFrameCallback = VitalFrameCallback(frameRateVitalMonitor) { isInitialized() }
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
        vitalObserver: VitalObserver
    ) {
        val readerRunnable = VitalReaderRunnable(
            vitalReader,
            vitalObserver,
            vitalExecutorService,
            VITAL_UPDATE_PERIOD_MS
        )
        vitalExecutorService.schedule(
            readerRunnable,
            VITAL_UPDATE_PERIOD_MS,
            TimeUnit.MILLISECONDS
        )
    }

    // Update Vitals every second
    private const val VITAL_UPDATE_PERIOD_MS = 100L

    // endregion
}
