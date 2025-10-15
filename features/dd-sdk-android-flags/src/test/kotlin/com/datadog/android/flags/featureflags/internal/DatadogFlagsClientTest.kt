/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.featureflags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.model.VariationType
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DatadogFlagsClientTest {

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlagsRepository: FlagsRepository

    @Mock
    lateinit var mockEvaluationsManager: EvaluationsManager

    @Mock
    lateinit var mockRumEvaluationLogger: RumEvaluationLogger

    private lateinit var testedClient: DatadogFlagsClient

    @StringForgery
    lateinit var fakeDefaultValue: String

    @StringForgery
    lateinit var fakeJsonKey: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockFeatureSdkCore.getFeature(RUM_FEATURE_NAME)) doReturn mock()

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = true,
                rumIntegrationEnabled = true
            ),
            rumEvaluationLogger = mockRumEvaluationLogger
        )
    }

    // region resolveBooleanValue()

    @Test
    fun `M return flag value W resolveBooleanValue() { flag exists with string boolean value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W resolveBooleanValue() { flag exists with invalid boolean string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "not-a-boolean"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveBooleanValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M log RUM evaluation W resolveBooleanValue() { flag exists with valid boolean and context exists }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString()
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeEvaluationContext

        // When
        testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlagValue
        )
    }

    @Test
    fun `M not log RUM evaluation W resolveBooleanValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeEvaluationContext

        // When
        testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockRumEvaluationLogger, never()).logEvaluation(any(), any())
    }

    @Test
    fun `M not log RUM evaluation W resolveBooleanValue() { flag has invalid boolean value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "not-a-boolean"
        )
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeEvaluationContext

        // When
        testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockRumEvaluationLogger, never()).logEvaluation(any(), any())
    }

    @Test
    fun `M log RUM evaluation W resolveBooleanValue() { evaluation context is null }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn null

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        // RUM evaluation should still be logged even without evaluation context
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlagValue
        )
    }

    // endregion

    // region resolveStringValue()

    @Test
    fun `M return flag value W resolveStringValue() { flag exists with string value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = fakeFlagValue
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W resolveStringValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveIntValue()

    @Test
    fun `M return flag value W resolveIntValue() { flag exists with string integer value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anInt()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W resolveIntValue() { flag exists with invalid integer string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = "not-an-integer"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveIntValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveDoubleValue()

    @Test
    fun `M return flag value W resolveDoubleValue() { flag exists with string double value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aDouble()
        val fakeDefaultValue = forge.aDouble()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.DOUBLE.value,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W resolveDoubleValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveStructureValue()

    @Test
    fun `M return flag value W resolveStructureValue() { flag exists with valid JSON string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }
        val fakeFlagValue = JSONObject().apply {
            put("key1", forge.anAlphabeticalString())
            put("key2", forge.anInt())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.JSON.value,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.toString()).isEqualTo(fakeFlagValue.toString())
    }

    @Test
    fun `M return default value and log error W resolveStructureValue() { flag exists with invalid JSON }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.JSON.value,
            variationValue = "invalid json {"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.USER),
            any(),
            any<JSONException>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M log specific error message W resolveStructureValue() { flag exists with malformed JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.JSON.value,
            variationValue = "{\"unclosed\": \"quote"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)

        // Verify the specific error message
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.USER),
                capture(),
                any<JSONException>(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue()).isEqualTo("Failed to parse JSON for key: $fakeFlagKey")
        }
    }

    @Test
    fun `M log error W resolveStructureValue() { flag exists with completely invalid JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.JSON.value,
            variationValue = "not json at all!"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.USER),
            any(),
            any<JSONException>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M return default value W resolveStructureValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region Constructor Logic

    @Test
    fun `M not create DefaultFlagsRepository W constructor { custom repository provided }`(forge: Forge) {
        // Given
        val customRepository = mockFlagsRepository
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationValue = fakeFlagValue
        )
        whenever(customRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = customRepository,
            flagsConfiguration = forge.getForgery(),
            rumEvaluationLogger = mockRumEvaluationLogger
        )

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then

        assertThat(result).isEqualTo(fakeFlagValue)

        verify(customRepository).getPrecomputedFlag(fakeFlagKey)
    }

    // endregion

    // region setEvaluationContext()

    @Test
    fun `M call evaluations manager W setEvaluationContext() { valid targeting key and attributes }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "plan" to forge.anElementFrom("free", "premium", "enterprise"),
            "region" to forge.anElementFrom("us-east-1", "eu-west-1"),
            "user_id" to forge.anInt().toString(),
            "is_beta" to forge.aBool().toString()
        )
        val fakeContext = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockEvaluationsManager).updateEvaluationsForContext(contextCaptor.capture())

        val capturedContext = contextCaptor.firstValue
        assertThat(capturedContext.targetingKey).isEqualTo(fakeTargetingKey)
        assertThat(capturedContext.attributes).hasSize(4)
        assertThat(capturedContext.attributes).containsOnlyKeys("plan", "region", "user_id", "is_beta")
        assertThat(capturedContext.attributes["user_id"]).isEqualTo(fakeAttributes["user_id"])
        assertThat(capturedContext.attributes["is_beta"]).isEqualTo(fakeAttributes["is_beta"])
    }

    @Test
    fun `M not call evaluations manager W setEvaluationContext() { blank targeting key }`() {
        // Given
        val blankTargetingKey = ""
        val fakeAttributes = mapOf("test" to "value")

        // When
        testedClient.setEvaluationContext(EvaluationContext(blankTargetingKey, fakeAttributes))

        // Then
        verify(mockEvaluationsManager, never()).updateEvaluationsForContext(any())
    }

    @Test
    fun `M log error and not crash W setEvaluationContext() { blank targeting key }`() {
        // Given
        val blankTargetingKey = ""
        val fakeAttributes = mapOf("test" to "value")

        // When
        testedClient.setEvaluationContext(EvaluationContext(blankTargetingKey, fakeAttributes))

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue()
            assertThat(message).contains("Invalid evaluation context")
            assertThat(message).contains("targeting key cannot be blank")
        }
    }

    @Test
    fun `M process context and store flags W setEvaluationContext() { complete flow }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "user.plan" to forge.anElementFrom("free", "premium", "enterprise"),
            "feature.enabled" to forge.aBool().toString()
        )
        val fakeContext = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        // Verify that the evaluations manager was called to process the context
        verify(mockEvaluationsManager).updateEvaluationsForContext(fakeContext)
    }

    // endregion

    // region exposure logging configuration

    @Test
    fun `M not write exposure event W resolveBooleanValue() { trackExposures is false }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeContext
        whenever(mockFeatureSdkCore.getFeature(any())) doReturn null

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = false,
                rumIntegrationEnabled = false
            ),
            rumEvaluationLogger = mockRumEvaluationLogger
        )

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        verify(mockFeatureSdkCore, never()).getFeature(any())
    }

    @Test
    fun `M write exposure event W resolveBooleanValue() { trackExposures is true }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeContext
        whenever(mockFeatureSdkCore.getFeature(any())) doReturn null

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        // Verify that getFeature was called to attempt writing exposure event
        verify(mockFeatureSdkCore).getFeature(any())
    }

    // endregion

    // region rumIntegrationEnabled flag

    @Test
    fun `M log RUM evaluation W resolveBooleanValue() { rumIntegrationEnabled is true }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeContext

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = eq(fakeFlagKey),
            value = eq(fakeFlagValue)
        )
    }

    @Test
    fun `M not log RUM evaluation W resolveBooleanValue() { rumIntegrationEnabled is false }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag
        whenever(mockFlagsRepository.getEvaluationContext()) doReturn fakeContext

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = true,
                rumIntegrationEnabled = false
            ),
            rumEvaluationLogger = mockRumEvaluationLogger
        )

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    // endregion
}
