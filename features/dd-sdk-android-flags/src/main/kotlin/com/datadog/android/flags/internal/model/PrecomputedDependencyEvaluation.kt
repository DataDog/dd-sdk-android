/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

internal sealed interface PrecomputedDependencyEvaluation {
    val flagKey: String

    data class Success(
        override val flagKey: String,
        val isPresentInAssignment: Boolean
    ) : PrecomputedDependencyEvaluation

    data class Error(
        override val flagKey: String,
        val errorCode: String,
        val errorMessage: String? = null
    ) : PrecomputedDependencyEvaluation
}

internal data class PrecomputedFlagEvaluationError(
    val errorCode: String,
    val errorMessage: String? = null,
    val dependencyEvaluations: List<PrecomputedDependencyEvaluation> = emptyList()
)

internal data class ResolvedDependencyEvaluation(
    val flagKey: String,
    val flag: PrecomputedFlag? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val isPresentInAssignment: Boolean? = null
)
