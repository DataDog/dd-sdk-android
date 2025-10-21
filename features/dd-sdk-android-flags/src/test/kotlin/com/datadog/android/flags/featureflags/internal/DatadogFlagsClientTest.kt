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
import com.datadog.android.flags.internal.EventsProcessor
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
    lateinit var mockProcessor: EventsProcessor

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
            rumEvaluationLogger = mockRumEvaluationLogger,
            processor = mockProcessor
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
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

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
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

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
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M send RUM evaluation message W resolveBooleanValue() { rumIntegrationEnabled true }`(forge: Forge) {
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
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
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
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockRumEvaluationLogger)
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
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        verifyNoInteractions(mockRumEvaluationLogger)
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
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
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
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

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
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

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
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

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
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

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
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

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
            variationType = VariationType.NUMBER.value,
            variationValue = fakeFlagValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

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
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

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
            variationType = VariationType.OBJECT.value,
            variationValue = fakeFlagValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.toString()).isEqualTo(fakeFlagValue.toString())
    }

    @Test
    fun `M return default value W resolveStructureValue() { flag exists with invalid JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "invalid json {"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Parse errors are not logged - they're expected in normal operation
    }

    @Test
    fun `M return default value W resolveStructureValue() { flag exists with malformed JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "{\"unclosed\": \"quote"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Parse errors are not logged - they're expected in normal operation
    }

    @Test
    fun `M return default value W resolveStructureValue() { flag exists with completely invalid JSON }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "not json at all!"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Parse errors are not logged - they're expected in normal operation
    }

    @Test
    fun `M return default value W resolveStructureValue() {flag does not exist}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put(fakeJsonKey, forge.anAlphabeticalString())
        }
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

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
            variationType = VariationType.STRING.value,
            variationValue = fakeFlagValue
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(customRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = customRepository,
            flagsConfiguration = forge.getForgery(),
            rumEvaluationLogger = mockRumEvaluationLogger,
            processor = mockProcessor
        )

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then

        assertThat(result).isEqualTo(fakeFlagValue)

        verify(customRepository).getPrecomputedFlagWithContext(fakeFlagKey)
    }

    // endregion

    // region resolve() - Generic Resolution with ResolutionDetails

    @Test
    fun `M return ResolutionDetails with value and metadata W resolve() { successful resolution }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlagValue = !fakeDefaultValue
        val fakeVariationKey = forge.anAlphabeticalString()
        val fakeReason = forge.anElementFrom("TARGETING_MATCH", "RULE_MATCH", "DEFAULT")
        val fakeExtraLogging = JSONObject().apply {
            put("version", forge.anAlphabeticalString())
            put("environment", forge.anElementFrom("prod", "staging", "dev"))
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = fakeFlagValue.toString(),
            variationKey = fakeVariationKey,
            reason = fakeReason,
            extraLogging = fakeExtraLogging
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeFlagValue)
        assertThat(result.variant).isEqualTo(fakeVariationKey)
        assertThat(result.reason).isEqualTo(fakeReason)
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
        assertThat(result.flagMetadata).isNotNull
        assertThat(result.flagMetadata).containsKeys("version", "environment")
    }

    @Test
    fun `M return ResolutionDetails with error W resolve() { type mismatch }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = "some-string"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.variant).isNull()
        assertThat(result.reason).isEqualTo("ERROR")
        assertThat(result.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(result.errorMessage).contains("Flag '$fakeFlagKey'")
        assertThat(result.errorMessage).contains("has type 'string' but Boolean was requested")
        assertThat(result.flagMetadata).isNull()

        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M return ResolutionDetails with error W resolve() { flag not found }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn null

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
        assertThat(result.variant).isNull()
        assertThat(result.reason).isEqualTo("ERROR")
        assertThat(result.errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
        assertThat(result.errorMessage).contains("Flag '$fakeFlagKey'")
        assertThat(result.errorMessage).contains("Flag not found")
        assertThat(result.flagMetadata).isNull()

        // Verify no exposure tracked when flag not found
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M return ResolutionDetails with parse error W resolve() { invalid JSON object }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", "value")
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.OBJECT.value,
            variationValue = "not valid json{"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value.toString()).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.variant).isNull()
        assertThat(result.reason).isEqualTo("ERROR")
        assertThat(result.errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
        assertThat(result.errorMessage).contains("Flag '$fakeFlagKey'")
        assertThat(result.errorMessage).contains("Failed to parse value")
        assertThat(result.flagMetadata).isNull()

        // Verify no exposure tracked for parse error
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M track exposure W resolve() { successful resolution and trackExposures enabled }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        val fakeFlagValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = fakeFlagValue
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeFlagValue)
        // Verify exposure tracked for successful resolution
        verify(mockProcessor).processEvent(
            flagName = eq(fakeFlagKey),
            context = eq(fakeContext),
            data = eq(fakeFlag)
        )
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
        verifyNoInteractions(mockEvaluationsManager)
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

    // region exposure event logging configuration

    @Test
    fun `M not write exposure event to backend W resolveBooleanValue() { trackExposures is false }`(forge: Forge) {
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
        whenever(mockFeatureSdkCore.getFeature(any())) doReturn null
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = false,
                rumIntegrationEnabled = false
            ),
            rumEvaluationLogger = mockRumEvaluationLogger,
            processor = mockProcessor
        )

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        verifyNoInteractions(mockFeatureSdkCore)
    }

    @Test
    fun `M write exposure event to backend W resolveBooleanValue() { trackExposures is true }`(forge: Forge) {
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

        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        // Verify that processor was called to write exposure event to backend
        verify(mockProcessor).processEvent(
            flagName = eq(fakeFlagKey),
            context = eq(fakeEvaluationContext),
            data = eq(fakeFlag)
        )
    }

    // endregion

    // region rumIntegrationEnabled

    @Test
    fun `M send RUM evaluation message W resolveBooleanValue() { rumIntegrationEnabled is true }`(forge: Forge) {
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

        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        verify(mockRumEvaluationLogger).logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeFlagValue
        )
    }

    @Test
    fun `M not send RUM evaluation message W resolveBooleanValue() { rumIntegrationEnabled is false }`(forge: Forge) {
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

        testedClient = DatadogFlagsClient(
            featureSdkCore = mockFeatureSdkCore,
            evaluationsManager = mockEvaluationsManager,
            flagsRepository = mockFlagsRepository,
            flagsConfiguration = forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = true,
                rumIntegrationEnabled = false
            ),
            rumEvaluationLogger = mockRumEvaluationLogger,
            processor = mockProcessor
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn
            (fakeFlag to fakeEvaluationContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeFlagValue)
        verifyNoInteractions(mockRumEvaluationLogger)
    }

    // endregion

    // region Type Mismatch Tests

    @Test
    fun `M return default value and not track exposure W resolveBooleanValue() { type mismatch - string flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = "some-string"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'string' but Boolean was requested")
        }
    }

    @Test
    fun `M return default value and not track exposure W resolveIntValue() { type mismatch - boolean flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.BOOLEAN.value,
            variationValue = "true"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'boolean' but Int was requested")
        }
    }

    @Test
    fun `M return default value and not track exposure W resolveStructureValue() { type mismatch - string flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("default", "value")
        }
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.STRING.value,
            variationValue = "just a string"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.toString()).isEqualTo(fakeDefaultValue.toString())
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'string' but JSONObject was requested")
        }
    }

    @Test
    fun `M return default value and not track exposure W resolveStringValue() { type mismatch - number flag }`(
        forge: Forge
    ) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.NUMBER.value,
            variationValue = "42.5"
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        // Verify no exposure tracked for type mismatch
        verifyNoInteractions(mockProcessor)

        // Verify warning was logged
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                eq(null),
                eq(false),
                eq(null)
            )
            val message = lastValue.invoke()
            assertThat(message).contains("Flag '$fakeFlagKey'")
            assertThat(message).contains("has type 'number' but String was requested")
        }
    }

    @Test
    fun `M accept number, float and int W resolveDoubleValue() { compatible types }`(forge: Forge) {
        // Given
        val fakeFlagKey1 = forge.anAlphabeticalString()
        val fakeFlagKey2 = forge.anAlphabeticalString()
        val fakeFlagKey3 = forge.anAlphabeticalString()
        val fakeFlagValue = forge.aDouble()
        val fakeFlagIntValue = forge.anInt()
        val fakeDefaultValue = forge.aDouble()

        val numberFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.NUMBER.value,
            variationValue = fakeFlagValue.toString()
        )
        val floatFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.FLOAT.value,
            variationValue = fakeFlagValue.toString()
        )
        val intFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = fakeFlagIntValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey1)) doReturn (numberFlag to fakeContext)
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey2)) doReturn (floatFlag to fakeContext)
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey3)) doReturn (intFlag to fakeContext)

        // When
        val result1 = testedClient.resolveDoubleValue(fakeFlagKey1, fakeDefaultValue)
        val result2 = testedClient.resolveDoubleValue(fakeFlagKey2, fakeDefaultValue)
        val result3 = testedClient.resolveDoubleValue(fakeFlagKey3, fakeDefaultValue)

        // Then
        assertThat(result1).isEqualTo(fakeFlagValue)
        assertThat(result2).isEqualTo(fakeFlagValue)
        assertThat(result3).isEqualTo(fakeFlagIntValue.toDouble())
    }

    @Test
    fun `M return default value W resolveIntValue() { value exceeds Int MAX_VALUE }`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()
        // Value that's too large for Int
        val oversizedValue = Int.MAX_VALUE.toLong() + 1L
        val fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = VariationType.INTEGER.value,
            variationValue = oversizedValue.toString()
        )
        val fakeContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )
        whenever(mockFlagsRepository.getPrecomputedFlagWithContext(fakeFlagKey)) doReturn (fakeFlag to fakeContext)

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        // toIntOrNull() returns null for values outside Int range, so default is returned
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion
}
