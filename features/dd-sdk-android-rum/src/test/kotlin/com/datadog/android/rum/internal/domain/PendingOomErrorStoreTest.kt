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
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.domain.PendingOomErrorStore.PendingOomError
import com.datadog.android.rum.internal.domain.PendingOomErrorStore.State
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

    @Mock
    lateinit var stubTimeProvider: TimeProvider

    @Forgery
    lateinit var fakeEvent: ErrorEvent

    @LongForgery(min = 1_000_000L, max = 1_000_000_000L)
    var fakeNowMs: Long = 0L

    private var isStoreWriteSuccessful = true
    private var isBatchWriteSuccessful = true
    private val storedValues = mutableListOf<PendingOomError>()
    private var readCallback: DataStoreReadCallback<PendingOomError>? = null

    @BeforeEach
    fun `set up`(@StringForgery fakeErrorId: String) {
        fakeEvent = fakeEvent.copy(error = fakeEvent.error.copy(id = fakeErrorId))
        whenever(mockSdkCore.internalLogger) doReturn mock<InternalLogger>()
        whenever(mockSdkCore.timeProvider) doReturn stubTimeProvider
        whenever(stubTimeProvider.getDeviceTimestampMillis()) doReturn fakeNowMs
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingScope
        val writeScope: EventWriteScope = { it(mockBatchWriter) }
        whenever(mockRumScope.withWriteContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1).invoke(mock(), writeScope)
        }
        whenever(mockWriter.write(any(), any(), any())) doAnswer { isBatchWriteSuccessful }
        whenever(mockDataStore.setValue(any(), any<PendingOomError>(), any(), anyOrNull(), any())) doAnswer {
            storedValues.add(it.getArgument(1))
            val callback = it.getArgument<DataStoreWriteCallback?>(3)
            if (isStoreWriteSuccessful) callback?.onSuccess() else callback?.onFailure()
        }
        whenever(mockDataStore.value<PendingOomError>(any(), anyOrNull(), any(), any())) doAnswer {
            readCallback = it.getArgument(2)
        }
        testedStore = PendingOomErrorStore(mockSdkCore, mockHandler) { mockWriter }
    }

    @Test
    fun `M grant after storing reservation and write RUNNING W attach then ATTACHED {reloaded pending}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING, fakeNowMs))
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))

        // When
        testedStore.onAttachRequest(errorId)
        testedStore.onResolution(errorId, Outcome.ATTACHED)

        // Then
        assertThat(storedValues.map { it.state }).containsExactly(State.RESERVED, State.RESOLVED_ATTACHED)
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, true))
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileResolutionAck(errorId))
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.RUNNING)
        verify(mockDataStore).removeValue(PendingOomErrorStore.KEY, null)
    }

    @Test
    fun `M write STOPPED W resolution NOT_ATTACHED {pending}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING, fakeNowMs))

        // When
        testedStore.onResolution(errorId, Outcome.NOT_ATTACHED)

        // Then
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.STOPPED)
    }

    @Test
    fun `M deny W attach request {storing reservation fails}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING, fakeNowMs))
        isStoreWriteSuccessful = false

        // When
        testedStore.onAttachRequest(errorId)

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, false))
    }

    @Test
    fun `M deny W attach request {other error}`(@StringForgery fakeOtherId: String) {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING, fakeNowMs))

        // When
        testedStore.onAttachRequest(fakeOtherId)

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(fakeOtherId, false))
        assertThat(storedValues).isEmpty()
    }

    @Test
    fun `M answer queued request after load W attach request {while loading}`() {
        // Given
        testedStore.start(mockDataStore)
        testedStore.onAttachRequest(errorId)
        verify(mockProfilingScope, never()).sendEvent(any())

        // When
        readCallback?.onSuccess(
            DataStoreContent(PendingOomErrorStore.VERSION, PendingOomError(fakeEvent, State.PENDING, fakeNowMs))
        )

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, true))
    }

    @Test
    fun `M write without profiling and deny later request W timeout {pending}`() {
        // Given
        load(PendingOomError(fakeEvent, State.PENDING, fakeNowMs))

        // When
        runTimeout()
        testedStore.onAttachRequest(errorId)

        // Then
        assertThat(writtenEvent().dd.profiling).isNull()
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumOomProfileAttachReply(errorId, false))
    }

    @Test
    fun `M keep waiting W timeout {reserved}`() {
        // Given
        load(PendingOomError(fakeEvent, State.RESERVED, fakeNowMs))

        // When
        runTimeout()

        // Then
        verify(mockWriter, never()).write(any(), any(), any())
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
    }

    @Test
    fun `M write STOPPED W load {reserved older than max age}`() {
        // When
        load(PendingOomError(fakeEvent, State.RESERVED, fakeNowMs - PendingOomErrorStore.RESERVED_MAX_AGE_MS))

        // Then
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.STOPPED)
    }

    @Test
    fun `M write stored status without asking profiling W load {resolved}`() {
        // When
        load(PendingOomError(fakeEvent, State.RESOLVED_ATTACHED, fakeNowMs))

        // Then
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.RUNNING)
        verify(mockProfilingScope, never()).sendEvent(any())
    }

    @Test
    fun `M keep record W load {resolved, batch write fails}`() {
        // Given
        isBatchWriteSuccessful = false

        // When
        load(PendingOomError(fakeEvent, State.RESOLVED_NOT_ATTACHED, fakeNowMs))

        // Then
        verify(mockDataStore, never()).removeValue(any(), anyOrNull())
    }

    @Test
    fun `M flush older error with its outcome and store new one W hold() {crash loop, reserved and attached}`(
        @Forgery fakeNewEvent: ErrorEvent
    ) {
        // Given
        assertThat(fakeNewEvent.error.id).isNotEqualTo(errorId)
        load(PendingOomError(fakeEvent, State.RESERVED, fakeNowMs))

        // When
        testedStore.hold(fakeNewEvent, attachedErrorId = errorId, batchWriter = mockBatchWriter)

        // Then
        assertThat(writtenEvent().error.id).isEqualTo(errorId)
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.RUNNING)
        assertThat(storedValues.single().event).isEqualTo(fakeNewEvent)
        assertThat(storedValues.single().state).isEqualTo(State.PENDING)
    }

    @Test
    fun `M keep event fields W serialize then deserialize`() {
        // Given
        val record = PendingOomError(fakeEvent, State.RESERVED, fakeNowMs)

        // When
        val serialized = PendingOomErrorStore.PendingOomErrorSerializer().serialize(record)
        val result = PendingOomErrorStore.PendingOomErrorDeserializer().deserialize(serialized)

        // Then
        checkNotNull(result)
        assertThat(result.state).isEqualTo(State.RESERVED)
        assertThat(result.heldAtMs).isEqualTo(fakeNowMs)
        assertThat(result.event.date).isEqualTo(fakeEvent.date)
        assertThat(result.event.session.id).isEqualTo(fakeEvent.session.id)
        assertThat(result.event.view.id).isEqualTo(fakeEvent.view.id)
        assertThat(result.event.error.id).isEqualTo(fakeEvent.error.id)
    }

    private val errorId: String
        get() = fakeEvent.error.id.orEmpty()

    private fun load(record: PendingOomError?) {
        testedStore.start(mockDataStore)
        readCallback?.onSuccess(record?.let { DataStoreContent(PendingOomErrorStore.VERSION, it) })
    }

    private fun runTimeout() {
        argumentCaptor<Runnable> {
            verify(mockHandler).postDelayed(capture(), eq(PendingOomErrorStore.TIMEOUT_MS))
            firstValue.run()
        }
    }

    private fun writtenEvent(): ErrorEvent {
        val captor = argumentCaptor<Any>()
        verify(mockWriter).write(eq(mockBatchWriter), captor.capture(), eq(EventType.CRASH))
        return captor.firstValue as ErrorEvent
    }
}
