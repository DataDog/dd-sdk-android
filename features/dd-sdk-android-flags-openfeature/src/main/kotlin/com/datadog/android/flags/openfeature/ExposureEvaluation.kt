/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import dev.openfeature.kotlin.sdk.EvaluationContext

/**
 * A completed flag evaluation and its Datadog exposure decision, passed to an [ExposureCallback].
 *
 * @property flagKey the flag key.
 * @property variant the selected variant key, or null when the default value was returned.
 * @property allocationKey the key of the selected allocation, or null when none was selected.
 * @property targetingKey the targeting key of [evaluationContext], or null if it has none.
 * @property evaluationContext for an exposure or a split with features, a copy of the context that the evaluated
 * assignment was fetched for: it identifies the exposed subject, and its attribute values are strings, as sent to
 * Datadog. Otherwise a copy of the OpenFeature evaluation context, or null if none was set.
 * @property features the `HOOK` features of the selected split, keyed by feature key, for example
 * `holdout.key`. Each value is a [String], [Double] or [Boolean]. Empty when the split has none. Each callback
 * gets its own copy.
 * @property isExposure true when the evaluation succeeded and selected an allocation that logs exposures,
 * including repeats for this subject.
 */
class ExposureEvaluation internal constructor(
    val flagKey: String,
    val variant: String?,
    val allocationKey: String?,
    val targetingKey: String?,
    val evaluationContext: EvaluationContext?,
    val features: Map<String, Any>,
    val isExposure: Boolean,
    private val cacheHit: Boolean?
) {
    /**
     * True when this exposure repeats one already recorded for this subject, with the same allocation, variant
     * and split. True requires [isExposure].
     */
    val isCacheHit: Boolean
        get() = isExposure && cacheHit == true

    /** True for a new or changed exposure. The SDK sends it to Datadog when exposure tracking is on. */
    fun shouldSend(): Boolean = isExposure && cacheHit == false
}
