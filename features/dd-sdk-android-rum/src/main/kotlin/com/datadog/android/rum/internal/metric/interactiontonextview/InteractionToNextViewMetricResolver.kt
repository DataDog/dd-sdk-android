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

internal class InteractionToNextViewMetricResolver(
    private val internalLogger: InternalLogger,
    private val ingestionValidator: InteractionIngestionValidator = ActionTypeInteractionValidator(),
    private val lastInteractionIdentifier: LastInteractionIdentifier = TimeBasedInteractionIdentifier()
) {
    private val lastInteractions = LinkedHashMap<String, InternalInteractionContext>()
    private val lastViewCreatedDiagnostics = LinkedHashMap<String, DiagnosticInfo>()

    fun onViewCreated(viewId: String, timestamp: Long) {
        lastViewCreatedDiagnostics[viewId] = DiagnosticInfo(timestamp)
        purgeOldEntries()
    }

    fun onActionSent(context: InternalInteractionContext) {
        if (ingestionValidator.validate(context)) {
            lastInteractions[context.viewId] = context
            lastViewCreatedDiagnostics[context.viewId]?.let { diagnostic ->
                diagnostic.actionsCount++

                resolveCurrentViewCreationTimestamp(context.viewId)?.let { currentViewCreationTimestamp ->
                    val previousViewLastInteractionContext = context.toPreviousViewLastInteractionContext(
                        currentViewCreationTimestamp
                    )

                    if (lastInteractionIdentifier.validate(previousViewLastInteractionContext)) {
                        diagnostic.actionsEligibleCount++
                    }
                }
            }
        }
        purgeOldEntries()
    }

    fun getState(viewId: String) = ViewInitializationMetricsState(
        config = lastInteractionIdentifier.toConfig(),
        initializationTime = resolveMetric(viewId),
        noValueReason = resolveNoValueReason(viewId)
    )

    @Suppress("ReturnCount")
    fun resolveMetric(viewId: String): Long? {
        purgeOldEntries()
        val currentViewCreatedTimestamp = resolveCurrentViewCreationTimestamp(viewId) ?: return null
        val lastPrevViewInteraction = resolveLastInteraction(viewId, currentViewCreatedTimestamp)
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
        if (lastViewCreatedDiagnostics.size > 1) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] No previous interaction found for this viewId:$viewId" }
            )
        }
        return null
    }

    private fun resolveCurrentViewCreationTimestamp(viewId: String): Long? {
        val currentViewCreationTimestamp = lastViewCreatedDiagnostics[viewId]?.createdTimestamp
        if (currentViewCreationTimestamp == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] The view was not yet created for this viewId:$viewId" }
            )
        }
        return currentViewCreationTimestamp
    }

    private fun resolveLastInteraction(viewId: String, currentViewCreatedTimestamp: Long): InternalInteractionContext? {
        val previousViewId = resolvePreviousViewId(viewId)
        if (previousViewId != null) {
            lastInteractions[previousViewId]?.let {
                val context = it.toPreviousViewLastInteractionContext(currentViewCreatedTimestamp)
                if (lastInteractionIdentifier.validate(context)) {
                    return it
                }
            }
        }
        return null
    }

    private fun resolvePreviousViewId(viewId: String): String? {
        val currentViewIdIndex = lastViewCreatedDiagnostics.keys.indexOf(viewId)
        return lastViewCreatedDiagnostics.keys.elementAtOrNull(currentViewIdIndex - 1)
    }

    private fun purgeOldEntries() {
        while (lastInteractions.entries.size > MAX_ENTRIES) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            // we make sure the collection is never empty
            lastInteractions.entries.remove(lastInteractions.entries.first())
        }
        while (lastViewCreatedDiagnostics.entries.size > MAX_ENTRIES) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            // we make sure the collection is never empty
            lastViewCreatedDiagnostics.remove(lastViewCreatedDiagnostics.keys.first())
        }
    }

    @VisibleForTesting
    internal fun lasInteractions(): Map<String, InternalInteractionContext> {
        return lastInteractions
    }

    @VisibleForTesting
    internal fun lastViewCreatedTimestamps(): Map<String, Long> {
        return lastViewCreatedDiagnostics.mapValues { it.value.createdTimestamp }
    }

    private fun resolveNoValueReason(viewId: String): NoValueReason.InteractionToNextView {
        val previousViewId = resolvePreviousViewId(viewId)
            ?: return NoValueReason.InteractionToNextView.NO_PREVIOUS_VIEW

        val diagnostic = lastViewCreatedDiagnostics[previousViewId]
            ?: return NoValueReason.InteractionToNextView.UNKNOWN

        if (diagnostic.actionsCount == 0) {
            return NoValueReason.InteractionToNextView.NO_ACTION
        }

        if (diagnostic.actionsEligibleCount == 0) {
            return NoValueReason.InteractionToNextView.NO_ELIGIBLE_ACTION
        }

        return NoValueReason.InteractionToNextView.UNKNOWN
    }

    companion object {
        // we need to keep at least 4 entries to be able to calculate the metric for consecutive views that
        // are going to be created almost in the same time and will rely on the previous view interaction
        internal const val MAX_ENTRIES = 4

        private fun LastInteractionIdentifier.toConfig(): ViewInitializationMetricsConfig {
            if (this !is TimeBasedInteractionIdentifier) return ViewInitializationMetricsConfig.CUSTOM

            return if (isDefault()) {
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

        private class DiagnosticInfo(
            val createdTimestamp: Long,
            var actionsCount: Int = 0,
            var actionsEligibleCount: Int = 0
        )
    }
}
