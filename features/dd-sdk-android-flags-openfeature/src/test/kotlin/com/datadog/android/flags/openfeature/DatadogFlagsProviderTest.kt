/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
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
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)

        // Use lenient stubbings for mocks that are conditionally used across different tests
        lenient().whenever(mockFlagsClient.state).thenReturn(mockStateObservable)

        lenient().whenever(mockStateObservable.addListener(any())).doAnswer {
            capturedStateListener = it.getArgument(0)
            Unit
        }

        lenient().apply { doNothing().whenever(mockStateObservable).removeListener(any()) }

        lenient().whenever(mockStateObservable.getCurrentState()).thenReturn(FlagsClientState.NotReady)

        lenient().whenever(mockFlagsClient.setEvaluationContext(any(), any())).doAnswer { invocation ->
            val callback = invocation.getArgument<EvaluationContextCallback?>(1)
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

    // region Primitive Type Evaluations

    @Test
    fun `M delegate to FlagsClient and convert result W getBooleanEvaluation()`(
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
    fun `M delegate to FlagsClient and convert result W getStringEvaluation()`(
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

    @Test
    fun `M delegate to FlagsClient and convert result W getIntegerEvaluation()`(
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
    }

    @Test
    fun `M delegate to FlagsClient and convert result W getDoubleEvaluation()`(
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
    }

    @Test
    fun `M properly convert error details W evaluation with error`(
        forge: Forge,
        @StringForgery flagKey: String,
        @StringForgery errorMessage: String
    ) {
        // Given - test error conversion with any type (using boolean as example)
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

        // Then - verify error details are properly converted
        assertThat(result.value).isEqualTo(defaultValue)
        assertThat(result.variant).isNull()
        assertThat(result.errorCode).isNotNull
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")
    }

    // endregion

    // region Object Evaluation
    // Note: Object evaluation has unique logic with sentinel values and type conversion,
    // so we need more thorough tests here.

    @Test
    fun `M convert Map to Value structure W getObjectEvaluation() {successful resolution}`(
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

        // Then - verify Map is converted to Value.Structure
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
    fun `M preserve original default Value W getObjectEvaluation() {sentinel returned}`(
        @StringForgery flagKey: String,
        @Forgery fakeErrorCode: ErrorCode,
        @StringForgery errorMessage: String
    ) {
        // Given - test that when FlagsClient returns the sentinel, we return the original default
        // This is the key logic of getObjectEvaluation: avoiding unnecessary conversions
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
            value = emptyMap<String, Any?>(), // Sentinel: empty map returned by FlagsClient
            errorCode = fakeErrorCode,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )
        whenever(mockFlagsClient.resolve(eq(flagKey), any<Map<String, Any?>>()))
            .thenReturn(resolution)

        // When
        val result = provider.getObjectEvaluation(flagKey, defaultValue, null)

        // Then - original defaultValue is returned unchanged (preserving types)
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

    // region Provider Lifecycle
    // Note: These tests focus on the provider's behavior - context conversion and delegation.
    // The FlagsClient's actual async/blocking behavior is tested in its own test suite.

    @Test
    fun `M delegate to FlagsClient W initialize() {with context}`() = runTest {
        // Given
        val context = ImmutableContext(targetingKey = "user-123")

        // When
        provider.initialize(context)

        // Then - verify context is converted and passed to FlagsClient
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())
        assertThat(contextCaptor.firstValue.targetingKey).isEqualTo("user-123")
    }

    @Test
    fun `M use empty context W initialize() {null context}`() = runTest {
        // When
        provider.initialize(null)

        // Then - verify empty context is passed to FlagsClient
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())
        assertThat(contextCaptor.firstValue).isEqualTo(EvaluationContext.EMPTY)
    }

    @Test
    fun `M delegate to FlagsClient W onContextSet()`() = runTest {
        // Given
        val newContext = ImmutableContext(targetingKey = "user-456")

        // When
        provider.onContextSet(null, newContext)

        // Then - verify context is converted and passed to FlagsClient
        val contextCaptor = argumentCaptor<EvaluationContext>()
        verify(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())
        assertThat(contextCaptor.firstValue.targetingKey).isEqualTo("user-456")
    }

    // endregion

    // region Provider Events
    // Note: These tests verify the provider correctly converts FlagsClient state changes
    // to OpenFeature events and properly manages listener lifecycle.

    @Test
    fun `M register listener W observe() {flow collected}`() = runTest {
        // When
        val job = launch {
            provider.observe().collect { }
        }
        // Wait for the listener to be registered by running pending coroutines
        testScheduler.runCurrent()

        // Then - listener is registered (verified by captured listener)
        assertThat(capturedStateListener).isNotNull()
        verify(mockStateObservable).addListener(any())

        job.cancel()
    }

    @Test
    fun `M emit ProviderReady event W observe() {state changes to Ready}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        testScheduler.runCurrent() // Start the flow collection
        checkNotNull(capturedStateListener).onStateChanged(FlagsClientState.Ready)
        testScheduler.runCurrent() // Process the state change
        job.cancel()

        // Then - Ready state is converted to ProviderReady event
        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(OpenFeatureProviderEvents.ProviderReady::class.java)
    }

    @Test
    fun `M emit ProviderStale event W observe() {state changes to Stale}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        testScheduler.runCurrent()
        checkNotNull(capturedStateListener).onStateChanged(FlagsClientState.Stale)
        testScheduler.runCurrent()
        job.cancel()

        // Then - Stale state is converted to ProviderStale event
        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(OpenFeatureProviderEvents.ProviderStale::class.java)
    }

    @Test
    fun `M emit ProviderError event W observe() {state changes to Error}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()
        val errorState = FlagsClientState.Error(RuntimeException("test error"))

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        testScheduler.runCurrent()
        checkNotNull(capturedStateListener).onStateChanged(errorState)
        testScheduler.runCurrent()
        job.cancel()

        // Then - Error state is converted to ProviderError event
        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(OpenFeatureProviderEvents.ProviderError::class.java)
    }

    @Test
    fun `M filter NotReady state W observe() {state changes to NotReady}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        testScheduler.runCurrent()
        checkNotNull(capturedStateListener).onStateChanged(FlagsClientState.NotReady)
        testScheduler.runCurrent()
        job.cancel()

        // Then - NotReady is filtered (handled by blocking initialize())
        assertThat(events).isEmpty()
    }

    @Test
    fun `M filter Reconciling state W observe() {state changes to Reconciling}`() = runTest {
        // Given
        val events = mutableListOf<OpenFeatureProviderEvents>()

        // When
        val job = launch {
            provider.observe().collect { events.add(it) }
        }
        testScheduler.runCurrent()
        checkNotNull(capturedStateListener).onStateChanged(FlagsClientState.Reconciling)
        testScheduler.runCurrent()
        job.cancel()

        // Then - Reconciling is filtered (SDK emits PROVIDER_RECONCILING)
        assertThat(events).isEmpty()
    }

    @Test
    fun `M unregister listener W observe() {flow cancelled}`() = runTest {
        // When
        val job = launch {
            provider.observe().collect { }
        }
        testScheduler.runCurrent()
        assertThat(capturedStateListener).isNotNull()
        verify(mockStateObservable).addListener(any())

        job.cancel()
        testScheduler.runCurrent() // Process cancellation

        // Then - listener is cleaned up
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
