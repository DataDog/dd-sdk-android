/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.tools.annotation.NoOpImplementation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@NoOpImplementation
internal interface UISlownessMetricDispatcher {

    fun onViewCreated(viewId: String)

    fun incrementSlowFrameCount(viewId: String)

    fun incrementIgnoredFrameCount(viewId: String)

    fun incrementMissedFrameCount(viewId: String)

    fun sendMetric(viewId: String)
}

internal class DefaultUISlownessMetricDispatcher(
    private val config: SlowFramesConfiguration,
    private val internalLogger: InternalLogger,
    private val samplingRate: Float = DEFAULT_SAMPLING_RATE
) : UISlownessMetricDispatcher {

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
    override fun sendMetric(viewId: String) {
        val telemetry = viewTelemetry.remove(viewId)
        if (telemetry == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                target = Target.TELEMETRY,
                messageBuilder = { "No telemetry found for viewId=$viewId" }
            )
            return
        }

        internalLogger.logMetric(
            samplingRate = samplingRate,
            messageBuilder = { UI_SLOWNESS_MESSAGE },
            additionalProperties = buildMetricAttributesMap(
                slowFramesCount = telemetry.slowFramesCount.get(),
                ignoredFramesCount = telemetry.ignoredFramesCount.get(),
                missedFramesCount = telemetry.missedFrameCount.get(),
            )
        )
    }

    private fun buildMetricAttributesMap(
        slowFramesCount: Int,
        ignoredFramesCount: Int,
        missedFramesCount: Int
    ): Map<String, Any> = buildMap {
        put(KEY_METRIC_TYPE, VALUE_METRIC_TYPE)
        put(
            KEY_RUM_UI_SLOWNESS,
            buildMap {
                put(
                    KEY_SLOW_FRAMES,
                    buildMap {
                        put(KEY_COUNT, slowFramesCount)
                        put(KEY_IGNORED_COUNT, ignoredFramesCount)
                        put(KEY_MISSED_COUNT, missedFramesCount)
                        put(
                            KEY_CONFIG,
                            buildMap {
                                put(KEY_MAX_COUNT, config.maxSlowFramesAmount)
                                put(KEY_SLOW_FRAME_THRESHOLD, 2.0f) // no option this value to be changed for now
                                put(KEY_MAX_DURATION, config.maxSlowFrameThresholdNs)
                                put(KEY_VIEW_MIN_DURATION, config.minViewLifetimeThresholdNs)
                            }
                        )
                    }
                )
            }
        )
    }

    companion object {
        private const val DEFAULT_SAMPLING_RATE: Float = 0.75f

        internal const val UI_SLOWNESS_MESSAGE = "[Mobile Metric] RUM UI Slowness"

        internal const val KEY_METRIC_TYPE = "metric_type"
        internal const val VALUE_METRIC_TYPE = "rum ui slowness"

        internal const val KEY_RUM_UI_SLOWNESS = "rum_ui_slowness"

        internal const val KEY_SLOW_FRAMES = "slow_frames"
        internal const val KEY_COUNT = "count"
        internal const val KEY_IGNORED_COUNT = "ignored_count"
        internal const val KEY_MISSED_COUNT = "missed_count"
        internal const val KEY_CONFIG = "config"
        internal const val KEY_MAX_COUNT = "max_count"
        internal const val KEY_SLOW_FRAME_THRESHOLD = "slow_frame_threshold"
        internal const val KEY_MAX_DURATION = "max_duration"
        internal const val KEY_VIEW_MIN_DURATION = "view_min_duration"
    }
}
