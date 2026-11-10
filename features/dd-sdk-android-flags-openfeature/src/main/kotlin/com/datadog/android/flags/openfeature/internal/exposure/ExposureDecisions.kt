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
import java.util.WeakHashMap

/**
 * Exposure decisions of the evaluations that a provider resolved.
 *
 * OpenFeature passes the provider's metadata instance unchanged to every hook of the same evaluation, and the
 * provider makes that metadata unique, so it keys the evaluation's decision. A decision is released when
 * OpenFeature drops the evaluation result, so nested evaluations cannot evict it.
 */
internal class ExposureDecisions {

    /**
     * @param hookFeatures the `HOOK` features of the selected split.
     * @param context the context of the assignment that was evaluated.
     * @param recordExposure records the exposure; null when the evaluation is not an exposure.
     */
    internal class Decision(
        val hookFeatures: Map<String, Any>,
        val context: EvaluationContext?,
        private val recordExposure: (() -> Boolean)?
    ) {
        private var isRecorded: Boolean? = null

        val isExposure: Boolean
            get() = recordExposure != null

        /** Records the exposure on the first call; returns whether it was already recorded, or null. */
        @Synchronized
        fun complete(): Boolean? {
            val record = recordExposure ?: return null
            return isRecorded ?: record().also { isRecorded = it }
        }
    }

    // A decision must not reference its metadata: the map would then never release the entry.
    private val decisions = WeakHashMap<EvaluationMetadata, Decision>()

    fun register(
        metadata: EvaluationMetadata,
        hookFeatures: Map<String, Any>,
        context: EvaluationContext?,
        recordExposure: (() -> Boolean)?
    ) {
        if (hookFeatures.isEmpty() && recordExposure == null) return
        val decision = Decision(hookFeatures, context, recordExposure)
        synchronized(decisions) { decisions[metadata] = decision }
    }

    fun find(details: FlagEvaluationDetails<*>): Decision? = synchronized(decisions) { decisions[details.metadata] }

    /**
     * Makes the final exposure decision of a successful evaluation.
     * @return whether the exposure was already recorded, or null when [details] is not a Datadog exposure.
     */
    fun complete(details: FlagEvaluationDetails<*>): Boolean? =
        if (details.errorCode != null || details.reason == Reason.ERROR.name) null else find(details)?.complete()
}
