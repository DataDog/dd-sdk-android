/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.tools.unit.forge.BaseConfigurator
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockStateObservable: StateObservable

    private lateinit var provider: DatadogFlagsProvider
    private var capturedStateListener: FlagsStateListener? = null

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        Mockito.lenient().`when`(mockFlagsClient.state).thenReturn(mockStateObservable)
        Mockito.lenient().`when`(mockStateObservable.addListener(any())).thenAnswer {
            capturedStateListener = it.getArgument(0)
            Unit
        }
        Mockito.lenient().doNothing().`when`(mockStateObservable).removeListener(any())
        Mockito.lenient().`when`(mockStateObservable.getCurrentState()).thenReturn(FlagsClientState.NotReady)

        // Mock setEvaluationContext to immediately call the callback's onSuccess
        Mockito.lenient().`when`(mockFlagsClient.setEvaluationContext(any(), any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<com.datadog.android.flags.EvaluationContextCallback?>(1)
            callback?.onSuccess()
            Unit
        }

        provider = DatadogFlagsProvider.wrap(mockFlagsClient, mockSdkCore)
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
        assertThat(result.errorMessage).isNull()
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
        assertThat(result.variant).isNull()
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
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `M return default string value W getStringEvaluation() {error resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery defaultValue: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val errorCode = forge.aValueFrom(ErrorCode::class.java)
        val resolution = ResolutionDetails(
            value = defaultValue,
            errorCode = errorCode,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getStringEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(defaultValue)
        assertThat(result.variant).isNull()
        assertThat(result.errorCode).isNotNull
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")
    }

    // endregion

    // region Integer Evaluation

    @Test
    fun `M return integer value W getIntegerEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery variant: String
    ) {
        // Given
        val expectedValue = forge.anInt()
        val defaultValue = forge.anInt()
        val resolution = ResolutionDetails(
            value = expectedValue,
            variant = variant,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getIntegerEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `M return default integer value W getIntegerEvaluation() {error resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val defaultValue = forge.anInt()
        val errorCode = forge.aValueFrom(ErrorCode::class.java)
        val resolution = ResolutionDetails(
            value = defaultValue,
            errorCode = errorCode,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getIntegerEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(defaultValue)
        assertThat(result.variant).isNull()
        assertThat(result.errorCode).isNotNull
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")
    }

    // endregion

    // region Double Evaluation

    @Test
    fun `M return double value W getDoubleEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery variant: String
    ) {
        // Given
        val expectedValue = forge.aDouble()
        val defaultValue = forge.aDouble()
        val resolution = ResolutionDetails(
            value = expectedValue,
            variant = variant,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getDoubleEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(expectedValue)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `M return default double value W getDoubleEvaluation() {error resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val defaultValue = forge.aDouble()
        val errorCode = forge.aValueFrom(ErrorCode::class.java)
        val resolution = ResolutionDetails(
            value = defaultValue,
            errorCode = errorCode,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(flagKey, defaultValue)).thenReturn(resolution)

        // When
        val result = provider.getDoubleEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isEqualTo(defaultValue)
        assertThat(result.variant).isNull()
        assertThat(result.errorCode).isNotNull
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")
    }

    // endregion

    // region Object Evaluation

    @Test
    fun `M return Value structure W getObjectEvaluation() {successful resolution}`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery variant: String
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
            variant = variant,
            reason = forge.aValueFrom(ResolutionReason::class.java)
        )
        whenever(mockFlagsClient.resolve(eq(flagKey), any<Map<String, Any?>>())).thenReturn(resolution)

        // When
        val result = provider.getObjectEvaluation(flagKey, defaultValue, null)

        // Then
        assertThat(result.value).isInstanceOf(Value.Structure::class.java)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo(resolution.reason?.name)
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
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
        assertThat(result.variant).isNull()
        assertThat(result.errorCode).isNotNull()
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")

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
        val context = ImmutableContext(targetingKey = "user-123")

        // When
        provider.initialize(context)

        // Then - triggers context setting via suspend function
        verify(mockFlagsClient).setEvaluationContext(any(), any())
    }

    @Test
    fun `M return immediately W initialize() {already ready state}`() = runTest {
        // Given
        val context = ImmutableContext(targetingKey = "user-123")

        // When
        provider.initialize(context)

        // Then - completes successfully by calling setEvaluationContext
        verify(mockFlagsClient).setEvaluationContext(any(), any())
    }

    // endregion

    // region Provider Lifecycle - onContextSet()
    // Note: Full integration testing of blocking behavior requires running FlagsClient

    @Test
    fun `M call setEvaluationContext W onContextSet() {context change}`() = runTest {
        // Given
        val newContext = ImmutableContext(targetingKey = "user-456")

        // When
        provider.onContextSet(null, newContext)

        // Then - triggers context setting via suspend function
        verify(mockFlagsClient).setEvaluationContext(any(), any())
    }

    // endregion

    // region Provider Events - observe()

    @Test
    fun `M return Flow W observe()`() {
        // When
        provider.observe()

        // Then - Flow returned without error
        verify(mockStateObservable, never()).addListener(any()) // Listener not added until collection starts
    }

    @Test
    fun `M emit ProviderReady W observe() {state changes to Ready}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        delay(100) // Let flow set up
        capturedStateListener?.onStateChanged(FlagsClientState.Ready)
        delay(100) // Let event propagate
        job.cancel()

        // Then
        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(OpenFeatureProviderEvents.ProviderReady::class.java)
    }

    @Test
    fun `M emit ProviderStale W observe() {state changes to Stale}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        delay(100) // Let flow set up
        capturedStateListener?.onStateChanged(FlagsClientState.Stale)
        delay(100) // Let event propagate
        job.cancel()

        // Then
        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(OpenFeatureProviderEvents.ProviderStale::class.java)
    }

    @Test
    fun `M emit ProviderError W observe() {state changes to Error}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()
        val errorState = FlagsClientState.Error(RuntimeException("test error"))

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        delay(100) // Let flow set up
        capturedStateListener?.onStateChanged(errorState)
        delay(100) // Let event propagate
        job.cancel()

        // Then
        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(OpenFeatureProviderEvents.ProviderError::class.java)
    }

    @Test
    fun `M not emit event W observe() {state changes to NotReady}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        delay(100) // Let flow set up
        capturedStateListener?.onStateChanged(FlagsClientState.NotReady)
        delay(100) // Let event propagate
        job.cancel()

        // Then - NotReady is filtered out (SDK handles via blocking initialize)
        assertThat(events).isEmpty()
    }

    @Test
    fun `M not emit event W observe() {state changes to Reconciling}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        delay(100) // Let flow set up
        capturedStateListener?.onStateChanged(FlagsClientState.Reconciling)
        delay(100) // Let event propagate
        job.cancel()

        // Then - Reconciling is filtered out (SDK emits PROVIDER_RECONCILING)
        assertThat(events).isEmpty()
    }

    @Test
    fun `M remove listener W observe() {flow cancelled}`() = runTest {
        // When
        val job = launch {
            provider.observe().collect { }
        }
        delay(100) // Let flow set up
        verify(mockStateObservable).addListener(any())
        job.cancel()
        delay(100) // Let cleanup happen

        // Then
        verify(mockStateObservable).removeListener(any())
    }

    // endregion

    // region Shutdown

    @Test
    fun `M not throw exception W shutdown()`() {
        assertDoesNotThrow { provider.shutdown() }
    }

    // endregion
}
