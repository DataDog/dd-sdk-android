/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.utils

import com.datadog.android.flags.model.BatchedFlagEvaluations
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class FlagEvaluationAssert(actual: BatchedFlagEvaluations.FlagEvaluation) :
    AbstractObjectAssert<FlagEvaluationAssert, BatchedFlagEvaluations.FlagEvaluation>(
        actual,
        FlagEvaluationAssert::class.java
    ) {

    fun hasFlagKey(expected: String): FlagEvaluationAssert {
        assertThat(actual.flag.key)
            .overridingErrorMessage(
                "Expected flag.key to be <%s> but was <%s>",
                expected,
                actual.flag.key
            )
            .isEqualTo(expected)
        return this
    }

    fun hasVariantKey(expected: String?): FlagEvaluationAssert {
        assertThat(actual.variant?.key)
            .overridingErrorMessage(
                "Expected variant.key to be <%s> but was <%s>",
                expected,
                actual.variant?.key
            )
            .isEqualTo(expected)
        return this
    }

    fun hasAllocationKey(expected: String?): FlagEvaluationAssert {
        assertThat(actual.allocation?.key)
            .overridingErrorMessage(
                "Expected allocation.key to be <%s> but was <%s>",
                expected,
                actual.allocation?.key
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTargetingKey(expected: String?): FlagEvaluationAssert {
        assertThat(actual.targetingKey)
            .overridingErrorMessage(
                "Expected targeting_key to be <%s> but was <%s>",
                expected,
                actual.targetingKey
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNoTargetingKey(): FlagEvaluationAssert {
        assertThat(actual.targetingKey)
            .overridingErrorMessage(
                "Expected targeting_key to be null but was <%s>",
                actual.targetingKey
            )
            .isNull()
        return this
    }

    fun hasEvaluationCount(expected: Long): FlagEvaluationAssert {
        assertThat(actual.evaluationCount)
            .overridingErrorMessage(
                "Expected evaluation_count to be <%s> but was <%s>",
                expected,
                actual.evaluationCount
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFirstEvaluation(expected: Long): FlagEvaluationAssert {
        assertThat(actual.firstEvaluation)
            .overridingErrorMessage(
                "Expected first_evaluation to be <%s> but was <%s>",
                expected,
                actual.firstEvaluation
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLastEvaluation(expected: Long): FlagEvaluationAssert {
        assertThat(actual.lastEvaluation)
            .overridingErrorMessage(
                "Expected last_evaluation to be <%s> but was <%s>",
                expected,
                actual.lastEvaluation
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFirstEvaluationBetween(min: Long, max: Long): FlagEvaluationAssert {
        assertThat(actual.firstEvaluation)
            .isBetween(min, max)
        return this
    }

    fun hasLastEvaluationBetween(min: Long, max: Long): FlagEvaluationAssert {
        assertThat(actual.lastEvaluation)
            .isBetween(min, max)
        return this
    }

    fun hasRuntimeDefaultUsed(expected: Boolean): FlagEvaluationAssert {
        assertThat(actual.runtimeDefaultUsed)
            .overridingErrorMessage(
                "Expected runtime_default_used to be <%s> but was <%s>",
                expected,
                actual.runtimeDefaultUsed
            )
            .isEqualTo(expected)
        return this
    }

    fun hasError(): FlagEvaluationAssert {
        assertThat(actual.error)
            .overridingErrorMessage("Expected error to be present but was null")
            .isNotNull
        return this
    }

    fun hasNoError(): FlagEvaluationAssert {
        assertThat(actual.error)
            .overridingErrorMessage(
                "Expected error to be null but was <%s>",
                actual.error
            )
            .isNull()
        return this
    }

    fun hasErrorMessage(expected: String): FlagEvaluationAssert {
        assertThat(actual.error?.message)
            .overridingErrorMessage(
                "Expected error.message to be <%s> but was <%s>",
                expected,
                actual.error?.message
            )
            .isEqualTo(expected)
        return this
    }

    fun hasContext(): FlagEvaluationAssert {
        assertThat(actual.context)
            .overridingErrorMessage("Expected context to be present but was null")
            .isNotNull
        return this
    }

    fun hasNoContext(): FlagEvaluationAssert {
        assertThat(actual.context)
            .overridingErrorMessage(
                "Expected context to be null but was <%s>",
                actual.context
            )
            .isNull()
        return this
    }

    fun timestampEqualsFirstEvaluation(): FlagEvaluationAssert {
        assertThat(actual.timestamp)
            .overridingErrorMessage(
                "Expected timestamp (<%s>) to equal first_evaluation (<%s>) per EVALLOG.10",
                actual.timestamp,
                actual.firstEvaluation
            )
            .isEqualTo(actual.firstEvaluation)
        return this
    }

    companion object {
        fun assertThat(actual: BatchedFlagEvaluations.FlagEvaluation): FlagEvaluationAssert =
            FlagEvaluationAssert(actual)
    }
}
