/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.InternalLogger.Target
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.generated.RumViewEndedLog
import com.datadog.android.rum.internal.generated.logRumViewEnded

internal class ViewEndedMetricDispatcher(
    private val viewType: RumViewType,
    private val internalLogger: InternalLogger,
    instrumentationType: ViewScopeInstrumentationType? = null
) : ViewMetricDispatcher {

    private val instrumentationType: ViewScopeInstrumentationType =
        instrumentationType ?: ViewScopeInstrumentationType.Native.MANUAL

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

        internalLogger.logRumViewEnded(
            rve = RumViewEndedLog.Rve(
                duration = duration,
                loadingTime = loadingTime?.let { RumViewEndedLog.Rve.LoadingTime(it) },
                viewType = toGeneratedViewType(viewType),
                tns = RumViewEndedLog.Rve.Tns(
                    value = tnsState.initializationTime,
                    config = toGeneratedTnsConfig(tnsState.config),
                    noValueReason = if (tnsState.initializationTime == null) {
                        toGeneratedTnsNoValueReason(tnsState.noValueReason)
                    } else {
                        null
                    }
                ),
                inv = RumViewEndedLog.Rve.Inv(
                    value = invState.initializationTime,
                    config = toGeneratedInvConfig(invState.config),
                    noValueReason = if (invState.initializationTime == null) {
                        toGeneratedInvNoValueReason(invState.noValueReason)
                    } else {
                        null
                    }
                ),
                instrumentationType = instrumentationType.value
            )
        )

        metricSent = true
    }

    override fun onDurationResolved(newDuration: Long) {
        duration = newDuration
    }

    override fun onViewLoadingTimeResolved(newLoadingTime: Long) {
        loadingTime = newLoadingTime
    }

    companion object {

        internal const val VIEW_ENDED_MESSAGE = "[Mobile Metric] RUM View Ended"

        private fun toGeneratedViewType(
            viewType: RumViewType
        ): RumViewEndedLog.Rve.ViewType = when (viewType) {
            RumViewType.NONE,
            RumViewType.FOREGROUND -> RumViewEndedLog.Rve.ViewType.CUSTOM
            RumViewType.BACKGROUND -> RumViewEndedLog.Rve.ViewType.BACKGROUND
            RumViewType.APPLICATION_LAUNCH -> RumViewEndedLog.Rve.ViewType.APPLICATION_LAUNCH
        }

        private fun toGeneratedTnsConfig(
            config: ViewInitializationMetricsConfig
        ): RumViewEndedLog.Rve.Tns.Config = when (config) {
            ViewInitializationMetricsConfig.DISABLED -> RumViewEndedLog.Rve.Tns.Config.DISABLED
            ViewInitializationMetricsConfig.CUSTOM -> RumViewEndedLog.Rve.Tns.Config.CUSTOM
            ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> RumViewEndedLog.Rve.Tns.Config.TIME_BASED_DEFAULT
            ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> RumViewEndedLog.Rve.Tns.Config.TIME_BASED_CUSTOM
        }

        private fun toGeneratedInvConfig(
            config: ViewInitializationMetricsConfig
        ): RumViewEndedLog.Rve.Inv.Config = when (config) {
            ViewInitializationMetricsConfig.DISABLED -> RumViewEndedLog.Rve.Inv.Config.DISABLED
            ViewInitializationMetricsConfig.CUSTOM -> RumViewEndedLog.Rve.Inv.Config.CUSTOM
            ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> RumViewEndedLog.Rve.Inv.Config.TIME_BASED_DEFAULT
            ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> RumViewEndedLog.Rve.Inv.Config.TIME_BASED_CUSTOM
        }

        private fun toGeneratedTnsNoValueReason(
            reason: NoValueReason?
        ): RumViewEndedLog.Rve.Tns.NoValueReason = when (reason) {
            null -> RumViewEndedLog.Rve.Tns.NoValueReason.UNKNOWN
            is NoValueReason.TimeToNetworkSettle -> when (reason) {
                NoValueReason.TimeToNetworkSettle.UNKNOWN -> RumViewEndedLog.Rve.Tns.NoValueReason.UNKNOWN
                NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET -> RumViewEndedLog.Rve.Tns.NoValueReason.NOT_SETTLED_YET
                NoValueReason.TimeToNetworkSettle.NO_RESOURCES -> RumViewEndedLog.Rve.Tns.NoValueReason.NO_RESOURCES
                NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES -> RumViewEndedLog.Rve.Tns.NoValueReason.NO_INITIAL_RESOURCES
            }
            else -> RumViewEndedLog.Rve.Tns.NoValueReason.UNKNOWN
        }

        private fun toGeneratedInvNoValueReason(
            reason: NoValueReason?
        ): RumViewEndedLog.Rve.Inv.NoValueReason = when (reason) {
            null -> RumViewEndedLog.Rve.Inv.NoValueReason.UNKNOWN
            is NoValueReason.InteractionToNextView -> when (reason) {
                NoValueReason.InteractionToNextView.UNKNOWN -> RumViewEndedLog.Rve.Inv.NoValueReason.UNKNOWN
                NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW -> RumViewEndedLog.Rve.Inv.NoValueReason.NO_PREVIOUS_VIEW
                NoValueReason.InteractionToNextView.NO_ACTION -> RumViewEndedLog.Rve.Inv.NoValueReason.NO_ACTION
                NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION -> RumViewEndedLog.Rve.Inv.NoValueReason.NO_ELIGIBLE_ACTION
                NoValueReason.InteractionToNextView.DISABLED -> RumViewEndedLog.Rve.Inv.NoValueReason.DISABLED
            }
            else -> RumViewEndedLog.Rve.Inv.NoValueReason.UNKNOWN
        }
    }
}
