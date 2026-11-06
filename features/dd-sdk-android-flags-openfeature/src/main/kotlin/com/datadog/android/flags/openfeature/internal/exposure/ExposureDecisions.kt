/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.exposure

import com.datadog.android.flags.model.EvaluationContext
import dev.openfeature.kotlin.sdk.EvaluationMetadata
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.Reason

/**
 * Exposure decisions of the evaluations a provider resolved on the current thread.
 *
 * OpenFeature passes the provider's metadata instance unchanged to every hook of the same evaluation,
 * so it identifies the evaluation. Hooks run on the evaluating thread, after the provider returns.
 */
internal class ExposureDecisions {

    /**
     * @param metadata the metadata instance that the provider returned for the evaluation.
     * @param hookFeatures the `HOOK` features of the selected split.
     * @param context the context of the assignment that was evaluated.
     * @param recordExposure records the exposure; null when the evaluation is not an exposure.
     */
    internal class Decision(
        val metadata: EvaluationMetadata,
        val hookFeatures: Map<String, Any>,
        val context: EvaluationContext?,
        private val recordExposure: (() -> Boolean)?
    ) {
        private var isRecorded: Boolean? = null

        val isExposure: Boolean
            get() = recordExposure != null

        /** Records the exposure on the first call; returns whether it was already recorded, or null. */
        fun complete(): Boolean? {
            val record = recordExposure ?: return null
            return isRecorded ?: record().also { isRecorded = it }
        }
    }

    private val decisions = object : ThreadLocal<ArrayDeque<Decision>>() {
        override fun initialValue(): ArrayDeque<Decision> = ArrayDeque()
    }

    fun register(
        metadata: EvaluationMetadata,
        hookFeatures: Map<String, Any>,
        context: EvaluationContext?,
        recordExposure: (() -> Boolean)?
    ) {
        if (hookFeatures.isEmpty() && recordExposure == null) return
        val queue = decisions.get() ?: return
        if (queue.size == MAX_DECISIONS_PER_THREAD) queue.removeFirstOrNull()
        queue.addLast(Decision(metadata, hookFeatures, context, recordExposure))
    }

    fun find(details: FlagEvaluationDetails<*>): Decision? =
        decisions.get()?.lastOrNull { it.metadata === details.metadata }

    /**
     * Makes the final exposure decision of a successful evaluation.
     * @return whether the exposure was already recorded, or null when [details] is not a Datadog exposure.
     */
    fun complete(details: FlagEvaluationDetails<*>): Boolean? =
        if (details.errorCode != null || details.reason == Reason.ERROR.name) null else find(details)?.complete()

    private companion object {
        // Covers evaluations nested in hooks; a decision is used by the hooks of its own evaluation only.
        const val MAX_DECISIONS_PER_THREAD = 16
    }
}
