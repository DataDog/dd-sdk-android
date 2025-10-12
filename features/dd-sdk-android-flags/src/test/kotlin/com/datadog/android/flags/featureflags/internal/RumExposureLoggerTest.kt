/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.flags.RumFlagEvaluationMessage
import com.datadog.android.internal.flags.RumFlagExposureMessage
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RumExposureLoggerTest {

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockTimeInfo: TimeInfo

    private lateinit var testedLogger: DefaultRumExposureLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockDatadogContext.time) doReturn mockTimeInfo
        whenever(mockTimeInfo.serverTimeOffsetMs) doReturn 0L
        whenever(mockFeatureScope.withContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext) -> Unit>(1)
            callback.invoke(mockDatadogContext)
        }

        testedLogger = DefaultRumExposureLogger(
            featureScope = mockFeatureScope
        )
    }

    // region logExposure

    @Test
    fun `M send RumFlagEvaluationMessage W logExposure()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAsciiString()
        val fakePrecomputedFlag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = JSONObject(),
            reason = forge.anAlphabeticalString()
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedLogger.logExposure(
            flagKey = fakeFlagKey,
            value = fakeValue,
            assignment = fakePrecomputedFlag,
            evaluationContext = fakeEvaluationContext
        )

        // Then
        argumentCaptor<RumFlagEvaluationMessage> {
            verify(mockFeatureScope).sendEvent(capture())
            assertThat(lastValue.flagKey).isEqualTo(fakeFlagKey)
            assertThat(lastValue.value).isEqualTo(fakeValue)
        }
    }

    @Test
    fun `M send RumFlagExposureMessage W logExposure()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAsciiString()
        val fakeAllocationKey = forge.anAlphabeticalString()
        val fakeVariationKey = forge.anAlphabeticalString()
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(forge.anAlphabeticalString() to forge.anAsciiString())
        val fakePrecomputedFlag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey,
            extraLogging = JSONObject(),
            reason = forge.anAlphabeticalString()
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = fakeAttributes
        )

        // When
        testedLogger.logExposure(
            flagKey = fakeFlagKey,
            value = fakeValue,
            assignment = fakePrecomputedFlag,
            evaluationContext = fakeEvaluationContext
        )

        // Then
        argumentCaptor<RumFlagExposureMessage> {
            verify(mockFeatureScope).sendEvent(capture())
            val capturedMessage = allValues.filterIsInstance<RumFlagExposureMessage>().first()

            assertThat(capturedMessage.flagKey).isEqualTo(fakeFlagKey)
            assertThat(capturedMessage.allocationKey).isEqualTo(fakeAllocationKey)
            assertThat(capturedMessage.exposureKey).isEqualTo("$fakeFlagKey-$fakeAllocationKey")
            assertThat(capturedMessage.subjectKey).isEqualTo(fakeTargetingKey)
            assertThat(capturedMessage.variantKey).isEqualTo(fakeVariationKey)
            assertThat(capturedMessage.subjectAttributes).isEqualTo(fakeAttributes)
        }
    }

    @Test
    fun `M calculate timestamp correctly W logExposure()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAsciiString()
        val fakeServerOffset = forge.aLong(min = -10000, max = 10000)
        val fakePrecomputedFlag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = JSONObject(),
            reason = forge.anAlphabeticalString()
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockTimeInfo.serverTimeOffsetMs) doReturn fakeServerOffset

        // When
        val callTimeMs = System.currentTimeMillis()
        testedLogger.logExposure(
            flagKey = fakeFlagKey,
            value = fakeValue,
            assignment = fakePrecomputedFlag,
            evaluationContext = fakeEvaluationContext
        )

        // Then
        argumentCaptor<RumFlagExposureMessage> {
            verify(mockFeatureScope).sendEvent(capture())
            val capturedMessage = allValues.filterIsInstance<RumFlagExposureMessage>().first()

            // Timestamp should be approximately currentTime + serverOffset
            // We allow some tolerance for test execution time
            assertThat(capturedMessage.timestamp).isBetween(
                callTimeMs + fakeServerOffset - 1000,
                System.currentTimeMillis() + fakeServerOffset + 1000
            )
        }
    }

    @Test
    fun `M send both messages W logExposure()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAsciiString()
        val fakePrecomputedFlag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = JSONObject(),
            reason = forge.anAlphabeticalString()
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedLogger.logExposure(
            flagKey = fakeFlagKey,
            value = fakeValue,
            assignment = fakePrecomputedFlag,
            evaluationContext = fakeEvaluationContext
        )

        // Then
        argumentCaptor<Any> {
            verify(mockFeatureScope, times(2)).sendEvent(capture())

            assertThat(allValues).hasSize(2)
            assertThat(allValues.filterIsInstance<RumFlagEvaluationMessage>()).hasSize(1)
            assertThat(allValues.filterIsInstance<RumFlagExposureMessage>()).hasSize(1)
        }
    }

    @Test
    fun `M format exposureKey correctly W logExposure()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAsciiString()
        val fakeAllocationKey = forge.anAlphabeticalString()
        val fakeFlag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = fakeAllocationKey,
            variationKey = forge.anAlphabeticalString(),
            extraLogging = JSONObject(),
            reason = forge.anAlphabeticalString()
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedLogger.logExposure(
            flagKey = fakeFlagKey,
            value = fakeValue,
            assignment = fakeFlag,
            evaluationContext = fakeEvaluationContext
        )

        // Then
        argumentCaptor<RumFlagExposureMessage> {
            verify(mockFeatureScope).sendEvent(capture())
            val capturedMessage = allValues.filterIsInstance<RumFlagExposureMessage>().first()

            assertThat(capturedMessage.exposureKey).isEqualTo("$fakeFlagKey-$fakeAllocationKey")
        }
    }

    @Test
    fun `M use context callback W logExposure()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAsciiString()
        val fakePrecomputedFlag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = JSONObject(),
            reason = forge.anAlphabeticalString()
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When
        testedLogger.logExposure(
            flagKey = fakeFlagKey,
            value = fakeValue,
            assignment = fakePrecomputedFlag,
            evaluationContext = fakeEvaluationContext
        )

        // Then
        verify(mockFeatureScope).withContext(eq(emptySet()), any())
    }

    // endregion
}
