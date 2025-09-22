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
internal class DatadogFlagsProviderTest {

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

    private lateinit var testedProvider: DatadogFlagsProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        ForgeConfigurator.configure(forge)
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger

        testedProvider = DatadogFlagsProvider(
            executorService = mockExecutorService,
            featureSdkCore = mockFeatureSdkCore,
            flagsContext = mockFlagsContext,
            flagsRepository = mockFlagsRepository
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
            variationType = PrecomputedFlagConstants.VariationType.BOOLEAN,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedProvider.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W resolveBooleanValue() { flag exists with invalid boolean string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.BOOLEAN,
            variationValue = "not-a-boolean"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedProvider.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

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
        val result = testedProvider.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
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
            variationType = PrecomputedFlagConstants.VariationType.STRING,
            variationValue = fakeFlagValue
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedProvider.resolveStringValue(fakeFlagKey, fakeDefaultValue)

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
        val result = testedProvider.resolveStringValue(fakeFlagKey, fakeDefaultValue)

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
            variationType = PrecomputedFlagConstants.VariationType.INTEGER,
            variationValue = fakeFlagValue.toString()
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedProvider.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W resolveIntValue() { flag exists with invalid integer string }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.INTEGER,
            variationValue = "not-an-integer"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedProvider.resolveIntValue(fakeFlagKey, fakeDefaultValue)

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
        val result = testedProvider.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveNumberValue()

    @Test
    fun `M return flag value W resolveNumberValue() { flag exists with string double value }`(forge: Forge) {
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
        val result = testedProvider.resolveNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
    }

    @Test
    fun `M return default value W resolveNumberValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedProvider.resolveNumberValue(fakeFlagKey, fakeDefaultValue)

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
        val result = testedProvider.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

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
            put("default", forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.JSON,
            variationValue = "invalid json {"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedProvider.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

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
    fun `M log specific error message W resolveStructureValue() { flag exists with malformed JSON }`(forge: Forge) {
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
        val result = testedProvider.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

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
    fun `M log error W resolveStructureValue() { flag exists with completely invalid JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = PrecomputedFlagConstants.VariationType.JSON,
            variationValue = "not json at all!"
        )
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn fakeFlag

        // When
        val result = testedProvider.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

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
    fun `M return default value W resolveStructureValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", forge.anAlphabeticalString())
        }
        whenever(mockFlagsRepository.getPrecomputedFlag(fakeFlagKey)) doReturn null

        // When
        val result = testedProvider.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

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
        val provider = DatadogFlagsProvider(
            executorService = mockExecutorService,
            featureSdkCore = mockFeatureSdkCore,
            flagsContext = mockFlagsContext,
            flagsRepository = customRepository
        )

        // Then
        val result = provider.resolveStringValue(fakeFlagKey, "default")
        assertThat(result).isEqualTo(fakeFlagValue)

        verify(customRepository).getPrecomputedFlag(fakeFlagKey)
    }

    // endregion

    // region setContext()

    @Test
    fun `M create evaluation context W setContext() { valid attributes }`(forge: Forge) {
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
        testedProvider.setContext(EvaluationContext(fakeTargetingKey, fakeAttributes))

        // Then
        // Since orchestrator runs async, we can't directly verify the EvaluationContext
        // But we can verify that the method completes without throwing
        // Integration tests would verify the full flow
    }

    @Test
    fun `M filter unsupported attribute types W setContext() { mixed valid and invalid types }`(forge: Forge) {
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
        testedProvider.setContext(evaluationContext)

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
    fun `M handle empty attributes W setContext() { empty map }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val emptyAttributes = emptyMap<String, Any>()

        // When
        testedProvider.setContext(EvaluationContext(fakeTargetingKey, emptyAttributes))

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
    fun `M preserve targeting key exactly W setContext() { special characters }`() {
        // Given
        val specialTargetingKey = "user@domain.com-123_test.key"
        val fakeAttributes = mapOf("test" to "value")

        // When
        testedProvider.setContext(EvaluationContext(specialTargetingKey, fakeAttributes))

        // Then
        // Method should complete without throwing for special characters
        // The targeting key preservation is tested in EvaluationContext tests
    }

    @Test
    fun `M handle filtered attributes W setContext() { valid attributes only }`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val validAttributes = mapOf(
            "valid_string" to forge.anAlphabeticalString(),
            "valid_number" to forge.anInt()
        )

        // When
        testedProvider.setContext(EvaluationContext(fakeTargetingKey, validAttributes))

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
    fun `M not throw exception W setContext() { all supported attribute types }`(forge: Forge) {
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
        testedProvider.setContext(EvaluationContext(fakeTargetingKey, supportedAttributes))
    }

    @Test
    fun `M log error and not update context W setContext() { blank targeting key }`() {
        // Given
        val blankTargetingKey = ""
        val fakeAttributes = mapOf("test" to "value")
        val evaluationContext = EvaluationContext(blankTargetingKey, fakeAttributes)

        // When
        testedProvider.setContext(evaluationContext)

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
    fun `M log error and not update context W setContext() { whitespace-only targeting key }`() {
        // Given
        val whitespaceTargetingKey = "   "
        val fakeAttributes = mapOf("test" to "value")
        val evaluationContext = EvaluationContext(whitespaceTargetingKey, fakeAttributes)

        // When
        testedProvider.setContext(evaluationContext)

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
}
