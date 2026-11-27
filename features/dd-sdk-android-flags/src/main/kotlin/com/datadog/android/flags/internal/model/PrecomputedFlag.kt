/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import com.datadog.android.flags.model.UnparsedFlagWithConsent
import com.datadog.android.flags.model.UnparsedFlagWithFeatures
import org.json.JSONObject

internal data class PrecomputedFlag(
    override val variationType: String,
    override val variationValue: String,
    override val doLog: Boolean,
    override val allocationKey: String,
    override val variationKey: String,
    override val extraLogging: JSONObject,
    override val reason: String,
    override val serialId: Long? = null,
    override val observeFullEvaluationData: Boolean = false,
    override val features: List<SplitFeature> = emptyList()
) : UnparsedFlagWithConsent, UnparsedFlagWithFeatures

internal fun PrecomputedFlag.metadata(observeFullEvaluationData: Boolean): Map<String, Any> {
    val metadata = mutableMapOf<String, Any>()
    extraLogging.keys().forEach { key ->
        val value = extraLogging.opt(key)
        when (value) {
            is String, is Number, is Boolean -> if (!key.startsWith(RESERVED_METADATA_PREFIX)) metadata[key] = value
        }
    }
    if (allocationKey.isNotBlank()) {
        metadata["allocationKey"] = allocationKey
    }
    metadata[OBSERVE_FULL_EVALUATION_DATA_METADATA_KEY] = observeFullEvaluationData
    features.forEach { feature ->
        if (SplitFeature.DESTINATION_HOOK in feature.destinations) {
            metadata[HOOK_FEATURE_METADATA_PREFIX + feature.key] = feature.value.scalar
        }
    }
    return metadata
}

internal const val OBSERVE_FULL_EVALUATION_DATA_METADATA_KEY = "__dd_observe_full_evaluation_data"
private const val RESERVED_METADATA_PREFIX = "__dd_"
private const val HOOK_FEATURE_METADATA_PREFIX = "__dd_feature."
