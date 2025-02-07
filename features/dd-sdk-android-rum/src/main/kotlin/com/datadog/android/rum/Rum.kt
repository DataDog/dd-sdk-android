/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.os.Build
import android.os.Handler
import android.os.Looper
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.metric.SessionEndedMetricDispatcher
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.telemetry.internal.TelemetryEventHandler

/**
 * An entry point to Datadog RUM feature.
 */
object Rum {

    /**
     * Enables a RUM feature based on the configuration provided and registers RUM monitor.
     *
     * @param rumConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @Suppress("ReturnCount")
    @JvmOverloads
    @JvmStatic
    fun enable(rumConfiguration: RumConfiguration, sdkCore: SdkCore = Datadog.getInstance()) {
        if (sdkCore !is InternalSdkCore) {
            val logger = (sdkCore as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { UNEXPECTED_SDK_CORE_TYPE }
            )
            return
        }

        if (rumConfiguration.applicationId.isBlank()) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { INVALID_APPLICATION_ID_ERROR_MESSAGE }
            )
            return
        }

        if (sdkCore.getFeature(Feature.RUM_FEATURE_NAME) != null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { RUM_FEATURE_ALREADY_ENABLED }
            )
            return
        }

        val rumFeature = RumFeature(
            sdkCore = sdkCore,
            applicationId = rumConfiguration.applicationId,
            configuration = rumConfiguration.featureConfiguration
        )

        sdkCore.registerFeature(rumFeature)

        sdkCore.getFeature(rumFeature.name)?.dataStore?.let {
            RumAnonymousIdentifierManager(it, sdkCore).manageAnonymousId(
                rumConfiguration.featureConfiguration.trackAnonymousUser
            )
        }

        val rumMonitor = createMonitor(sdkCore, rumFeature)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // small hack: we need to read last RUM view event file, but we don't want to do on the
            // main thread, but at the same time we want to read it surely before it is updated
            // by the new RUM session, so we will read it on the RUM events thread (once read we
            // will switch to another worker thread, so that RUM events thread is not busy)
            rumFeature.consumeLastFatalAnr(rumMonitor.executorService)
        }

        GlobalRumMonitor.registerIfAbsent(
            monitor = rumMonitor,
            sdkCore
        )

        // TODO RUM-3794 there is a small chance of application crashing between RUM monitor
        //  registration and the moment SDK init is processed, in this case we will miss this crash
        //  (it won't activate new session). Ideally we should start session when monitor is created
        //  and before it is registered, but with current code (internal RUM scopes using the
        //  `GlobalRumMonitor`) it is impossible to break cycle dependency.
        rumMonitor.start()
    }

    // region private

    private fun createMonitor(
        sdkCore: InternalSdkCore,
        rumFeature: RumFeature
    ): DatadogRumMonitor {
        val sessionEndedMetricDispatcher = SessionEndedMetricDispatcher(internalLogger = sdkCore.internalLogger)
        return DatadogRumMonitor(
            applicationId = rumFeature.applicationId,
            sdkCore = sdkCore,
            sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
            sampleRate = rumFeature.sampleRate,
            writer = rumFeature.dataWriter,
            handler = Handler(Looper.getMainLooper()),
            telemetryEventHandler = TelemetryEventHandler(
                sdkCore = sdkCore,
                eventSampler = RateBasedSampler(rumFeature.telemetrySampleRate),
                sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
                configurationExtraSampler = RateBasedSampler(
                    rumFeature.telemetryConfigurationSampleRate
                )
            ),
            firstPartyHostHeaderTypeResolver = sdkCore.firstPartyHostResolver,
            cpuVitalMonitor = rumFeature.cpuVitalMonitor,
            memoryVitalMonitor = rumFeature.memoryVitalMonitor,
            frameRateVitalMonitor = rumFeature.frameRateVitalMonitor,
            backgroundTrackingEnabled = rumFeature.backgroundEventTracking,
            trackFrustrations = rumFeature.trackFrustrations,
            sessionListener = rumFeature.sessionListener,
            executorService = sdkCore.createSingleThreadExecutorService("rum-pipeline"),
            initialResourceIdentifier = rumFeature.initialResourceIdentifier,
            lastInteractionIdentifier = rumFeature.lastInteractionIdentifier
        )
    }

    // endregion

    // region Constants

    internal const val UNEXPECTED_SDK_CORE_TYPE =
        "SDK instance provided doesn't implement InternalSdkCore."

    internal const val INVALID_APPLICATION_ID_ERROR_MESSAGE =
        "You're trying to create a RumMonitor instance, " +
            "but the RUM application id was empty. No RUM data will be sent."

    internal const val RUM_FEATURE_ALREADY_ENABLED =
        "RUM Feature is already enabled in this SDK core, ignoring the call to enable it."

    // endregion
}
