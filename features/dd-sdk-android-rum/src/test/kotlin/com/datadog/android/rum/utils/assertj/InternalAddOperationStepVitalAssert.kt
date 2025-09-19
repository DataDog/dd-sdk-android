/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.assertj

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class InternalAddOperationStepVitalAssert(actual: InternalTelemetryEvent.ApiUsage.AddOperationStepVital) :
    AbstractAssert<InternalAddOperationStepVitalAssert, InternalTelemetryEvent.ApiUsage.AddOperationStepVital>(
        actual,
        InternalAddOperationStepVitalAssert::class.java
    ) {

    fun isEqualTo(expected: InternalTelemetryEvent.ApiUsage.AddOperationStepVital) {
        hasActionType(expected.actionType)
    }

    private fun hasActionType(
        expected: InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType
    ): InternalAddOperationStepVitalAssert {
        assertThat(actual.actionType)
            .overridingErrorMessage(
                "Expected operationStepVital event to" +
                    " have actionType $expected but was ${actual.actionType}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThat(actual: InternalTelemetryEvent.ApiUsage.AddOperationStepVital) =
            InternalAddOperationStepVitalAssert(actual)
    }
}
