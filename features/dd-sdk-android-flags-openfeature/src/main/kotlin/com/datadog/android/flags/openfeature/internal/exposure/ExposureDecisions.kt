/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.exposure

import com.datadog.android.flags._FlagsInternalProxy
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
internal class ExposureDecisions(
    // A decision lives as long as its evaluation's metadata is reachable, which spans all hooks of the evaluation.
    // It must not reference that metadata: the map would then never release the entry.
    private val decisions: MutableMap<EvaluationMetadata, Decision> = WeakHashMap()
) {

    /**
     * @param features the `HOOK` features of the selected split. The decision keeps its own copy.
     * @param context the context of the assignment that was evaluated.
     * @param recordExposure records the exposure and returns whether it was already recorded, or null when the
     * evaluation turned stale. It is null for a non-exposure.
     * @param isExposure true when the root evaluation is an exposure.
     * @param dependencyExposures selected prerequisite decisions, in evaluation order.
     */
    internal class Decision(
        features: Map<String, Any>,
        val context: EvaluationContext?,
        private val recordExposure: (() -> Boolean?)?,
        val isExposure: Boolean = recordExposure != null,
        dependencyExposures: List<_FlagsInternalProxy.DeferredDependencyExposure> = emptyList()
    ) {
        val hookFeatures: Map<String, Any> = features.toMap()
        val dependencies: List<DependencyDecision> = dependencyExposures.map { DependencyDecision(it) }

        private var isCompleted = false
        private var isRecorded: Boolean? = null

        /**
         * Records the exposure on the first call.
         * @return whether it was already recorded, or null for a non-exposure or a stale evaluation.
         */
        @Synchronized
        fun complete(): Boolean? {
            val record = recordExposure ?: return null
            if (!isCompleted) {
                isRecorded = record()
                isCompleted = true
            }
            return isRecorded
        }

        @Synchronized
        fun completeAll(): Completion {
            val completedDependencies = dependencies.map { it.complete() }
            return Completion(complete(), completedDependencies)
        }
    }

    internal class DependencyDecision(exposure: _FlagsInternalProxy.DeferredDependencyExposure) {
        val flagKey: String = exposure.flagKey
        val variantKey: String? = exposure.variantKey
        val allocationKey: String? = exposure.allocationKey
        val hookFeatures: Map<String, Any> = exposure.hookFeatures.toMap()
        val isExposure: Boolean = exposure.isExposure
        private val recordExposure = exposure.recordExposure
        private var isCompleted = false
        private var isRecorded: Boolean? = null

        @Synchronized
        fun complete(): CompletedDependency {
            if (!isCompleted) {
                isRecorded = recordExposure?.invoke()
                isCompleted = true
            }
            return CompletedDependency(this, isRecorded)
        }
    }

    internal data class CompletedDependency(val decision: DependencyDecision, val cacheHit: Boolean?)

    internal data class Completion(
        val rootCacheHit: Boolean?,
        val dependencies: List<CompletedDependency>
    )

    fun register(
        metadata: EvaluationMetadata,
        hookFeatures: Map<String, Any>,
        context: EvaluationContext?,
        recordExposure: (() -> Boolean?)?
    ) = registerWithDependencies(metadata, hookFeatures, context, recordExposure, recordExposure != null, emptyList())

    fun registerWithDependencies(
        metadata: EvaluationMetadata,
        hookFeatures: Map<String, Any>,
        context: EvaluationContext?,
        recordExposure: (() -> Boolean?)?,
        isExposure: Boolean,
        dependencyExposures: List<_FlagsInternalProxy.DeferredDependencyExposure>
    ) {
        if (hookFeatures.isEmpty() && recordExposure == null && dependencyExposures.isEmpty()) return
        val decision = Decision(hookFeatures, context, recordExposure, isExposure, dependencyExposures)
        synchronized(decisions) { decisions[metadata] = decision }
    }

    fun find(details: FlagEvaluationDetails<*>): Decision? = synchronized(decisions) { decisions[details.metadata] }

    /**
     * Makes the final exposure decision of a successful evaluation.
     * @return whether the exposure was already recorded, or null when [details] is not a Datadog exposure.
     */
    fun complete(details: FlagEvaluationDetails<*>): Boolean? =
        if (details.errorCode != null || details.reason == Reason.ERROR.name) null else find(details)?.complete()

    fun completeAll(details: FlagEvaluationDetails<*>): Completion? =
        if (details.errorCode != null || details.reason == Reason.ERROR.name) null else find(details)?.completeAll()
}
