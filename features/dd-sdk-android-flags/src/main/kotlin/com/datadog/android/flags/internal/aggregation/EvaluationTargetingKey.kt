/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

/** A validated telemetry identity, computed before repeated evaluations. */
@ConsistentCopyVisibility
internal data class EvaluationTargetingKey private constructor(val value: String?) {
    companion object {
        val MISSING = EvaluationTargetingKey(null)

        fun create(value: String?, observeFullEvaluationData: Boolean): EvaluationTargetingKey =
            EvaluationTargetingKey(EvaluationPrivacy.targetingKey(value, observeFullEvaluationData))
    }
}

/** One entry per client for bridge replay identities that are independent of the installed state. */
internal class EvaluationTargetingKeyCache {
    @Volatile
    private var entry: Entry? = null

    fun get(value: String?, observeFullEvaluationData: Boolean): EvaluationTargetingKey {
        val current = entry
        if (current != null && current.source == value &&
            current.observeFullEvaluationData == observeFullEvaluationData
        ) {
            return current.targetingKey
        }
        val targetingKey = EvaluationTargetingKey.create(value, observeFullEvaluationData)
        entry = Entry(value, observeFullEvaluationData, targetingKey)
        return targetingKey
    }

    private data class Entry(
        val source: String?,
        val observeFullEvaluationData: Boolean,
        val targetingKey: EvaluationTargetingKey
    )
}
