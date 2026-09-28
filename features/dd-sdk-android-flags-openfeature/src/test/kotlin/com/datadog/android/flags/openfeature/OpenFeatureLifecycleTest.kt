/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionDetails
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.OpenFeatureStatus
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Exercises the released OpenFeature SDK and real Datadog adapter together. Only the native
 * FlagsClient boundary is controlled, so state notifications and operation completion can be
 * scheduled independently, as they are by the native client's executor and timeout scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OpenFeatureLifecycleTest {

    @Test
    fun `M keep recoverable errors nonfatal W native failure and recovery`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val statuses = mutableListOf<OpenFeatureStatus>()
        val subscription = backgroundScope.launch(dispatcher) {
            OpenFeatureAPI.statusFlow.collect { statuses.add(it) }
        }
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Ready)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            initialization.await()

            fixture.state.update(FlagsClientState.Error(IllegalStateException("Network request failed")))
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getStatus()).isInstanceOf(OpenFeatureStatus.Error::class.java)
            assertThat((OpenFeatureAPI.getStatus() as OpenFeatureStatus.Error).error)
                .hasMessage("Network request failed")
            assertThat(statuses.filterIsInstance<OpenFeatureStatus.Fatal>()).isEmpty()

            fixture.state.update(FlagsClientState.Ready)
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getClient().getBooleanValue("flag", false)).isTrue()
        } finally {
            subscription.cancel()
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
        assertThat(fixture.state.listenerCount).isZero()
    }

    @Test
    fun `M keep initialization failure nonfatal W event precedes callback`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            val error = IllegalStateException("Network request failed")
            fixture.state.update(FlagsClientState.Error(error))
            testScheduler.runCurrent()
            // Inspect before lifecycle completion can overwrite the event-derived status.
            val eventStatus = OpenFeatureAPI.getStatus()
            fixture.callback.onFailure(error)
            testScheduler.runCurrent()
            initialization.await()
            assertThat(eventStatus).isInstanceOf(OpenFeatureStatus.Error::class.java)
            assertThat(OpenFeatureAPI.getStatus()).isInstanceOf(OpenFeatureStatus.Error::class.java)
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `M expose upstream Ready overwrite W cached timeout event precedes completion`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Stale)
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Stale)

            fixture.callback.onFailure(mock<FlagsInitializationTimeoutException>())
            testScheduler.runCurrent()
            initialization.await()

            // Spec v0.8 requires Ready after normal initialization. Kotlin package 0.8.0
            // follows that legacy model; this does not assert spec v0.9 event ownership.
            assertThat(fixture.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
            assertThat(OpenFeatureAPI.getClient().getBooleanValue("flag", false)).isTrue()
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `M expose upstream equal context suppression W repeated context setter`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Ready)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            initialization.await()

            OpenFeatureAPI.setEvaluationContextAndWait(ImmutableContext("user"))

            // #251 removes this equality guard, but is not in the released dependency.
            assertThat(fixture.contextCalls).isEqualTo(1)
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `M expose upstream Stale ordering W cached timeout completion precedes event collection`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val initialization = async(UnconfinedTestDispatcher(testScheduler)) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Stale)
            // Native state changes before its callback, but the event collector has not run yet.
            fixture.callback.onFailure(mock<FlagsInitializationTimeoutException>())
            initialization.await()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
            testScheduler.runCurrent()
            assertThat(fixture.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Stale)
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `M expose upstream Error overwrite W context refresh fails with usable flags`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Ready)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            initialization.await()

            val reconciliation = async(dispatcher) {
                OpenFeatureAPI.setEvaluationContextAndWait(ImmutableContext("other-user"))
            }
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Reconciling)
            fixture.state.update(FlagsClientState.Stale)
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Stale)
            fixture.callback.onFailure(IllegalStateException("Network request failed"))
            testScheduler.runCurrent()
            reconciliation.await()
            assertThat(fixture.state.getCurrentState()).isEqualTo(FlagsClientState.Stale)
            assertThat(OpenFeatureAPI.getStatus()).isInstanceOf(OpenFeatureStatus.Error::class.java)
            assertThat(OpenFeatureAPI.getClient().getBooleanValue("flag", false)).isTrue()
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `M preserve lifecycle status W configuration events in NotReady Ready and Stale`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        // Inject configuration events at the provider boundary; no native public event API is needed.
        val configurationEvents = MutableSharedFlow<OpenFeatureProviderEvents>()
        val eventProvider = object : FeatureProvider by fixture.provider {
            override fun observe() = merge(fixture.provider.observe(), configurationEvents)
        }
        val events = mutableListOf<OpenFeatureProviderEvents>()
        val subscription = backgroundScope.launch(dispatcher) {
            OpenFeatureAPI.observe<OpenFeatureProviderEvents>().collect { events.add(it) }
        }
        val event = OpenFeatureProviderEvents.ProviderConfigurationChanged(
            OpenFeatureProviderEvents.EventDetails(flagsChanged = setOf("flag"))
        )
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(eventProvider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            configurationEvents.emit(event)
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.NotReady)
            fixture.state.update(FlagsClientState.Ready)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            initialization.await()
            configurationEvents.emit(event)
            testScheduler.runCurrent()
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
            fixture.state.update(FlagsClientState.Stale)
            testScheduler.runCurrent()
            configurationEvents.emit(event)
            testScheduler.runCurrent()

            assertThat(events.filterIsInstance<OpenFeatureProviderEvents.ProviderConfigurationChanged>())
                .containsExactly(event, event, event)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Stale)
            assertThat(OpenFeatureAPI.getClient().statusFlow.first()).isEqualTo(OpenFeatureStatus.Stale)
        } finally {
            subscription.cancel()
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
        assertThat(fixture.state.listenerCount).isZero()
    }

    @Test
    fun `M replay current client status and observe reconciliation W late status subscription`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val statuses = mutableListOf<OpenFeatureStatus>()
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Ready)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            initialization.await()
            val client = OpenFeatureAPI.getClient()
            val subscription = backgroundScope.launch(dispatcher) {
                client.statusFlow.collect { statuses.add(it) }
            }
            testScheduler.runCurrent()
            val reconciliation = async(dispatcher) {
                OpenFeatureAPI.setEvaluationContextAndWait(ImmutableContext("other-user"))
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Ready)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            reconciliation.await()
            assertThat(statuses).containsExactly(
                OpenFeatureStatus.Ready,
                OpenFeatureStatus.Reconciling,
                OpenFeatureStatus.Ready
            )
            subscription.cancel()
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M keep Reconciling W superseded context callback arrives`(success: Boolean) = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(fixture.provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            fixture.state.update(FlagsClientState.Ready)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            initialization.await()

            OpenFeatureAPI.setEvaluationContext(ImmutableContext("first"), dispatcher)
            testScheduler.runCurrent()
            val supersededCallback = fixture.callback
            OpenFeatureAPI.setEvaluationContext(ImmutableContext("second"), dispatcher)
            testScheduler.runCurrent()
            if (success) {
                supersededCallback.onSuccess()
            } else {
                supersededCallback.onFailure(IllegalStateException("Obsolete request failed"))
            }
            testScheduler.runCurrent()
            val statusWhileSecondRequestPending = OpenFeatureAPI.getStatus()
            fixture.callback.onSuccess()
            testScheduler.runCurrent()

            assertThat(statusWhileSecondRequestPending).isEqualTo(OpenFeatureStatus.Reconciling)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M keep replacement NotReady W cancelled provider initialization callback arrives`(success: Boolean) = runTest {
        val first = Fixture()
        val replacement = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = OpenFeatureAPI.getClient()
        val statuses = mutableListOf<OpenFeatureStatus>()
        val subscription = backgroundScope.launch(dispatcher) {
            client.statusFlow.collect { statuses.add(it) }
        }
        try {
            OpenFeatureAPI.setProvider(first.provider, dispatcher, ImmutableContext("first"))
            testScheduler.runCurrent()
            OpenFeatureAPI.setProvider(replacement.provider, dispatcher, ImmutableContext("replacement"))
            testScheduler.runCurrent()
            assertThat(first.state.listenerCount).isZero()

            if (success) {
                first.callback.onSuccess()
            } else {
                first.callback.onFailure(IllegalStateException("Old provider failed"))
            }
            first.state.update(FlagsClientState.Stale)
            testScheduler.runCurrent()
            val statusWhileReplacementPending = OpenFeatureAPI.getStatus()
            replacement.state.update(FlagsClientState.Ready)
            replacement.callback.onSuccess()
            testScheduler.runCurrent()

            assertThat(OpenFeatureAPI.getProvider()).isSameAs(replacement.provider)
            assertThat(statusWhileReplacementPending).isEqualTo(OpenFeatureStatus.NotReady)
            assertThat(OpenFeatureAPI.getStatus()).isEqualTo(OpenFeatureStatus.Ready)
            assertThat(statuses).containsExactly(OpenFeatureStatus.NotReady, OpenFeatureStatus.Ready)
        } finally {
            subscription.cancel()
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
        assertThat(replacement.state.listenerCount).isZero()
    }

    @Test
    fun `M gate native resolution W NotReady and Fatal but permit Stale`() = runTest {
        val fixture = Fixture()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val injectedEvents = MutableSharedFlow<OpenFeatureProviderEvents>()
        val provider = object : FeatureProvider by fixture.provider {
            override fun observe() = merge(fixture.provider.observe(), injectedEvents)
        }
        try {
            val initialization = async(dispatcher) {
                OpenFeatureAPI.setProviderAndWait(provider, ImmutableContext("user"), dispatcher)
            }
            testScheduler.runCurrent()
            val client = OpenFeatureAPI.getClient()
            val notReady = client.getBooleanDetails("flag", false)
            assertThat(notReady.errorCode.toString()).isEqualTo("PROVIDER_NOT_READY")
            assertThat(notReady.value).isFalse()
            org.mockito.kotlin.verify(fixture.flagsClient, org.mockito.kotlin.never()).resolve("flag", false)
            fixture.callback.onSuccess()
            testScheduler.runCurrent()
            initialization.await()
            injectedEvents.emit(
                OpenFeatureProviderEvents.ProviderError(
                    OpenFeatureProviderEvents.EventDetails(
                        errorCode = dev.openfeature.kotlin.sdk.exceptions.ErrorCode.PROVIDER_FATAL
                    )
                )
            )
            testScheduler.runCurrent()
            val fatal = client.getBooleanDetails("flag", false)
            assertThat(fatal.errorCode.toString()).isEqualTo("PROVIDER_FATAL")
            assertThat(fatal.value).isFalse()
            org.mockito.kotlin.verify(fixture.flagsClient, org.mockito.kotlin.never()).resolve("flag", false)
            fixture.state.update(FlagsClientState.Stale)
            testScheduler.runCurrent()
            assertThat(client.getBooleanDetails("flag", false).value).isTrue()
            org.mockito.kotlin.verify(fixture.flagsClient).resolve("flag", false)
        } finally {
            OpenFeatureAPI.shutdown()
            testScheduler.runCurrent()
        }
    }

    private class Fixture {
        val state = TestStateObservable()
        val flagsClient = mock<FlagsClient>()
        lateinit var callback: EvaluationContextCallback
        var contextCalls = 0
        val provider: DatadogFlagsProvider

        init {
            val sdkCore = mock<FeatureSdkCore>()
            whenever(sdkCore.internalLogger).thenReturn(mock())
            whenever(flagsClient.state).thenReturn(state)
            whenever(flagsClient.setEvaluationContext(any(), any())).doAnswer {
                contextCalls++
                callback = it.getArgument(1)
                Unit
            }
            whenever(flagsClient.resolve("flag", false)).thenReturn(ResolutionDetails(value = true))
            provider = DatadogFlagsProvider.wrap(flagsClient, sdkCore)
        }
    }

    private class TestStateObservable : StateObservable {
        private var currentState: FlagsClientState = FlagsClientState.NotReady
        private val listeners = mutableSetOf<FlagsStateListener>()
        val listenerCount: Int get() = listeners.size

        override fun getCurrentState(): FlagsClientState = currentState

        override fun addListener(listener: FlagsStateListener) {
            listeners.add(listener)
            listener.onStateChanged(currentState)
        }

        override fun removeListener(listener: FlagsStateListener) {
            listeners.remove(listener)
        }

        fun update(state: FlagsClientState) {
            currentState = state
            listeners.toList().forEach { it.onStateChanged(state) }
        }
    }
}
