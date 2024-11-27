/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.interactiontonextview

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger

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
        val currentViewCreatedTimestamp = lastViewCreatedTimestamps[viewId]
        if (currentViewCreatedTimestamp == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] The view was not yet created for this viewId:$viewId" }
            )
            return null
        }
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
        if (lastViewCreatedTimestamps.size > 1) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] No previous interaction found for this viewId:$viewId" }
            )
        }
        return null
    }

    private fun resolveLastInteraction(viewId: String, currentViewCreatedTimestamp: Long): InternalInteractionContext? {
        val currentViewIdIndex = lastViewCreatedTimestamps.keys.indexOf(viewId)
        val previousViewId = lastViewCreatedTimestamps.keys.elementAtOrNull(currentViewIdIndex - 1)
        if (previousViewId != null) {
            lastInteractions[previousViewId]?.let {
                val context = PreviousViewLastInteractionContext(
                    it.actionType,
                    it.eventCreatedAtNanos,
                    currentViewCreatedTimestamp
                )
                if (lastInteractionIdentifier.validate(context)) {
                    return it
                }
            }
        }
        return null
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
    }
}
