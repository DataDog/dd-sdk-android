/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger
import com.datadog.tools.annotation.NoOpImplementation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@NoOpImplementation
internal interface UISlownessMetricDispatcher {

    fun onViewCreated(viewId: String)

    fun incrementSlowFrameCount(viewId: String)

    fun incrementIgnoredFrameCount(viewId: String)

    fun incrementMissedFrameCount(viewId: String)

    fun sendMetric(viewId: String, viewDurationNs: Long)
}

internal class DefaultUISlownessMetricDispatcher(
    private val config: SlowFramesConfiguration,
    private val internalLogger: InternalLogger
) : UISlownessMetricDispatcher {

    private val logger = DdSdkAndroidRumLogger(internalLogger)

    internal data class SlowFramesTelemetry(
        val slowFramesCount: AtomicInteger = AtomicInteger(0),
        val ignoredFramesCount: AtomicInteger = AtomicInteger(0),
        val missedFrameCount: AtomicInteger = AtomicInteger(0)
    )

    private val viewTelemetry = ConcurrentHashMap<String, SlowFramesTelemetry>()

    // Called from the main thread
    override fun onViewCreated(viewId: String) {
        viewTelemetry.putIfAbsent(viewId, SlowFramesTelemetry())
    }

    // Called from the background thread
    override fun incrementSlowFrameCount(viewId: String) {
        viewTelemetry[viewId]?.slowFramesCount?.incrementAndGet()
    }

    // Called from the background thread
    override fun incrementIgnoredFrameCount(viewId: String) {
        viewTelemetry[viewId]?.ignoredFramesCount?.incrementAndGet()
    }

    // Called from the background thread
    override fun incrementMissedFrameCount(viewId: String) {
        viewTelemetry[viewId]?.missedFrameCount?.incrementAndGet()
    }

    // Called from the main thread
    override fun sendMetric(viewId: String, viewDurationNs: Long) {
        val telemetry = viewTelemetry.remove(viewId)
        if (telemetry == null) {
            logger.logNoTelemetryForView(viewId = viewId)
            return
        }

        logger.logUiSlowness(
            DdSdkAndroidRumLogger.RumUiSlowness(
                viewDuration = viewDurationNs,
                slowFrames = DdSdkAndroidRumLogger.RumUiSlowness.SlowFrames(
                    count = telemetry.slowFramesCount.get(),
                    ignoredCount = telemetry.ignoredFramesCount.get(),
                    missedCount = telemetry.missedFrameCount.get(),
                    config = DdSdkAndroidRumLogger.RumUiSlowness.SlowFrames.Config(
                        maxCount = config.maxSlowFramesAmount,
                        maxDuration = config.maxSlowFrameThresholdNs,
                        viewMinDuration = config.minViewLifetimeThresholdNs
                    )
                )
            )
        )
    }
}
