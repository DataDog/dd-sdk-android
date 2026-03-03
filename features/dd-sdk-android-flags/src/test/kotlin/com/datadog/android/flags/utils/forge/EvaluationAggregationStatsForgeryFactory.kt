/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.internal.aggregation.EvaluationAggregationKey
import com.datadog.android.flags.internal.aggregation.EvaluationAggregationStats
import com.datadog.android.flags.model.ResolutionReason
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class EvaluationAggregationStatsForgeryFactory : ForgeryFactory<EvaluationAggregationStats> {
    override fun getForgery(forge: Forge): EvaluationAggregationStats {
        val reason = forge.aValueFrom(ResolutionReason::class.java)
        val isDefaultOrError = reason == ResolutionReason.DEFAULT || reason == ResolutionReason.ERROR
        val hasError = forge.aBool() && (reason == ResolutionReason.ERROR || forge.aBool())
        val firstEvalTimestamp = forge.aPositiveLong()
        val lastEvalTimestamp = firstEvalTimestamp + forge.aLong(min = 0, max = 10_000)

        return EvaluationAggregationStats(
            context = forge.getForgery(),
            aggregationKey = EvaluationAggregationKey(
                flagKey = forge.anAlphabeticalString(),
                variantKey = if (!isDefaultOrError) forge.anAlphabeticalString() else null,
                allocationKey = if (!isDefaultOrError) forge.anAlphabeticalString() else null,
                targetingKey = forge.aNullable { anAlphabeticalString() },
                viewName = forge.aNullable { anAlphabeticalString() },
                errorCode = if (hasError) forge.anAlphabeticalString() else null
            ),
            errorMessage = if (hasError) forge.anAlphabeticalString() else null,
            count = forge.anInt(min = 1, max = 10_000),
            firstEvaluation = firstEvalTimestamp,
            lastEvaluation = lastEvalTimestamp,
            rumApplicationId = forge.aNullable { getForgery<UUID>().toString() }
        )
    }
}
