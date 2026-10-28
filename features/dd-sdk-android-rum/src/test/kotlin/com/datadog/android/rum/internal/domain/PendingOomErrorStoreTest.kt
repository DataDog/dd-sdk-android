/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.os.Handler
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilerEvent.OomProfileResolution.Outcome
import com.datadog.android.rum.internal.domain.PendingOomErrorStore.PendingOomError
import com.datadog.android.rum.internal.domain.PendingOomErrorStore.State
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PendingOomErrorStoreTest {

    private lateinit var testedStore: PendingOomErrorStore

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockRumScope: FeatureScope

    @Mock
    lateinit var mockProfilingScope: FeatureScope

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockDataStore: DataStoreHandler

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeEvent: ErrorEvent

    private var isBatchWriteSuccessful = true

    // The data store's content, and its writes waiting for their callback, in order.
    private var storedValue: PendingOomError? = null
    private val pendingStoreWrites = mutableListOf<Pair<PendingOomError, DataStoreWriteCallback?>>()
    private var readCallback: DataStoreReadCallback<PendingOomError>? = null

    @BeforeEach
    fun `set up`(@StringForgery fakeErrorId: String) {
        fakeEvent = fakeEvent.copy(error = fakeEvent.error.copy(id = fakeErrorId))
        whenever(mockSdkCore.internalLogger) doReturn mock<InternalLogger>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingScope
        stubWriteContextRuns()
        whenever(mockWriter.write(any(), any(), any())) doAnswer { isBatchWriteSuccessful }
        whenever(mockDataStore.setValue(any(), any<PendingOomError>(), any(), anyOrNull(), any())) doAnswer {
            pendingStoreWrites.add(it.getArgument<PendingOomError>(1) to it.getArgument(3))
            Unit
        }
        whenever(mockDataStore.removeValue(any(), anyOrNull())) doAnswer {
            storedValue = null
        }
        whenever(mockDataStore.value<PendingOomError>(any(), anyOrNull(), any(), any())) doAnswer {
            readCallback = it.getArgument(2)
        }
        testedStore = newStore()
    }

    // region normal flow

    @Test
    fun `M grant only once reservation is persisted W onAttachRequest() {pending}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))

        // When
        testedStore.onAttachRequest(errorId)

        // Then
        verify(mockProfilingScope, never()).sendEvent(isA<ProfilerEvent.RumOomProfileAttachReply>())
        completeStoreWrites(isSuccessful = true)
        assertThat(storedValue?.state).isEqualTo(State.RESERVED)
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, true))
    }

    @Test
    fun `M ack and write RUNNING only once outcome is persisted W onResolution(ATTACHED) {reserved}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED))

        // When
        testedStore.onResolution(errorId, Outcome.ATTACHED)

        // Then
        verify(mockProfilingScope, never()).sendEvent(isA<ProfilerEvent.RumOomProfileResolutionAck>())
        verify(mockWriter, never()).write(any(), any(), any())
        completeStoreWrites(isSuccessful = true)
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileResolutionAck(errorId))
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.RUNNING)
        assertThat(storedValue).isNull()
    }

    @Test
    fun `M write STOPPED W onResolution(NOT_ATTACHED) {pending}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))

        // When
        testedStore.onResolution(errorId, Outcome.NOT_ATTACHED)
        completeStoreWrites(isSuccessful = true)

        // Then
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.STOPPED)
    }

    @Test
    fun `M grant again and write once W repeated request and repeated resolution`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))
        testedStore.onAttachRequest(errorId)
        completeStoreWrites(isSuccessful = true)

        // When
        testedStore.onAttachRequest(errorId)
        testedStore.onResolution(errorId, Outcome.ATTACHED)
        completeStoreWrites(isSuccessful = true)
        testedStore.onResolution(errorId, Outcome.ATTACHED)

        // Then
        verify(mockProfilingScope, times(2)).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, true))
        verify(mockProfilingScope, times(2)).sendEvent(ProfilerEvent.RumOomProfileResolutionAck(errorId))
        verify(mockWriter).write(any(), any(), any())
    }

    @Test
    fun `M deny W onAttachRequest() {other error}`(@StringForgery fakeOtherId: String) {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))

        // When
        testedStore.onAttachRequest(fakeOtherId)

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(fakeOtherId, false))
        assertThat(pendingStoreWrites).isEmpty()
    }

    @Test
    fun `M not ack duplicate W onResolution() twice {first outcome still being persisted}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED))
        testedStore.onResolution(errorId, Outcome.ATTACHED)

        // When
        testedStore.onResolution(errorId, Outcome.ATTACHED)

        // Then
        verify(mockProfilingScope, never()).sendEvent(isA<ProfilerEvent.RumOomProfileResolutionAck>())
        completeStoreWrites(isSuccessful = true)
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileResolutionAck(errorId))
    }

    @Test
    fun `M never ack and stay reserved W restart {duplicate outcome, first persistence fails}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED))
        testedStore.onResolution(errorId, Outcome.ATTACHED)
        testedStore.onResolution(errorId, Outcome.ATTACHED)

        // When
        completeStoreWrites(isSuccessful = false)
        restart()

        // Then
        verify(mockProfilingScope, never()).sendEvent(isA<ProfilerEvent.RumOomProfileResolutionAck>())
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
        assertThat(storedValue?.state).isEqualTo(State.RESERVED)
    }

    // endregion

    // region persistence failures

    @Test
    fun `M deny and stay pending W onAttachRequest() {reservation not persisted}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))
        testedStore.onAttachRequest(errorId)

        // When
        completeStoreWrites(isSuccessful = false)
        runTimeout()
        completeStoreWrites(isSuccessful = true)

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, false))
        assertThat(writtenEvent().dd.profiling).isNull()
    }

    @Test
    fun `M not ack nor write W onResolution() {outcome not persisted}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED))

        // When
        testedStore.onResolution(errorId, Outcome.ATTACHED)
        completeStoreWrites(isSuccessful = false)
        testedStore.onResolution(errorId, Outcome.ATTACHED)
        completeStoreWrites(isSuccessful = true)

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileResolutionAck(errorId))
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.RUNNING)
    }

    @Test
    fun `M keep record W write() {batch write fails}`() {
        // Given
        isBatchWriteSuccessful = false

        // When
        load(PendingOomError(fakeEvent, State.RESOLVED_ATTACHED))

        // Then
        verify(mockDataStore, never()).removeValue(any(), anyOrNull())
        assertThat(storedValue?.state).isEqualTo(State.RESOLVED_ATTACHED)
    }

    // endregion

    // region timeout

    @Test
    fun `M persist no-profile decision before writing W timeout {pending}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))

        // When
        runTimeout()

        // Then
        verify(mockWriter, never()).write(any(), any(), any())
        testedStore.onAttachRequest(errorId)
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, false))
        completeStoreWrites(isSuccessful = true)
        assertThat(writtenEvent().dd.profiling).isNull()
    }

    @Test
    fun `M write without profiling and never grant W restart {timed out, died before write}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))
        runTimeout()
        completeStoreWrites(isSuccessful = true, writeScopeRuns = false)

        // When
        restart()
        testedStore.onAttachRequest(errorId)

        // Then
        assertThat(writtenEvent().dd.profiling).isNull()
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, false))
        verify(mockProfilingScope, never()).sendEvent(isA<ProfilerEvent.RumHeldOomErrorEvent>())
    }

    @Test
    fun `M keep waiting W timeout {reserved}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED))

        // When
        runTimeout()

        // Then
        assertThat(pendingStoreWrites).isEmpty()
        verify(mockWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M write without profiling once loaded W timeout {before load, pending}`() {
        // Given
        testedStore.start(mockDataStore)
        runTimeout()

        // When
        readCallback?.onSuccess(
            DataStoreContent(PendingOomErrorStore.VERSION, PendingOomError(fakeEvent, State.PENDING))
        )
        completeStoreWrites(isSuccessful = true)

        // Then
        assertThat(writtenEvent().dd.profiling).isNull()
    }

    // endregion

    // region restart

    @Test
    fun `M announce again W restart {reserved}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING))
        testedStore.onAttachRequest(errorId)
        completeStoreWrites(isSuccessful = true)

        // When
        restart()

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
        verify(mockWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M write stored status without asking profiling W restart {resolved, died before write}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED))
        testedStore.onResolution(errorId, Outcome.ATTACHED)
        completeStoreWrites(isSuccessful = true, writeScopeRuns = false)

        // When
        restart()

        // Then
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.RUNNING)
    }

    // endregion

    // region readiness

    @Test
    fun `M answer after load W onAttachRequest() {before start}`() {
        // Given
        testedStore.onAttachRequest(errorId)

        // When
        load(PendingOomError(fakeEvent, State.PENDING))
        verify(mockProfilingScope, never()).sendEvent(isA<ProfilerEvent.RumOomProfileAttachReply>())
        completeStoreWrites(isSuccessful = true)

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, true))
    }

    @Test
    fun `M announce again W onProfilingReady() {held error not settled}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED))

        // When
        testedStore.onProfilingReady()

        // Then
        verify(mockProfilingScope, times(2)).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
    }

    // endregion

    // region tryHold

    @Test
    fun `M store pending W tryHold() {empty slot}`() {
        // Given
        load(null)

        // When
        val isHeld = testedStore.tryHold(fakeEvent)

        // Then
        assertThat(isHeld).isTrue
        assertThat(pendingStoreWrites.single().first.state).isEqualTo(State.PENDING)
    }

    @Test
    fun `M not hold W tryHold() {not loaded}`() {
        // When
        val isHeld = testedStore.tryHold(fakeEvent)

        // Then
        assertThat(isHeld).isFalse
    }

    @Test
    fun `M not hold and keep older error W tryHold() {crash loop}`(@Forgery fakeNewEvent: ErrorEvent) {
        // Given
        isBatchWriteSuccessful = false
        load(PendingOomError(fakeEvent, State.RESOLVED_ATTACHED))

        // When
        val isHeld = testedStore.tryHold(fakeNewEvent)

        // Then
        assertThat(isHeld).isFalse
        assertThat(pendingStoreWrites).isEmpty()
        assertThat(storedValue?.event).isEqualTo(fakeEvent)
    }

    // endregion

    @Test
    fun `M keep event and state W serialize then deserialize`() {
        // Given
        val record = PendingOomError(fakeEvent, State.RESOLVED_NO_PROFILE)

        // When
        val serialized = PendingOomErrorStore.PendingOomErrorSerializer().serialize(record)
        val result = PendingOomErrorStore.PendingOomErrorDeserializer().deserialize(serialized)

        // Then
        checkNotNull(result)
        assertThat(result.state).isEqualTo(State.RESOLVED_NO_PROFILE)
        assertThat(result.event.date).isEqualTo(fakeEvent.date)
        assertThat(result.event.session.id).isEqualTo(fakeEvent.session.id)
        assertThat(result.event.view.id).isEqualTo(fakeEvent.view.id)
        assertThat(result.errorId).isEqualTo(errorId)
    }

    private val errorId: String
        get() = fakeEvent.error.id.orEmpty()

    private fun stubWriteContextRuns() {
        val writeScope: EventWriteScope = { it(mockBatchWriter) }
        whenever(mockRumScope.withWriteContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1).invoke(mock(), writeScope)
        }
    }

    private fun newStore() = PendingOomErrorStore(mockSdkCore, mockHandler) { mockWriter }

    private fun load(record: PendingOomError?) {
        storedValue = record
        testedStore.start(mockDataStore)
        readCallback?.onSuccess(record?.let { DataStoreContent(PendingOomErrorStore.VERSION, it) })
    }

    /** Simulates a process death: a new store reloads what is on disk. */
    private fun restart() {
        pendingStoreWrites.clear()
        clearInvocations(mockHandler, mockProfilingScope)
        stubWriteContextRuns()
        testedStore = newStore()
        load(storedValue)
    }

    private fun completeStoreWrites(isSuccessful: Boolean, writeScopeRuns: Boolean = true) {
        if (!writeScopeRuns) {
            whenever(mockRumScope.withWriteContext(any(), any())) doAnswer {}
        }
        val writes = pendingStoreWrites.toList()
        pendingStoreWrites.clear()
        writes.forEach { (value, callback) ->
            if (isSuccessful) {
                storedValue = value
                callback?.onSuccess()
            } else {
                callback?.onFailure()
            }
        }
    }

    private fun runTimeout() {
        argumentCaptor<Runnable> {
            verify(mockHandler).postDelayed(capture(), eq(PendingOomErrorStore.TIMEOUT_MS))
            lastValue.run()
        }
    }

    private fun writtenEvent(): ErrorEvent {
        val captor = argumentCaptor<Any>()
        verify(mockWriter).write(eq(mockBatchWriter), captor.capture(), eq(EventType.CRASH))
        return captor.firstValue as ErrorEvent
    }
}
