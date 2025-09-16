/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.internal.DatadogFlagsProvider.Companion.ERROR_FAILED_PARSING_JSON
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlagConstants
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.model.FlagsContext
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
            assertThat(lastValue()).isEqualTo(ERROR_FAILED_PARSING_JSON.format(fakeFlagKey))
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
}
