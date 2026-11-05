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
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.utils.WriteOperation
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
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

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeEvent: ErrorEvent

    private var isBatchWriteSuccessful = true
    private var readCallback: DataStoreReadCallback<ErrorEvent>? = null

    // The data store's single stored error, if any.
    private val disk = mutableListOf<ErrorEvent>()

    // Callbacks of the stored error writes, completed by the test like the data store's thread would.
    private val storeWriteCallbacks = mutableListOf<DataStoreWriteCallback?>()

    @BeforeEach
    fun `set up`(@StringForgery fakeErrorId: String) {
        fakeEvent = fakeEvent.copy(error = fakeEvent.error.copy(id = fakeErrorId))
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingScope
        val writeScope: EventWriteScope = { it(mockBatchWriter) }
        whenever(mockRumScope.withWriteContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1).invoke(mock(), writeScope)
        }
        whenever(mockWriter.write(any(), any(), any())) doAnswer { isBatchWriteSuccessful }
        // The test completes the load read; later reads return the disk content.
        whenever(mockDataStore.value<ErrorEvent>(any(), anyOrNull(), any(), any())) doAnswer {
            val callback = it.getArgument<DataStoreReadCallback<ErrorEvent>>(2)
            if (readCallback == null) {
                readCallback = callback
            } else {
                callback.onSuccess(
                    disk.firstOrNull()?.let { event ->
                        DataStoreContent(PendingOomErrorStore.VERSION, event)
                    }
                )
            }
        }
        whenever(mockDataStore.setValue(any(), any<ErrorEvent>(), any(), anyOrNull(), any())) doAnswer {
            disk.clear()
            disk.add(it.getArgument(1))
            storeWriteCallbacks.add(it.getArgument(3))
            Unit
        }
        whenever(mockDataStore.removeValue(any(), anyOrNull())) doAnswer {
            disk.clear()
            Unit
        }
        testedStore = PendingOomErrorStore(mockSdkCore, mockHandler) { mockWriter }
    }

    // region tryHold

    @Test
    fun `M not hold W tryHold() {not loaded}`() {
        // Given
        testedStore.start(mockDataStore)

        // When
        val result = testedStore.tryHold(fakeEvent)

        // Then
        assertThat(result).isFalse
        verify(mockDataStore, never()).setValue(any(), any<ErrorEvent>(), any(), anyOrNull(), any())
    }

    @Test
    fun `M persist and hold W tryHold() {loaded, no error stored}`() {
        // Given
        load(null)

        // When
        val result = testedStore.tryHold(fakeEvent)

        // Then
        assertThat(result).isTrue
        verify(mockDataStore).setValue(
            eq(PendingOomErrorStore.KEY),
            eq(fakeEvent),
            eq(PendingOomErrorStore.VERSION),
            anyOrNull(),
            any()
        )
        verify(mockWriter, never()).write(any(), any(), any())
        verify(mockProfilingScope, never()).sendEvent(any())
    }

    @Test
    fun `M hold the newest and write the older one without status W tryHold() {loaded error awaiting its profile}`(
        @Forgery fakeNewEvent: ErrorEvent
    ) {
        // Given
        load(fakeEvent)

        // When
        val result = testedStore.tryHold(fakeNewEvent)
        testedStore.onOomProfileWriteResult(errorId, true)

        // Then
        assertThat(result).isTrue
        verify(mockDataStore).setValue(
            eq(PendingOomErrorStore.KEY),
            eq(fakeNewEvent),
            eq(PendingOomErrorStore.VERSION),
            anyOrNull(),
            any()
        )
        val written = writtenEvent()
        assertThat(written.error.id).isEqualTo(errorId)
        assertThat(written.dd.profiling).isNull()
        assertThat(disk).containsExactly(fakeNewEvent)
    }

    @Test
    fun `M hold the newest and write the older one W tryHold() {held in this process}`(
        @Forgery fakeNewEvent: ErrorEvent
    ) {
        // Given
        load(null)
        testedStore.tryHold(fakeEvent)

        // When
        val result = testedStore.tryHold(fakeNewEvent)

        // Then
        assertThat(result).isTrue
        assertThat(writtenEvent().dd.profiling).isNull()
        verify(mockDataStore, never()).removeValue(any(), anyOrNull())
    }

    @Test
    fun `M write it right away without status W tryHold() {storing the error fails}`(
        @Forgery fakeNewEvent: ErrorEvent
    ) {
        // Given
        load(null)
        testedStore.tryHold(fakeEvent)
        disk.clear()

        // When
        checkNotNull(storeWriteCallbacks.single()).onFailure()

        // Then
        val written = writtenEvent()
        assertThat(written.error.id).isEqualTo(errorId)
        assertThat(written.dd.profiling).isNull()
        // No longer stored: a new hold doesn't write it again as the older error.
        clearInvocations(mockWriter)
        assertThat(testedStore.tryHold(fakeNewEvent)).isTrue
        verify(mockWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M write it once W tryHold() {storing fails, newer hold before the fallback write runs}`(
        @Forgery fakeNewEvent: ErrorEvent
    ) {
        // Given
        val deferredWrites = deferWriteScopes()
        load(null)
        testedStore.tryHold(fakeEvent)
        checkNotNull(storeWriteCallbacks.single()).onFailure()

        // When
        testedStore.tryHold(fakeNewEvent)
        deferredWrites.forEach { it() }

        // Then
        val written = writtenEvent()
        assertThat(written.error.id).isEqualTo(errorId)
        assertThat(written.dd.profiling).isNull()
        assertThat(disk).containsExactly(fakeNewEvent)
    }

    @Test
    fun `M not write it W tryHold() {storing the error succeeds}`() {
        // Given
        load(null)
        testedStore.tryHold(fakeEvent)

        // When
        checkNotNull(storeWriteCallbacks.single()).onSuccess()

        // Then
        verify(mockWriter, never()).write(any(), any(), any())
        assertThat(disk).containsExactly(fakeEvent)
    }

    @Test
    fun `M not write it twice W tryHold() {storing fails after a newer hold replaced it}`(
        @Forgery fakeNewEvent: ErrorEvent
    ) {
        // Given
        load(null)
        testedStore.tryHold(fakeEvent)
        testedStore.tryHold(fakeNewEvent)

        // When
        checkNotNull(storeWriteCallbacks.first()).onFailure()

        // Then
        writtenEvent()
    }

    @Test
    fun `M hold W tryHold() {load failed}`() {
        // Given
        testedStore.start(mockDataStore)
        readCallback?.onFailure()

        // When
        val result = testedStore.tryHold(fakeEvent)

        // Then
        assertThat(result).isTrue
    }

    @Test
    fun `M log and not throw W onOomProfileWriteResult() {writer throws}`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val fakeException = IllegalStateException(fakeMessage)
        whenever(mockWriter.write(any(), any(), any())) doThrow fakeException
        load(fakeEvent)

        // When
        testedStore.onOomProfileWriteResult(errorId, true)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            WriteOperation.WRITE_OPERATION_FAILED_ERROR,
            fakeException
        )
    }

    // endregion

    // region next launch

    @Test
    fun `M remove it from disk, announce and schedule timeout W start() {record loaded}`() {
        // When
        load(fakeEvent)

        // Then
        verify(mockDataStore).removeValue(eq(PendingOomErrorStore.KEY), anyOrNull())
        assertThat(disk).isEmpty()
        verify(mockWriter, never()).write(any(), any(), any())
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
        verify(mockHandler).postDelayed(any(), eq(PendingOomErrorStore.TIMEOUT_MS))
    }

    @Test
    fun `M not touch the disk W start() {nothing stored}`() {
        // When
        load(null)

        // Then
        verify(mockDataStore, never()).removeValue(any(), anyOrNull())
        verify(mockHandler, never()).postDelayed(any(), any())
    }

    @Test
    fun `M announce again only while awaiting its profile W onProfilingReady()`() {
        // Given
        load(fakeEvent)

        // When
        testedStore.onProfilingReady()
        testedStore.onOomProfileWriteResult(errorId, true)
        testedStore.onProfilingReady()

        // Then
        verify(mockProfilingScope, times(2)).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M write with matching status W onOomProfileWriteResult()`(isWritten: Boolean) {
        // Given
        load(fakeEvent)

        // When
        testedStore.onOomProfileWriteResult(errorId, isWritten)

        // Then
        val expected = if (isWritten) ErrorEvent.ProfilingStatus.RUNNING else ErrorEvent.ProfilingStatus.STOPPED
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(expected)
        assertThat(writtenEvent().date).isEqualTo(fakeEvent.date)
    }

    @Test
    fun `M ignore W onOomProfileWriteResult() {other error, then second profile write result}`(
        @StringForgery fakeOtherId: String
    ) {
        // Given
        load(fakeEvent)

        // When
        testedStore.onOomProfileWriteResult(fakeOtherId, true)
        testedStore.onOomProfileWriteResult(errorId, false)
        testedStore.onOomProfileWriteResult(errorId, true)

        // Then
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.STOPPED)
    }

    @Test
    fun `M cancel the timeout W onOomProfileWriteResult()`() {
        // Given
        load(fakeEvent)

        // When
        testedStore.onOomProfileWriteResult(errorId, true)

        // Then
        verifyTimeoutCancelled()
    }

    @Test
    fun `M cancel the timeout W tryHold() {loaded error awaiting its profile}`(
        @Forgery fakeNewEvent: ErrorEvent
    ) {
        // Given
        load(fakeEvent)

        // When
        testedStore.tryHold(fakeNewEvent)

        // Then
        verifyTimeoutCancelled()
    }

    @Test
    fun `M write without profiling and ignore later profile write result W timeout`() {
        // Given
        load(fakeEvent)

        // When
        runTimeout()
        testedStore.onOomProfileWriteResult(errorId, true)

        // Then
        assertThat(writtenEvent().dd.profiling).isNull()
    }

    @Test
    fun `M not retry it on a later launch W onOomProfileWriteResult() {batch write fails}`() {
        // Given
        isBatchWriteSuccessful = false
        load(fakeEvent)

        // When
        testedStore.onOomProfileWriteResult(errorId, true)

        // Then
        assertThat(disk).isEmpty()
        clearInvocations(mockProfilingScope)
        testedStore = PendingOomErrorStore(mockSdkCore, mock()) { mockWriter }
        readCallback = null
        testedStore.start(mockDataStore)
        readCallback?.onSuccess(null)
        verify(mockProfilingScope, never()).sendEvent(any())
    }

    @Test
    fun `M not write it again W tryHold() {loaded record already written}`(@Forgery fakeNewEvent: ErrorEvent) {
        // Given
        load(fakeEvent)
        testedStore.onOomProfileWriteResult(errorId, true)

        // When
        testedStore.tryHold(fakeNewEvent)

        // Then
        writtenEvent()
    }

    // endregion

    @Test
    fun `M keep id, date and attributes W serialize then deserialize`(
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String,
        @StringForgery fakeKnownValue: String
    ) {
        // Given
        val knownAttribute = RumEventSerializer.knownAttributes.first()
        val event = fakeEvent.copy(
            context = ErrorEvent.Context(mutableMapOf(fakeKey to fakeValue, knownAttribute to fakeKnownValue))
        )

        // When
        val serialized = PendingOomErrorStore.ErrorEventSerializer(mockInternalLogger).serialize(event)
        val result = PendingOomErrorStore.ErrorEventDeserializer().deserialize(checkNotNull(serialized))

        // Then
        checkNotNull(result)
        assertThat(result.error.id).isEqualTo(errorId)
        assertThat(result.date).isEqualTo(event.date)
        assertThat(result.view.id).isEqualTo(event.view.id)
        assertThat(result.context?.additionalProperties)
            .containsEntry(fakeKey, JsonPrimitive(fakeValue))
            .containsEntry(knownAttribute, JsonPrimitive(fakeKnownValue))
    }

    @Test
    fun `M omit the attribute W serialize() {attribute toString throws}`(
        @StringForgery fakeKey: String,
        @StringForgery fakeMessage: String
    ) {
        // Given
        val throwingValue = object {
            override fun toString(): String = error(fakeMessage)
        }
        val event = fakeEvent.copy(context = ErrorEvent.Context(mutableMapOf(fakeKey to throwingValue)))

        // When
        val serialized = PendingOomErrorStore.ErrorEventSerializer(mockInternalLogger).serialize(event)

        // Then
        checkNotNull(serialized)
        assertThat(ErrorEvent.fromJson(serialized).context?.additionalProperties.orEmpty()).doesNotContainKey(fakeKey)
    }

    @Test
    fun `M return null and log W serialize() {serialization fails}`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val fakeException = IllegalStateException(fakeMessage)
        val mockDelegate = mock<Serializer<Any>>()
        whenever(mockDelegate.serialize(any())) doThrow fakeException

        // When
        val serialized = PendingOomErrorStore.ErrorEventSerializer(
            mockInternalLogger,
            mockDelegate
        ).serialize(fakeEvent)

        // Then
        assertThat(serialized).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            PendingOomErrorStore.LOG_SERIALIZATION_FAILED,
            fakeException
        )
    }

    @Test
    fun `M return null and log W serialize() {serialization runs out of memory}`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val fakeError = OutOfMemoryError(fakeMessage)
        val mockDelegate = mock<Serializer<Any>>()
        whenever(mockDelegate.serialize(any())) doThrow fakeError

        // When
        val serialized = PendingOomErrorStore.ErrorEventSerializer(
            mockInternalLogger,
            mockDelegate
        ).serialize(fakeEvent)

        // Then
        assertThat(serialized).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            PendingOomErrorStore.LOG_SERIALIZATION_FAILED,
            fakeError
        )
    }

    @Test
    fun `M return null W deserialize() {corrupted}`(@StringForgery fakeGarbage: String) {
        // When
        val result = PendingOomErrorStore.ErrorEventDeserializer().deserialize("{$fakeGarbage")

        // Then
        assertThat(result).isNull()
    }

    private fun deferWriteScopes(): MutableList<() -> Unit> {
        val deferred = mutableListOf<() -> Unit>()
        whenever(mockRumScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            deferred.add { callback.invoke(mock(), { block -> block(mockBatchWriter) }) }
            Unit
        }
        return deferred
    }

    private val errorId: String
        get() = fakeEvent.error.id.orEmpty()

    private fun load(record: ErrorEvent?) {
        disk.clear()
        record?.let { disk.add(it) }
        testedStore.start(mockDataStore)
        readCallback?.onSuccess(record?.let { DataStoreContent(PendingOomErrorStore.VERSION, it) })
    }

    /** The posted timeout is removed: running it would write nothing. */
    private fun verifyTimeoutCancelled() {
        argumentCaptor<Runnable> {
            verify(mockHandler).postDelayed(capture(), eq(PendingOomErrorStore.TIMEOUT_MS))
            verify(mockHandler).removeCallbacks(firstValue)
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
