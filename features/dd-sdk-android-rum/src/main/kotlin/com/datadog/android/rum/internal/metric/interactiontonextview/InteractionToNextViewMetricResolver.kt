/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.interactiontonextview

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsConfig
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsState
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.interactiontonextview.PreviousViewLastInteractionContext
import com.datadog.android.rum.metric.interactiontonextview.TimeBasedInteractionIdentifier

@Suppress("TooManyFunctions")
internal class InteractionToNextViewMetricResolver(
    private val internalLogger: InternalLogger,
    private val ingestionValidator: InteractionIngestionValidator = ActionTypeInteractionValidator(),
    private val lastInteractionIdentifier: LastInteractionIdentifier = TimeBasedInteractionIdentifier()
) {
    private val lastInteractions = LinkedHashMap<String, InternalInteractionContext>()
    private val lastViewCreatedTimestamps = LinkedHashMap<String, Long>()

    fun onViewCreated(viewId: String, timestamp: Long) {
        lastViewCreatedTimestamps[viewId] = timestamp
        purgeOldEntries()
    }

    fun onActionSent(context: InternalInteractionContext) {
        if (ingestionValidator.validate(context)) {
            lastInteractions[context.viewId] = context
        }
        purgeOldEntries()
    }

    @Suppress("ReturnCount")
    fun resolveMetric(viewId: String): Long? {
        purgeOldEntries()
        val currentViewCreatedTimestamp = resolveCurrentViewCreationTimestamp(viewId) ?: return null
        val previousViewId = resolvePreviousViewId(viewId)
        val lastPrevViewInteraction = previousViewId?.let {
            resolveLastInteraction(it, currentViewCreatedTimestamp)
        }
        if (lastPrevViewInteraction != null) {
            val difference = currentViewCreatedTimestamp - lastPrevViewInteraction.eventCreatedAtNanos
            if (difference > 0) {
                return difference
            } else {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    {
                        "[ViewNetworkSettledMetric] The difference between the last interaction " +
                            "and the current view is negative for viewId:$viewId"
                    }
                )
                return null
            }
        }
        // in case there are no previous interactions for this view and there's only one view created
        // we are probably in the first view of the app (AppLaunch) and we can't calculate the metric
        if (lastViewCreatedTimestamps.size > 1) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] No previous interaction found for this viewId:$viewId" }
            )
        }
        return null
    }

    fun getState(viewId: String) = resolveMetric(viewId).let { metricValue ->
        ViewInitializationMetricsState(
            initializationTime = metricValue,
            config = lastInteractionIdentifier.toConfig(),
            noValueReason = if (metricValue == null) resolveNoValueReason(viewId) else null
        )
    }

    @Suppress("ReturnCount")
    private fun resolveNoValueReason(viewId: String): NoValueReason.InteractionToNextView {
        // First of all, if there is no timestamp for the current view, all other metrics are meaningless.
        val currentViewCreatedTimestamp = resolveCurrentViewCreationTimestamp(viewId)
            ?: return NoValueReason.InteractionToNextView.UNKNOWN

        // Second, if there is no previous view information, we are either on the very first screen of the app or
        // lost the previous view information for some reason
        val previousViewId = resolvePreviousViewId(viewId)
            ?: return NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW

        // Third - the case where there is no interaction from the previous view.
        if (lastInteractions[previousViewId] == null) {
            return NoValueReason.InteractionToNextView.NO_ACTION
        }

        // Finally - checking that the previous view interaction is eligible
        resolveLastInteraction(previousViewId, currentViewCreatedTimestamp)
            ?: return NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION

        // Last resort - usually reproducible if metric actually exists
        return NoValueReason.InteractionToNextView.UNKNOWN
    }

    private fun purgeOldEntries() {
        while (lastInteractions.entries.size > MAX_ENTRIES) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            // we make sure the collection is never empty
            lastInteractions.entries.remove(lastInteractions.entries.first())
        }
        while (lastViewCreatedTimestamps.entries.size > MAX_ENTRIES) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            // we make sure the collection is never empty
            lastViewCreatedTimestamps.remove(lastViewCreatedTimestamps.keys.first())
        }
    }

    private fun resolveCurrentViewCreationTimestamp(viewId: String): Long? {
        val currentViewCreationTimestamp = lastViewCreatedTimestamps[viewId]
        if (currentViewCreationTimestamp == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] The view was not yet created for this viewId:$viewId" }
            )
        }
        return currentViewCreationTimestamp
    }

    private fun resolveLastInteraction(
        previousViewId: String,
        currentViewCreatedTimestamp: Long
    ): InternalInteractionContext? {
        lastInteractions[previousViewId]?.let {
            val context = it.toPreviousViewLastInteractionContext(currentViewCreatedTimestamp)
            if (lastInteractionIdentifier.validate(context)) {
                return it
            }
        }

        return null
    }

    private fun resolvePreviousViewId(viewId: String): String? {
        val currentViewIdIndex = lastViewCreatedTimestamps.keys.indexOf(viewId)
        return lastViewCreatedTimestamps.keys.elementAtOrNull(currentViewIdIndex - 1)
    }

    @VisibleForTesting
    internal fun lasInteractions(): Map<String, InternalInteractionContext> {
        return lastInteractions
    }

    @VisibleForTesting
    internal fun lastViewCreatedTimestamps(): Map<String, Long> {
        return lastViewCreatedTimestamps
    }

    companion object {
        // we need to keep at least 4 entries to be able to calculate the metric for consecutive views that
        // are going to be created almost in the same time and will rely on the previous view interaction
        internal const val MAX_ENTRIES = 4

        private fun LastInteractionIdentifier.toConfig(): ViewInitializationMetricsConfig {
            if (this !is TimeBasedInteractionIdentifier) return ViewInitializationMetricsConfig.CUSTOM

            return if (defaultThresholdUsed()) {
                ViewInitializationMetricsConfig.TIME_BASED_DEFAULT
            } else {
                ViewInitializationMetricsConfig.TIME_BASED_CUSTOM
            }
        }

        private fun InternalInteractionContext.toPreviousViewLastInteractionContext(
            currentViewCreatedTimestamp: Long
        ) = PreviousViewLastInteractionContext(
            actionType,
            eventCreatedAtNanos,
            currentViewCreatedTimestamp
        )
    }
}
