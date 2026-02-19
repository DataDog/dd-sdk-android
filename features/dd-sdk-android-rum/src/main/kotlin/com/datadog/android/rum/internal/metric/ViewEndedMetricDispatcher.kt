/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger

internal class ViewEndedMetricDispatcher(
    private val viewType: RumViewType,
    private val internalLogger: InternalLogger,
    instrumentationType: ViewScopeInstrumentationType? = null
) : ViewMetricDispatcher {

    private val instrumentationType: ViewScopeInstrumentationType =
        instrumentationType ?: ViewScopeInstrumentationType.Native.MANUAL

    private val logger = DdSdkAndroidRumLogger(internalLogger)

    private var duration: Long? = null

    private var loadingTime: Long? = null
    private var metricSent: Boolean = false

    override fun sendViewEnded(invState: ViewInitializationMetricsState, tnsState: ViewInitializationMetricsState) {
        if (metricSent) {
            logger.logViewEndedSentTwice()
            return
        }

        logger.logRumViewEnded(
            rve = DdSdkAndroidRumLogger.Rve(
                duration = duration,
                loadingTime = loadingTime?.let { DdSdkAndroidRumLogger.Rve.LoadingTime(it) },
                viewType = toGeneratedViewType(viewType),
                tns = DdSdkAndroidRumLogger.Rve.Tns(
                    value = tnsState.initializationTime,
                    config = toGeneratedTnsConfig(tnsState.config),
                    noValueReason = if (tnsState.initializationTime == null) {
                        toGeneratedTnsNoValueReason(tnsState.noValueReason)
                    } else {
                        null
                    }
                ),
                inv = DdSdkAndroidRumLogger.Rve.Inv(
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
        ): DdSdkAndroidRumLogger.Rve.ViewType = when (viewType) {
            RumViewType.NONE,
            RumViewType.FOREGROUND -> DdSdkAndroidRumLogger.Rve.ViewType.CUSTOM
            RumViewType.BACKGROUND -> DdSdkAndroidRumLogger.Rve.ViewType.BACKGROUND
            RumViewType.APPLICATION_LAUNCH -> DdSdkAndroidRumLogger.Rve.ViewType.APPLICATION_LAUNCH
        }

        private fun toGeneratedTnsConfig(
            config: ViewInitializationMetricsConfig
        ): DdSdkAndroidRumLogger.Rve.Tns.Config = when (config) {
            ViewInitializationMetricsConfig.DISABLED -> DdSdkAndroidRumLogger.Rve.Tns.Config.DISABLED
            ViewInitializationMetricsConfig.CUSTOM -> DdSdkAndroidRumLogger.Rve.Tns.Config.CUSTOM
            ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> DdSdkAndroidRumLogger.Rve.Tns.Config.TIME_BASED_DEFAULT
            ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> DdSdkAndroidRumLogger.Rve.Tns.Config.TIME_BASED_CUSTOM
        }

        private fun toGeneratedInvConfig(
            config: ViewInitializationMetricsConfig
        ): DdSdkAndroidRumLogger.Rve.Inv.Config = when (config) {
            ViewInitializationMetricsConfig.DISABLED -> DdSdkAndroidRumLogger.Rve.Inv.Config.DISABLED
            ViewInitializationMetricsConfig.CUSTOM -> DdSdkAndroidRumLogger.Rve.Inv.Config.CUSTOM
            ViewInitializationMetricsConfig.TIME_BASED_DEFAULT -> DdSdkAndroidRumLogger.Rve.Inv.Config.TIME_BASED_DEFAULT
            ViewInitializationMetricsConfig.TIME_BASED_CUSTOM -> DdSdkAndroidRumLogger.Rve.Inv.Config.TIME_BASED_CUSTOM
        }

        private fun toGeneratedTnsNoValueReason(
            reason: NoValueReason?
        ): DdSdkAndroidRumLogger.Rve.Tns.NoValueReason = when (reason) {
            null -> DdSdkAndroidRumLogger.Rve.Tns.NoValueReason.UNKNOWN
            is NoValueReason.TimeToNetworkSettle -> when (reason) {
                NoValueReason.TimeToNetworkSettle.UNKNOWN -> DdSdkAndroidRumLogger.Rve.Tns.NoValueReason.UNKNOWN
                NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET -> DdSdkAndroidRumLogger.Rve.Tns.NoValueReason.NOT_SETTLED_YET
                NoValueReason.TimeToNetworkSettle.NO_RESOURCES -> DdSdkAndroidRumLogger.Rve.Tns.NoValueReason.NO_RESOURCES
                NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES -> DdSdkAndroidRumLogger.Rve.Tns.NoValueReason.NO_INITIAL_RESOURCES
            }
            else -> DdSdkAndroidRumLogger.Rve.Tns.NoValueReason.UNKNOWN
        }

        private fun toGeneratedInvNoValueReason(
            reason: NoValueReason?
        ): DdSdkAndroidRumLogger.Rve.Inv.NoValueReason = when (reason) {
            null -> DdSdkAndroidRumLogger.Rve.Inv.NoValueReason.UNKNOWN
            is NoValueReason.InteractionToNextView -> when (reason) {
                NoValueReason.InteractionToNextView.UNKNOWN -> DdSdkAndroidRumLogger.Rve.Inv.NoValueReason.UNKNOWN
                NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW -> DdSdkAndroidRumLogger.Rve.Inv.NoValueReason.NO_PREVIOUS_VIEW
                NoValueReason.InteractionToNextView.NO_ACTION -> DdSdkAndroidRumLogger.Rve.Inv.NoValueReason.NO_ACTION
                NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION -> DdSdkAndroidRumLogger.Rve.Inv.NoValueReason.NO_ELIGIBLE_ACTION
                NoValueReason.InteractionToNextView.DISABLED -> DdSdkAndroidRumLogger.Rve.Inv.NoValueReason.DISABLED
            }
            else -> DdSdkAndroidRumLogger.Rve.Inv.NoValueReason.UNKNOWN
        }
    }
}
