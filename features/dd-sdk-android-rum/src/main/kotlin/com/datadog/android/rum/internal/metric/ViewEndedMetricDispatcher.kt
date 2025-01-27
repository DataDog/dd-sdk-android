/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target

internal class ViewEndedMetricDispatcher(
    private val viewType: ViewMetricDispatcher.ViewType,
    private val internalLogger: InternalLogger,
    private val samplingRate: Float = DEFAULT_SAMPLE_RATE,
) : ViewMetricDispatcher {

    private var duration: Long? = null
    private var loadingTime: Long? = null
    private var metricSent: Boolean = false

    override fun sendViewEnded(invState: ViewInitializationMetricsState, tnsState: ViewInitializationMetricsState) {
        if (metricSent) {
            return internalLogger.log(
                InternalLogger.Level.WARN,
                target = Target.TELEMETRY,
                messageBuilder = { "Trying to send 'view ended' more than once" }
            )
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
    ): Map<String, Any?> = buildMap {
        put(KEY_DURATION, duration)
        put(KEY_LOADING_TIME, buildMap { put(KEY_VALUE, loadingTime) })
        put(KEY_VIEW_TYPE, viewType.toAttributeValue())
        put(
            KEY_TIME_TO_NETWORK_SETTLED,
            buildMap {
                put(KEY_VALUE, tnsState.initializationTime)
                put(KEY_CONFIG, tnsState.config.toAttributeValue())
                if (null == tnsState.initializationTime) {
                    put(
                        KEY_NO_VALUE_REASON,
                        tnsState.noValueReason.toAttributeValue()
                    )
                }
            }
        )
        put(
            KEY_INTERACTION_TO_NEXT_VIEW,
            buildMap {
                put(KEY_VALUE, invState.initializationTime)
                put(KEY_CONFIG, invState.config.toAttributeValue())
                if (null == invState.initializationTime) {
                    put(
                        KEY_NO_VALUE_REASON,
                        invState.noValueReason.toAttributeValue()
                    )
                }
            }
        )
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE: Float = 0.75f

        internal const val VIEW_ENDED_MESSAGE = "[Mobile Metric] RUM View Ended"

        internal const val KEY_METRIC_TYPE = "metric_type"

        internal const val VALUE_METRIC_TYPE = "rum view ended"

        internal const val KEY_RVE = "rve"
        internal const val KEY_DURATION = "duration"
        internal const val KEY_LOADING_TIME = "loading_time_value"

        internal const val KEY_TIME_TO_NETWORK_SETTLED = "tns"
        internal const val KEY_VALUE = "value"
        internal const val KEY_CONFIG = "config"
        private const val VALUE_TIME_BASED_DEFAULT = "time_based_default"
        private const val VALUE_TIME_BASED_CUSTOM = "time_based_custom"

        internal const val KEY_NO_VALUE_REASON = "no_value_reason"
        internal const val VALUE_NO_RESOURCES = "no_resources"
        internal const val VALUE_NO_INITIAL_RESOURCES = "no_initial_resources"
        internal const val VALUE_NOT_SETTLED_YET = "not_settled_yet"
        internal const val VALUE_UNKNOWN = "unknown"

        internal const val KEY_INTERACTION_TO_NEXT_VIEW = "inv"
        internal const val VALUE_NO_ACTION = "no_action"
        internal const val VALUE_NO_ELIGIBLE_ACTION = "no_eligible_action"
        internal const val VALUE_NO_PREVIOUS_VIEW = "no_previous_view"

        internal const val KEY_VIEW_TYPE = "view_type"
        internal const val VALUE_APPLICATION_LAUNCH = "application_launch"
        internal const val VALUE_BACKGROUND = "background"
        internal const val VALUE_CUSTOM = "custom"

        private fun ViewMetricDispatcher.ViewType.toAttributeValue() = when (this) {
            ViewMetricDispatcher.ViewType.CUSTOM -> VALUE_CUSTOM
            ViewMetricDispatcher.ViewType.BACKGROUND -> VALUE_BACKGROUND
            ViewMetricDispatcher.ViewType.APPLICATION -> VALUE_APPLICATION_LAUNCH
        }

        private fun ViewInitializationMetricsConfig.toAttributeValue(): String = when (this) {
            ViewInitializationMetricsConfig.CUSTOM -> VALUE_CUSTOM
            ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> VALUE_TIME_BASED_DEFAULT
            ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> VALUE_TIME_BASED_CUSTOM
        }

        private fun NoValueReason?.toAttributeValue(): String = when (this) {
            null -> VALUE_UNKNOWN

            is NoValueReason.InteractionToNextView -> when (this) {
                NoValueReason.InteractionToNextView.UNKNOWN -> VALUE_UNKNOWN
                NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW -> VALUE_NO_PREVIOUS_VIEW
                NoValueReason.InteractionToNextView.NO_ACTION -> VALUE_NO_ACTION
                NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION -> VALUE_NO_ELIGIBLE_ACTION
            }

            is NoValueReason.TimeToNetworkSettle -> when (this) {
                NoValueReason.TimeToNetworkSettle.UNKNOWN -> VALUE_UNKNOWN
                NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET -> VALUE_NOT_SETTLED_YET
                NoValueReason.TimeToNetworkSettle.NO_RESOURCES -> VALUE_NO_RESOURCES
                NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES -> VALUE_NO_INITIAL_RESOURCES
            }
        }
    }
}
