/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.internal.flags.RumFlagEvaluationMessage
import com.datadog.android.internal.flags.RumFlagExposureMessage
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface RumExposureLogger {
    fun logExposure(
        flagKey: String,
        value: Any,
        assignment: PrecomputedFlag,
        evaluationContext: EvaluationContext
    )
}

internal class DefaultRumExposureLogger(
    private val featureScope: FeatureScope
) : RumExposureLogger {
    override fun logExposure(
        flagKey: String,
        value: Any,
        assignment: PrecomputedFlag,
        evaluationContext: EvaluationContext
    ) {
        featureScope.sendEvent(
            RumFlagEvaluationMessage(
                flagKey = flagKey,
                value = value
            )
        )

        featureScope.withContext { context ->
            val timestampMs = System.currentTimeMillis() + context.time.serverTimeOffsetMs

            val exposureKey = "$flagKey-${assignment.allocationKey}"

            featureScope.sendEvent(
                RumFlagExposureMessage(
                    timestamp = timestampMs,
                    flagKey = flagKey,
                    allocationKey = assignment.allocationKey,
                    exposureKey = exposureKey,
                    subjectKey = evaluationContext.targetingKey,
                    variantKey = assignment.variationKey,
                    subjectAttributes = evaluationContext.attributes
                )
            )
        }
    }
}
