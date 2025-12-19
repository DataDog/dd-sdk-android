/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.SdkCore
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.tools.unit.forge.BaseConfigurator
import dev.openfeature.kotlin.sdk.Value
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode as OpenFeatureErrorCode

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(BaseConfigurator::class)
internal class DatadogFlagsProviderTest {

    @Mock
    lateinit var mockFlagsClient: FlagsClient

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockStateObservable: StateObservable

    private lateinit var provider: DatadogFlagsProvider
    private var capturedStateListener: FlagsStateListener? = null

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(mockFlagsClient.state).thenReturn(mockStateObservable)
        Mockito.lenient().`when`(mockStateObservable.addListener(any())).thenAnswer {
            capturedStateListener = it.getArgument(0)
            Unit
        }
        Mockito.lenient().`when`(mockStateObservable.getCurrentState()).thenReturn(FlagsClientState.NotReady)

        provider = DatadogFlagsProvider.wrap(mockFlagsClient)
    }

    @Test
    fun `M return provider metadata W metadata()`() {
        // When
        val metadata = provider.metadata

        // Then
        assertThat(metadata.name).isEqualTo("Datadog Feature Flags Provider")
    }

    @Test
    fun `M return empty hooks list W hooks()`() {
        // When
        val hooks = provider.hooks

        // Then
        assertThat(hooks).isEmpty()
    }

    // region Boolean Evaluation

    @Test
    fun `M return boolean value W getBooleanEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery variant: String
    ) {
        // Given
        val expectedValue = forge.aBool()
        val defaultValue = forge.aBool()
        val resolution = ResolutionDetails(
            value = expectedValue,
            variant = variant,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getBooleanEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
        assertThat(result.errorCode).isNull()
    }

    @Test
    fun `M return default boolean value W getBooleanEvaluation() {error resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val defaultValue = forge.aBool()
        val errorCode = forge.aValueFrom(ErrorCode::class.java)
        val resolution = ResolutionDetails(
            value = defaultValue,
            errorCode = errorCode,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getBooleanEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(defaultValue)
        assertThat(result.errorCode).isNotNull
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")
    }

    // endregion

    // region String Evaluation

    @Test
    fun `M return string value W getStringEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery expectedValue: String,
        @StringForgery defaultValue: String,
        @StringForgery variant: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = expectedValue,
            variant = variant,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getStringEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
    }

    // endregion

    // region Integer Evaluation

    @Test
    fun `M return integer value W getIntegerEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String
    ) {
        // Given
        val expectedValue = forge.anInt()
        val defaultValue = forge.anInt()
        val resolution = ResolutionDetails(
            value = expectedValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getIntegerEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
    }

    // endregion

    // region Double Evaluation

    @Test
    fun `M return double value W getDoubleEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String
    ) {
        // Given
        val expectedValue = forge.aDouble()
        val defaultValue = forge.aDouble()
        val resolution = ResolutionDetails(
            value = expectedValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getDoubleEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
    }

    // endregion

    // region Object Evaluation

    @Test
    fun `M return Value structure W getObjectEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String
    ) {
        // Given
        val defaultValue = Value.Structure(
            forge.aMap {
                anAlphabeticalString() to Value.String(anAlphabeticalString())
            }
        )
        val expectedMapValue: Map<String, Any?> = forge.aMap {
            anAlphabeticalString() to anAlphabeticalString()
        }
        val resolution = ResolutionDetails(
            value = expectedMapValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(eq(flagKey), any<Map<String, Any?>>())).thenReturn(resolution)

        // When
        val result = provider.getObjectEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isInstanceOf(Value.Structure::class.java)
        val structure = checkNotNull(result.value.asStructure())
        assertThat(structure.keys).isEqualTo(expectedMapValue.keys)
        expectedMapValue.forEach { (key, value) ->
            assertThat(structure[key]).isInstanceOf(Value.String::class.java)
            assertThat((structure[key] as Value.String).asString()).isEqualTo(value.toString())
        }
    }

    @Test
    fun `M preserve default Value types W getObjectEvaluation() {error resolution}`(
        @StringForgery flagKey: String,
        @Forgery fakeErrorCode: ErrorCode,
        @StringForgery errorMessage: String
    ) {
        // Given
        val defaultValue = Value.Structure(
            mapOf(
                "intValue" to Value.Integer(42),
                "boolValue" to Value.Boolean(true),
                "doubleValue" to Value.Double(3.14),
                "stringValue" to Value.String("test"),
                "nested" to Value.Structure(mapOf("key" to Value.String("value")))
            )
        )
        val resolution = ResolutionDetails(
            value = emptyMap<String, Any?>(), // Error case returns empty map
            errorCode = fakeErrorCode,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(eq(flagKey), any<Map<String, Any?>>()))
            .thenReturn(resolution)

        // When
        val result = provider.getObjectEvaluation(flagKey, defaultValue, null)

        // Then - Original defaultValue returned with types preserved
        assertThat(result.value).isSameAs(defaultValue)
        assertThat(result.errorCode).isNotNull()
        assertThat(result.errorMessage).isEqualTo(errorMessage)

        // Verify types are preserved (not converted to strings)
        val structure = checkNotNull(result.value.asStructure())
        assertThat(structure["intValue"]).isInstanceOf(Value.Integer::class.java)
        assertThat((structure["intValue"] as Value.Integer).asInteger()).isEqualTo(42)
        assertThat(structure["boolValue"]).isInstanceOf(Value.Boolean::class.java)
        assertThat((structure["boolValue"] as Value.Boolean).asBoolean()).isTrue()
        assertThat(structure["doubleValue"]).isInstanceOf(Value.Double::class.java)
        assertThat(structure["nested"]).isInstanceOf(Value.Structure::class.java)
    }

    // endregion

    // region Error Code Mapping

    @Test
    fun `M map PROVIDER_NOT_READY error code W toOpenFeatureErrorCode()`(
        @BoolForgery fakeDefaultValue: Boolean,
        @StringForgery flagKey: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = fakeDefaultValue,
            errorCode = ErrorCode.PROVIDER_NOT_READY,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(flagKey, fakeDefaultValue)).thenReturn(resolution)

        // When
        val result = provider.getBooleanEvaluation(flagKey, fakeDefaultValue, null)

        // Then
        assertThat(result.errorCode).isEqualTo(OpenFeatureErrorCode.PROVIDER_NOT_READY)
    }

    @Test
    fun `M map PARSE_ERROR error code W toOpenFeatureErrorCode()`(
        @BoolForgery fakeDefaultValue: Boolean,
        @StringForgery flagKey: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = fakeDefaultValue,
            errorCode = ErrorCode.PARSE_ERROR,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(flagKey, fakeDefaultValue)).thenReturn(resolution)

        // When
        val result = provider.getBooleanEvaluation(flagKey, fakeDefaultValue, null)

        // Then
        assertThat(result.errorCode).isEqualTo(OpenFeatureErrorCode.PARSE_ERROR)
    }

    @Test
    fun `M map TYPE_MISMATCH error code W toOpenFeatureErrorCode()`(
        @BoolForgery fakeDefaultValue: Boolean,
        @StringForgery flagKey: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = fakeDefaultValue,
            errorCode = ErrorCode.TYPE_MISMATCH,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(flagKey, fakeDefaultValue)).thenReturn(resolution)

        // When
        val result = provider.getBooleanEvaluation(flagKey, fakeDefaultValue, null)

        // Then
        assertThat(result.errorCode).isEqualTo(OpenFeatureErrorCode.TYPE_MISMATCH)
    }

    // endregion

    // region Value Conversion - Long to Double Promotion

    @Test
    fun `M convert to Integer W getObjectEvaluation() {Long within Int range}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery jsonKey: String
    ) {
        // Given
        val longValue = forge.anInt().toLong()
        val mapValue: Map<String, Any?> = mapOf(jsonKey to longValue)
        val resolution = ResolutionDetails(
            value = mapValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(eq(flagKey), any<Map<String, Any?>>()))
            .thenReturn(resolution)

        // When
        val result = provider.getObjectEvaluation(
            flagKey,
            Value.Structure(mapOf()),
            null
        )

        // Then
        val structure = checkNotNull(result.value.asStructure())
        val countValue = structure[jsonKey]
        assertThat(countValue).isInstanceOf(Value.Integer::class.java)
        assertThat((countValue as Value.Integer).asInteger()).isEqualTo(longValue.toInt())
    }

    @Test
    fun `M promote to Double W getObjectEvaluation() {Long exceeds Int MAX_VALUE}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery jsonKey: String
    ) {
        // Given
        val largeValue = forge.aLong(min = Int.MAX_VALUE.toLong() + 1)
        val mapValue: Map<String, Any?> = mapOf(jsonKey to largeValue)
        val resolution = ResolutionDetails(
            value = mapValue,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(eq(flagKey), any<Map<String, Any?>>()))
            .thenReturn(resolution)

        // When
        val result = provider.getObjectEvaluation(
            flagKey,
            Value.Structure(mapOf()),
            null
        )

        // Then
        val structure = checkNotNull(result.value.asStructure())
        val timestampValue = structure[jsonKey]
        assertThat(timestampValue).isInstanceOf(Value.Double::class.java)
        assertThat((timestampValue as Value.Double).asDouble()).isEqualTo(largeValue.toDouble())
    }

    // endregion

    // region Provider Lifecycle - initialize()
    // Note: Full integration testing of blocking behavior requires running FlagsClient
    // These tests verify the basic contract and listener management

    @Test
    fun `M return immediately W initialize() {null context}`() = runTest {
        // When
        provider.initialize(null)

        // Then - completes without calling state management
        verify(mockStateObservable, never()).addListener(any())
    }

    @Test
    fun `M call setEvaluationContext W initialize() {valid context}`() = runTest {
        // Given
        val context = dev.openfeature.kotlin.sdk.ImmutableContext(targetingKey = "user-123")
        whenever(mockStateObservable.getCurrentState()).thenReturn(FlagsClientState.Ready)

        // When
        provider.initialize(context)

        // Then - triggers context setting
        verify(mockFlagsClient).setEvaluationContext(any())
        verify(mockStateObservable).addListener(any())
    }

    @Test
    fun `M return immediately W initialize() {already ready state}`() = runTest {
        // Given
        val context = dev.openfeature.kotlin.sdk.ImmutableContext(targetingKey = "user-123")
        whenever(mockStateObservable.getCurrentState()).thenReturn(FlagsClientState.Ready)

        // When
        provider.initialize(context)

        // Then - completes via getCurrentState() fast path
        verify(mockStateObservable).getCurrentState()
        verify(mockStateObservable).removeListener(any())
    }

    // endregion

    // region Provider Lifecycle - onContextSet()
    // Note: Full integration testing of blocking behavior requires running FlagsClient

    @Test
    fun `M call setEvaluationContext W onContextSet() {context change}`() = runTest {
        // Given
        val newContext = dev.openfeature.kotlin.sdk.ImmutableContext(targetingKey = "user-456")
        whenever(mockStateObservable.getCurrentState()).thenReturn(FlagsClientState.Ready)

        // When
        provider.onContextSet(null, newContext)

        // Then - triggers context setting
        verify(mockFlagsClient).setEvaluationContext(any())
        verify(mockStateObservable).addListener(any())
    }

    // endregion

    // region Provider Events - observe()

    @Test
    fun `M return Flow W observe()`() {
        // When
        val flow = provider.observe()

        // Then - returns a Flow (basic smoke test)
        assertThat(flow).isNotNull()
        verify(mockStateObservable, never()).addListener(any()) // Listener not added until collection starts
    }

    // endregion

    // region Shutdown

    @Test
    fun `M not throw exception W shutdown()`() {
        assertThatCode { provider.shutdown() }.doesNotThrowAnyException()
    }

    // endregion
}
