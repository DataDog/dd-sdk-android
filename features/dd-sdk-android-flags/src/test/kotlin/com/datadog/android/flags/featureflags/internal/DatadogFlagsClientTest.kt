/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlagConstants
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.featureflags.model.EvaluationDetails
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogFlagsClientTest {

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlagsRepository: FlagsRepository

    @Mock
    lateinit var mockFlagsContext: FlagsContext

    private lateinit var testedClient: DatadogFlagsClient

    @BeforeEach
    fun `set up`(forge: Forge) {
        ForgeConfigurator.configure(forge)
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger

        testedClient = DatadogFlagsClient(
            executorService = mockExecutorService,
            featureSdkCore = mockFeatureSdkCore,
            flagsContext = mockFlagsContext,
            flagsRepository = mockFlagsRepository
        )
    }

    // region getBooleanValue()

    @Test
    fun `M return flag value W getBooleanValue() { flag exists with string boolean value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aBool()
        val fakeDefaultValue = !fakeFlagValue
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.BOOLEAN,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W getBooleanValue() { flag exists with invalid boolean string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.BOOLEAN,
            variationValue = "not-a-boolean"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getBooleanValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getStringValue()

    @Test
    fun `M return flag value W getStringValue() { flag exists with string value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.STRING,
            variationValue = fakeFlagValue
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W getStringValue() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getIntValue()

    @Test
    fun `M return flag value W getIntValue() { flag exists with string integer value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anInt()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.INTEGER,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W getIntValue() { flag exists with invalid integer string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.INTEGER,
            variationValue = "not-an-integer"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getIntValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getNumberValue()

    @Test
    fun `M return flag value W getNumberValue() { flag exists with string double value }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aDouble()
        val fakeDefaultValue = forge.aDouble()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.DOUBLE,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W getNumberValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getStructureValue()

    @Test
    fun `M return flag value W getStructureValue() { flag exists with valid JSON string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", forge.anAlphabeticalString())
        }
        val fakeFlagValue = JSONObject().apply {
            put("key1", forge.anAlphabeticalString())
            put("key2", forge.anInt())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.JSON,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.toString()).isEqualTo(fakeFlagValue.toString())
    }

    @Test
    fun `M return default value and log error W getStructureValue() { flag exists with invalid JSON }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.JSON,
            variationValue = "invalid json {"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            any<JSONException>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M log specific error message W getStructureValue() { flag exists with malformed JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.JSON,
            variationValue = "{\"unclosed\": \"quote"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)

        // Verify the specific error message
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.MAINTAINER),
                capture(),
                any<JSONException>(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue()).isEqualTo("Failed to parse JSON for key: $fakeFlagKey")
        }
    }

    @Test
    fun `M log error W getStructureValue() { flag exists with completely invalid JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.JSON,
            variationValue = "not json at all!"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            any<JSONException>(),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M return default value W getStructureValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", forge.anAlphabeticalString())
        }
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getStructureValue(fakeFlagKey, fakeDefaultValue)

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

        // When
        val provider = DatadogFlagsClient(
            executorService = mockExecutorService,
            featureSdkCore = mockFeatureSdkCore,
            flagsContext = mockFlagsContext,
            flagsRepository = customRepository
        )

        // Then
        val result = provider.getStringValue(fakeFlagKey, "default")
        assertThat(result).isEqualTo(fakeFlagValue)

        verify(customRepository).getPrecomputedFlag(fakeFlagKey)
    }

    // endregion

    // region setEvaluationContext()

    @Test
    fun `M create evaluation context W setEvaluationContext() { valid attributes }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "plan" to forge.anElementFrom("free", "premium", "enterprise"),
            "region" to forge.anElementFrom("us-east-1", "eu-west-1"),
            "user_id" to forge.anInt(),
            "is_beta" to forge.aBool(),
            "score" to forge.aDouble()
        )

        // When
        testedClient.setEvaluationContext(EvaluationContext(fakeTargetingKey, fakeAttributes))

        // Then
        // Since orchestrator runs async, we can't directly verify the EvaluationContext
        // But we can verify that the method completes without throwing
        // Integration tests would verify the full flow
    }

    @Test
    fun `M filter unsupported attribute types W setEvaluationContext() { mixed valid and invalid types }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val validAttributes = mapOf(
            "string_attr" to forge.anAlphabeticalString(),
            "number_attr" to forge.anInt(),
            "boolean_attr" to forge.aBool()
        )
        val invalidAttributes = mapOf(
            "invalid_list" to listOf(1, 2, 3),
            "invalid_array" to arrayOf("a", "b"),
            "invalid_map" to mapOf("not" to "supported") // Maps no longer supported
        )
        val allAttributes = validAttributes + invalidAttributes

        // When
        val evaluationContext = EvaluationContext.builder(fakeTargetingKey, mockInternalLogger)
            .addAll(allAttributes)
            .build()
        testedClient.setEvaluationContext(evaluationContext)

        // Then
        // addAll() now calls addAttribute() which filters, so warnings should be logged
        verify(mockInternalLogger, times(3)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M handle empty attributes W setEvaluationContext() { empty map }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val emptyAttributes = emptyMap<String, Any>()

        // When
        testedClient.setEvaluationContext(EvaluationContext(fakeTargetingKey, emptyAttributes))

        // Then
        // Should complete without errors and not log any warnings
        verify(mockInternalLogger, times(0)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M preserve targeting key exactly W setEvaluationContext() { special characters }`() {
        // Given
        val specialTargetingKey = "user@domain.com-123_test.key"
        val fakeAttributes = mapOf("test" to "value")

        // When
        testedClient.setEvaluationContext(EvaluationContext(specialTargetingKey, fakeAttributes))

        // Then
        // Method should complete without throwing for special characters
        // The targeting key preservation is tested in EvaluationContext tests
    }

    @Test
    fun `M handle filtered attributes W setEvaluationContext() { valid attributes only }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val validAttributes = mapOf(
            "valid_string" to forge.anAlphabeticalString(),
            "valid_number" to forge.anInt()
        )

        // When
        testedClient.setEvaluationContext(EvaluationContext(fakeTargetingKey, validAttributes))

        // Then
        // Should handle gracefully without warnings
        verify(mockInternalLogger, times(0)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M not throw exception W setEvaluationContext() { all supported attribute types }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val supportedAttributes = mapOf(
            "string_type" to forge.anAlphabeticalString(),
            "int_type" to forge.anInt(),
            "long_type" to forge.aLong(),
            "double_type" to forge.aDouble(),
            "float_type" to forge.aFloat(),
            "boolean_type" to forge.aBool()
        )

        // When & Then
        // Should not throw any exceptions
        testedClient.setEvaluationContext(EvaluationContext(fakeTargetingKey, supportedAttributes))
    }

    @Test
    fun `M log error and not update context W setEvaluationContext() { blank targeting key }`() {
        // Given
        val blankTargetingKey = ""
        val fakeAttributes = mapOf("test" to "value")
        val evaluationContext = EvaluationContext(blankTargetingKey, fakeAttributes)

        // When
        testedClient.setEvaluationContext(evaluationContext)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            assertThat(firstValue.invoke()).contains("Cannot set context: targeting key cannot be blank")
        }
    }

    @Test
    fun `M log error and not update context W setEvaluationContext() { whitespace-only targeting key }`() {
        // Given
        val whitespaceTargetingKey = "   "
        val fakeAttributes = mapOf("test" to "value")
        val evaluationContext = EvaluationContext(whitespaceTargetingKey, fakeAttributes)

        // When
        testedClient.setEvaluationContext(evaluationContext)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            assertThat(firstValue.invoke()).contains("Cannot set context: targeting key cannot be blank")
        }
    }

    // endregion

    // region getBooleanDetails()

    @Test
    fun `M return evaluation details W getBooleanDetails() { flag exists }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getBooleanDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeFlag.variationValue)
        assertThat(result.variationKey).isEqualTo(fakeFlag.variationKey)
        assertThat(result.reason).isEqualTo(fakeFlag.reason)
    }

    @Test
    fun `M return default evaluation details W getBooleanDetails() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getBooleanDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getStringDetails()

    @Test
    fun `M return evaluation details W getStringDetails() { flag exists }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getStringDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeFlag.variationValue)
        assertThat(result.variationKey).isEqualTo(fakeFlag.variationKey)
        assertThat(result.reason).isEqualTo(fakeFlag.reason)
    }

    @Test
    fun `M return default evaluation details W getStringDetails() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getStringDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getNumberDetails()

    @Test
    fun `M return evaluation details W getNumberDetails() { flag exists }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        val fakeFlag = forge.getForgery<PrecomputedFlag>()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getNumberDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeFlag.variationValue)
        assertThat(result.variationKey).isEqualTo(fakeFlag.variationKey)
        assertThat(result.reason).isEqualTo(fakeFlag.reason)
    }

    @Test
    fun `M return default evaluation details W getNumberDetails() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getNumberDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getIntDetails()

    @Test
    fun `M return evaluation details W getIntDetails() { flag exists }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getIntDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeFlag.variationValue)
        assertThat(result.variationKey).isEqualTo(fakeFlag.variationKey)
        assertThat(result.reason).isEqualTo(fakeFlag.reason)
    }

    @Test
    fun `M return default evaluation details W getIntDetails() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getIntDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getStructureDetails()

    @Test
    fun `M return evaluation details W getStructureDetails() { flag exists }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("key", forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedClient.getStructureDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeFlag.variationValue)
        assertThat(result.variationKey).isEqualTo(fakeFlag.variationKey)
        assertThat(result.reason).isEqualTo(fakeFlag.reason)
    }

    @Test
    fun `M return default evaluation details W getStructureDetails() { flag does not exist }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.getStructureDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion
}
