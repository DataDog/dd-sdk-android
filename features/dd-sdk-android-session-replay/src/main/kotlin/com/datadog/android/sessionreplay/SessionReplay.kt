/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import androidx.annotation.VisibleForTesting
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature.Companion.SESSION_REPLAY_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import java.lang.ref.WeakReference

/**
 * An entry point to Datadog Session Replay feature.
 */
object SessionReplay {

    @VisibleForTesting internal var currentRegisteredCore: WeakReference<SdkCore>? = null

    internal const val IS_ALREADY_REGISTERED_WARNING =
        "Session Replay is already enabled and does not support multiple instances. " +
            "The existing instance will continue to be used."

    /**
     * Enables a SessionReplay feature based on the configuration provided.
     * It is recommended to invoke this function as early as possible in the app's lifecycle,
     * ideally within the `Application#onCreate` callback, to ensure proper initialization.
     *
     * @param sessionReplayConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(
        sessionReplayConfiguration: SessionReplayConfiguration,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val featureSdkCore = sdkCore as FeatureSdkCore
        sessionReplayConfiguration.systemRequirementsConfiguration
            .runIfRequirementsMet(featureSdkCore.internalLogger) {
                val touchPrivacyManager = TouchPrivacyManager(sessionReplayConfiguration.touchPrivacy)
                val sessionReplayFeature = SessionReplayFeature(
                    sdkCore = featureSdkCore,
                    customEndpointUrl = sessionReplayConfiguration.customEndpointUrl,
                    privacy = sessionReplayConfiguration.privacy,
                    imagePrivacy = sessionReplayConfiguration.imagePrivacy,
                    touchPrivacy = sessionReplayConfiguration.touchPrivacy,
                    touchPrivacyManager = touchPrivacyManager,
                    textAndInputPrivacy = sessionReplayConfiguration.textAndInputPrivacy,
                    customMappers = sessionReplayConfiguration.customMappers,
                    customOptionSelectorDetectors = sessionReplayConfiguration.customOptionSelectorDetectors,
                    sampleRate = sessionReplayConfiguration.sampleRate,
                    startRecordingImmediately = sessionReplayConfiguration.startRecordingImmediately,
                    dynamicOptimizationEnabled = sessionReplayConfiguration.dynamicOptimizationEnabled
                )

                if (isAlreadyRegistered()) {
                    logAlreadyRegisteredWarning(sdkCore.internalLogger)
                } else {
                    currentRegisteredCore = WeakReference(sdkCore)
                    sdkCore.registerFeature(sessionReplayFeature)
                }
            }
    }

    /**
     * Start recording session replay data.
     * @param sdkCore SDK instance to get the feature from. If not provided, default SDK instance
     * will be used.
     */
    fun startRecording(
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val sessionReplayFeature = (sdkCore as? FeatureSdkCore)
            ?.getFeature(SESSION_REPLAY_FEATURE_NAME)?.let {
                it.unwrap() as? SessionReplayFeature
            }

        sessionReplayFeature?.manuallyStartRecording()
    }

    /**
     * Stop recording session replay data.
     * @param sdkCore SDK instance to get the feature from. If not provided, default SDK instance
     * will be used.
     */
    fun stopRecording(
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val sessionReplayFeature = (sdkCore as? FeatureSdkCore)
            ?.getFeature(SESSION_REPLAY_FEATURE_NAME)?.let {
                it.unwrap() as? SessionReplayFeature
            }

        sessionReplayFeature?.manuallyStopRecording()
    }

    private fun isAlreadyRegistered() =
        currentRegisteredCore?.get()?.isCoreActive() == true

    private fun logAlreadyRegisteredWarning(internalLogger: InternalLogger) =
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = { IS_ALREADY_REGISTERED_WARNING }
        )
}
