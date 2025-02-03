/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target
import com.datadog.android.rum.internal.domain.scope.RumViewType

internal class ViewEndedMetricDispatcher(
    private val viewType: RumViewType,
    private val internalLogger: InternalLogger,
    private val samplingRate: Float = DEFAULT_SAMPLE_RATE
) : ViewMetricDispatcher {

    private var duration: Long? = null
    private var loadingTime: Long? = null
    private var metricSent: Boolean = false

    override fun sendViewEnded(invState: ViewInitializationMetricsState, tnsState: ViewInitializationMetricsState) {
        if (metricSent) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                target = Target.TELEMETRY,
                messageBuilder = { "Trying to send 'view ended' more than once" }
            )
            return
        }

        internalLogger.logMetric(
            messageBuilder = { VIEW_ENDED_MESSAGE },
            additionalProperties = buildMetricAttributesMap(invState, tnsState),
            samplingRate = samplingRate
        )

        metricSent = true
    }

    override fun onDurationResolved(newDuration: Long) {
        duration = newDuration
    }

    override fun onViewLoadingTimeResolved(newLoadingTime: Long) {
        loadingTime = newLoadingTime
    }

    private fun buildMetricAttributesMap(
        invState: ViewInitializationMetricsState,
        tnsState: ViewInitializationMetricsState
    ): Map<String, Any?> = buildMap {
        put(KEY_METRIC_TYPE, VALUE_METRIC_TYPE)
        put(KEY_RVE, buildAttributesMap(invState, tnsState))
    }

    private fun buildAttributesMap(
        invState: ViewInitializationMetricsState,
        tnsState: ViewInitializationMetricsState
    ): Map<String, Any> = buildMap {
        putNonNull(KEY_DURATION, duration)
        if (loadingTime != null) {
            put(KEY_LOADING_TIME, buildMap { put(KEY_VALUE, loadingTime) })
        }
        put(KEY_VIEW_TYPE, toAttributeValue(viewType))
        put(
            KEY_TIME_TO_NETWORK_SETTLED,
            buildMap {
                put(KEY_VALUE, tnsState.initializationTime)
                put(KEY_CONFIG, toAttributeValue(tnsState.config))
                if (tnsState.initializationTime == null) {
                    putNonNull(KEY_NO_VALUE_REASON, toAttributeValue(tnsState.noValueReason))
                }
            }
        )
        put(
            KEY_INTERACTION_TO_NEXT_VIEW,
            buildMap {
                put(KEY_VALUE, invState.initializationTime)
                put(KEY_CONFIG, toAttributeValue(invState.config))
                if (invState.initializationTime == null) {
                    putNonNull(KEY_NO_VALUE_REASON, toAttributeValue(invState.noValueReason))
                }
            }
        )
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE: Float = 75f

        internal const val VIEW_ENDED_MESSAGE = "[Mobile Metric] RUM View Ended"

        internal const val KEY_METRIC_TYPE = "metric_type"

        private const val VALUE_METRIC_TYPE = "rum view ended"

        internal const val KEY_RVE = "rve"
        internal const val KEY_DURATION = "duration"
        internal const val KEY_LOADING_TIME = "loading_time"

        internal const val KEY_TIME_TO_NETWORK_SETTLED = "tns"
        internal const val KEY_VALUE = "value"
        internal const val KEY_CONFIG = "config"
        private const val VALUE_TIME_BASED_DEFAULT = "time_based_default"
        private const val VALUE_TIME_BASED_CUSTOM = "time_based_custom"

        internal const val KEY_NO_VALUE_REASON = "no_value_reason"
        private const val VALUE_NO_RESOURCES = "no_resources"
        private const val VALUE_NO_INITIAL_RESOURCES = "no_initial_resources"
        private const val VALUE_NOT_SETTLED_YET = "not_settled_yet"
        private const val VALUE_UNKNOWN = "unknown"

        internal const val KEY_INTERACTION_TO_NEXT_VIEW = "inv"
        private const val VALUE_NO_ACTION = "no_action"
        private const val VALUE_NO_ELIGIBLE_ACTION = "no_eligible_action"
        private const val VALUE_NO_PREVIOUS_VIEW = "no_previous_view"

        internal const val KEY_VIEW_TYPE = "view_type"
        private const val VALUE_APPLICATION_LAUNCH = "application_launch"
        private const val VALUE_BACKGROUND = "background"
        private const val VALUE_CUSTOM = "custom"

        private fun <K, V> MutableMap<K, V>.putNonNull(key: K, value: V?) {
            if (value != null) put(key, value)
        }

        @VisibleForTesting
        internal fun toAttributeValue(viewType: RumViewType) = when (viewType) {
            RumViewType.NONE,
            RumViewType.FOREGROUND -> VALUE_CUSTOM
            RumViewType.BACKGROUND -> VALUE_BACKGROUND
            RumViewType.APPLICATION_LAUNCH -> VALUE_APPLICATION_LAUNCH
        }

        @VisibleForTesting
        internal fun toAttributeValue(config: ViewInitializationMetricsConfig): String = when (config) {
            ViewInitializationMetricsConfig.CUSTOM -> VALUE_CUSTOM
            ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> VALUE_TIME_BASED_DEFAULT
            ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> VALUE_TIME_BASED_CUSTOM
        }

        @VisibleForTesting
        internal fun toAttributeValue(reason: NoValueReason?): String {
            return when (reason) {
                null -> VALUE_UNKNOWN

                is NoValueReason.InteractionToNextView -> when (reason) {
                    NoValueReason.InteractionToNextView.UNKNOWN -> VALUE_UNKNOWN
                    NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW -> VALUE_NO_PREVIOUS_VIEW
                    NoValueReason.InteractionToNextView.NO_ACTION -> VALUE_NO_ACTION
                    NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION -> VALUE_NO_ELIGIBLE_ACTION
                }

                is NoValueReason.TimeToNetworkSettle -> when (reason) {
                    NoValueReason.TimeToNetworkSettle.UNKNOWN -> VALUE_UNKNOWN
                    NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET -> VALUE_NOT_SETTLED_YET
                    NoValueReason.TimeToNetworkSettle.NO_RESOURCES -> VALUE_NO_RESOURCES
                    NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES -> VALUE_NO_INITIAL_RESOURCES
                }
            }
        }
    }
}
