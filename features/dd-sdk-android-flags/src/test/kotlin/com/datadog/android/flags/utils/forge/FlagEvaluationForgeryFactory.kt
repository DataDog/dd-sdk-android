/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.model.BatchedFlagEvaluations
import com.datadog.android.flags.model.ResolutionReason
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class FlagEvaluationForgeryFactory : ForgeryFactory<BatchedFlagEvaluations.FlagEvaluation> {
    override fun getForgery(forge: Forge): BatchedFlagEvaluations.FlagEvaluation {
        val reason = forge.aValueFrom(ResolutionReason::class.java)
        val isDefaultOrError = reason == ResolutionReason.DEFAULT || reason == ResolutionReason.ERROR
        val hasError = forge.aBool() && (reason == ResolutionReason.ERROR || forge.aBool())
        val firstEvalTimestamp = forge.aPositiveLong()
        val lastEvalTimestamp = firstEvalTimestamp + forge.aLong(min = 0, max = 10_000)

        return BatchedFlagEvaluations.FlagEvaluation(
            timestamp = firstEvalTimestamp,
            flag = BatchedFlagEvaluations.Identifier(forge.anAlphabeticalString()),
            variant = if (!isDefaultOrError) {
                BatchedFlagEvaluations.Identifier(forge.anAlphabeticalString())
            } else {
                null
            },
            allocation = if (!isDefaultOrError) {
                BatchedFlagEvaluations.Identifier(forge.anAlphabeticalString())
            } else {
                null
            },
            targetingRule = null, // Not currently tracked
            targetingKey = forge.aNullable { forge.anAlphabeticalString() },
            context = null,
            error = if (hasError) {
                BatchedFlagEvaluations.Error(message = forge.anAlphabeticalString())
            } else {
                null
            },
            evaluationCount = forge.aLong(min = 1, max = 10_000),
            firstEvaluation = firstEvalTimestamp,
            lastEvaluation = lastEvalTimestamp,
            runtimeDefaultUsed = isDefaultOrError
        )
    }
}
