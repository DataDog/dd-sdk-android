/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.assertj

import com.datadog.android.flags.internal.model.FlagEvaluation
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class FlagEvaluationAssert(actual: FlagEvaluation) :
    AbstractObjectAssert<FlagEvaluationAssert, FlagEvaluation>(actual, FlagEvaluationAssert::class.java) {

    fun hasRumApplicationId(expected: String?): FlagEvaluationAssert {
        val applicationId = actual.context?.dd?.rum?.application?.id
        assertThat(applicationId)
            .overridingErrorMessage("Expected RUM application ID to be $expected but was $applicationId")
            .isEqualTo(expected)
        return this
    }

    fun hasRumViewName(expected: String?): FlagEvaluationAssert {
        val viewName = actual.context?.dd?.rum?.view?.url
        assertThat(viewName)
            .overridingErrorMessage("Expected RUM view name to be $expected but was $viewName")
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThatFlagEvaluation(actual: FlagEvaluation): FlagEvaluationAssert = FlagEvaluationAssert(actual)
    }
}
