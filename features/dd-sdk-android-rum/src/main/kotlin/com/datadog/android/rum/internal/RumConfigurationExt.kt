/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.tracking.TrackingStrategy

/**
 * Applies a [RemoteConfiguration] to this [RumConfiguration], returning a new instance with the
 * remote values overlaid on top of the developer-supplied configuration.
 *
 * Fields absent from the remote payload (null) are left unchanged. Only the `rum` section of the
 * remote configuration is consumed here; other sections (sessionReplay, profiling, trace) are
 * handled by their respective feature modules.
 */
internal fun RumConfiguration.applyRemoteConfiguration(rc: RemoteConfiguration?): RumConfiguration {
    val rum = rc?.rum ?: return this
    return copy(
        featureConfiguration = featureConfiguration.copy(
            telemetrySampleRate = rum.telemetrySampleRate?.toFloat()
                ?: featureConfiguration.telemetrySampleRate,
            trackAnonymousUser = rum.trackAnonymousUser
                ?: featureConfiguration.trackAnonymousUser,
            userActionTracking = rum.trackUserInteractions
                ?: featureConfiguration.userActionTracking,
            backgroundEventTracking = rum.trackBackgroundEvents
                ?: featureConfiguration.backgroundEventTracking,
            trackFrustrations = rum.trackFrustrations
                ?: featureConfiguration.trackFrustrations,
            trackNonFatalAnrs = rum.trackNonFatalAnrs
                ?: featureConfiguration.trackNonFatalAnrs,
            vitalsMonitorUpdateFrequency = rum.vitalsUpdateFrequency?.toSdkFrequency()
                ?: featureConfiguration.vitalsMonitorUpdateFrequency,
            slowFramesConfiguration = featureConfiguration.applySlowFrames(rum.trackSlowFrames),
            longTaskTrackingStrategy = featureConfiguration.applyLongTask(rum.longTask)
        )
    )
}

private fun RumFeature.Configuration.applySlowFrames(
    trackSlowFrames: Boolean?
): SlowFramesConfiguration? = when (trackSlowFrames) {
    true -> slowFramesConfiguration ?: SlowFramesConfiguration.DEFAULT
    false -> null
    null -> slowFramesConfiguration
}

private fun RumFeature.Configuration.applyLongTask(
    longTask: RemoteConfiguration.LongTask?
): TrackingStrategy? {
    longTask ?: return longTaskTrackingStrategy
    return when (longTask.enabled) {
        false -> null
        true -> MainLooperLongTaskStrategy(
            longTask.threshold?.toLong()
                ?: (longTaskTrackingStrategy as? MainLooperLongTaskStrategy)?.thresholdMs
                ?: RumFeature.DEFAULT_LONG_TASK_THRESHOLD_MS
        )
        null -> longTaskTrackingStrategy
    }
}

private fun RemoteConfiguration.VitalsUpdateFrequency.toSdkFrequency(): VitalsUpdateFrequency =
    when (this) {
        RemoteConfiguration.VitalsUpdateFrequency.FREQUENT -> VitalsUpdateFrequency.FREQUENT
        RemoteConfiguration.VitalsUpdateFrequency.AVERAGE -> VitalsUpdateFrequency.AVERAGE
        RemoteConfiguration.VitalsUpdateFrequency.RARE -> VitalsUpdateFrequency.RARE
        RemoteConfiguration.VitalsUpdateFrequency.NEVER -> VitalsUpdateFrequency.NEVER
    }
