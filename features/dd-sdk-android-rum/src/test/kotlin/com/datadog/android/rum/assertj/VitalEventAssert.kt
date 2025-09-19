/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.assertj

import com.datadog.android.rum.featureoperations.FailureReason
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.toSchemaFailureReason
import com.datadog.android.rum.internal.domain.scope.toVitalSessionPrecondition
import com.datadog.android.rum.model.VitalEvent
import com.datadog.android.rum.model.VitalEvent.VitalEventSessionType
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class VitalEventAssert(actual: VitalEvent) : AbstractObjectAssert<VitalEventAssert, VitalEvent>(
    actual,
    VitalEventAssert::class.java
) {

    fun hasDate(expected: Long) = apply {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event data to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
    }

    fun hasApplicationId(expected: String) = apply {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected event to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
    }

    fun containsExactlyContextAttributes(expected: Map<String, Any?>) = apply {
        assertThat(actual.context?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have context " +
                    "additional properties $expected " +
                    "but was ${actual.context?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected)
    }

    fun hasStartReason(reason: RumSessionScope.StartReason) = apply {
        assertThat(actual.dd.session?.sessionPrecondition)
            .overridingErrorMessage(
                "Expected event to have a session sessionPrecondition of ${reason.name} " +
                    "but was ${actual.dd.session?.sessionPrecondition}"
            )
            .isEqualTo(reason.toVitalSessionPrecondition())
    }

    fun hasSampleRate(sampleRate: Float?) = apply {
        assertThat(actual.dd.configuration?.sessionSampleRate ?: 0)
            .overridingErrorMessage(
                "Expected event to have sample rate: $sampleRate" +
                    " but instead was: ${actual.dd.configuration?.sessionSampleRate}"
            )
            .isEqualTo(sampleRate)
    }

    fun hasSessionId(expected: String) = apply {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected event to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
    }

    fun hasSessionType(expected: VitalEventSessionType) = apply {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:$expected but was ${actual.session.type}"
            ).isEqualTo(expected)
    }

    fun hasSessionReplay(hasReplay: Boolean) = apply {
        assertThat(actual.session.hasReplay)
            .overridingErrorMessage(
                "Expected event data to have hasReplay $hasReplay but was ${actual.session.hasReplay}"
            )
            .isEqualTo(hasReplay)
    }

    fun hasViewId(expectedId: String) = apply {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view.id}"
            )
            .isEqualTo(expectedId)
    }

    fun hasName(expected: String) = apply {
        assertThat(actual.view.name)
            .overridingErrorMessage(
                "Expected event data to have view.name $expected but was ${actual.view.name}"
            )
            .isEqualTo(expected)
    }

    fun hasUrl(expected: String) = apply {
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url $expected but was ${actual.view.url}"
            )
            .isEqualTo(expected)
    }

    fun hasVitalName(expected: String) = apply {
        assertThat(actual.vital.name)
            .overridingErrorMessage(
                "Expected event data to have vital.name $expected but was ${actual.vital.name}"
            )
            .isEqualTo(expected)
    }

    fun hasVitalOperationalKey(expected: String?) = apply {
        assertThat(actual.vital.operationKey)
            .overridingErrorMessage(
                "Expected event data to have vital.operationKey $expected but was ${actual.vital.operationKey}"
            )
            .isEqualTo(expected)
    }

    fun hasVitalStepType(expected: VitalEvent.StepType) = apply {
        assertThat(actual.vital.stepType)
            .overridingErrorMessage(
                "Expected event data to have vital.operationKey $expected but was ${actual.vital.stepType}"
            )
            .isEqualTo(expected)
    }

    fun hasVitalFailureReason(expected: FailureReason) = apply {
        assertThat(actual.vital.failureReason)
            .overridingErrorMessage(
                "Expected event data to have vital.failureReason $expected but was ${actual.vital.failureReason}"
            )
            .isEqualTo(expected.toSchemaFailureReason())
    }

    fun hasNoVitalFailureReason() = apply {
        assertThat(actual.vital.failureReason)
            .overridingErrorMessage(
                "Expected event data to have vital.failureReason to be null but was ${actual.vital.failureReason}"
            )
            .isNull()
    }

    fun hasVitalType(expected: VitalEvent.VitalEventVitalType) = apply {
        assertThat(actual.vital.type)
            .overridingErrorMessage(
                "Expected event data to have vital.type $expected but was ${actual.vital.type}"
            )
            .isEqualTo(expected)
    }

    fun hasNoSyntheticsTest() = apply {
        assertThat(actual.synthetics?.testId)
            .overridingErrorMessage(
                "Expected event to have no synthetics.testId but was ${actual.synthetics?.testId}"
            ).isNull()
        assertThat(actual.synthetics?.resultId)
            .overridingErrorMessage(
                "Expected event to have no synthetics.resultId but was ${actual.synthetics?.resultId}"
            ).isNull()
    }

    fun hasSyntheticsTest(testId: String, resultId: String) = apply {
        assertThat(actual.synthetics?.testId)
            .overridingErrorMessage(
                "Expected event to have synthetics.testId $testId but was ${actual.synthetics?.testId}"
            ).isEqualTo(testId)
        assertThat(actual.synthetics?.resultId)
            .overridingErrorMessage(
                "Expected event to have synthetics.resultId $resultId but was ${actual.synthetics?.resultId}"
            ).isEqualTo(resultId)
    }

    companion object {
        internal fun assertThat(actual: VitalEvent): VitalEventAssert = VitalEventAssert(actual)
    }
}
