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
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.internal.profiling.ProfilerEvent
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
    private var readCallback: DataStoreReadCallback<ErrorEvent>? = null

    @BeforeEach
    fun `set up`(@StringForgery fakeErrorId: String) {
        fakeEvent = fakeEvent.copy(error = fakeEvent.error.copy(id = fakeErrorId))
        whenever(mockSdkCore.internalLogger) doReturn mock<InternalLogger>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumScope
        whenever(mockSdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)) doReturn mockProfilingScope
        val writeScope: EventWriteScope = { it(mockBatchWriter) }
        whenever(mockRumScope.withWriteContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1).invoke(mock(), writeScope)
        }
        whenever(mockWriter.write(any(), any(), any())) doAnswer { isBatchWriteSuccessful }
        whenever(mockDataStore.value<ErrorEvent>(any(), anyOrNull(), any(), any())) doAnswer {
            readCallback = it.getArgument(2)
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
    fun `M persist and hold once W tryHold() {loaded empty slot}`(@Forgery fakeOtherEvent: ErrorEvent) {
        // Given
        load(null)

        // When
        val first = testedStore.tryHold(fakeEvent)
        val second = testedStore.tryHold(fakeOtherEvent)

        // Then
        assertThat(first).isTrue
        assertThat(second).isFalse
        verify(mockDataStore).setValue(
            eq(PendingOomErrorStore.KEY),
            eq(fakeEvent),
            eq(PendingOomErrorStore.VERSION),
            anyOrNull(),
            any()
        )
        verify(mockProfilingScope, never()).sendEvent(any())
    }

    @Test
    fun `M not hold W tryHold() {record loaded from a previous process}`(@Forgery fakeNewEvent: ErrorEvent) {
        // Given
        load(fakeEvent)

        // When
        val result = testedStore.tryHold(fakeNewEvent)

        // Then
        assertThat(result).isFalse
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

    // endregion

    // region next launch

    @Test
    fun `M announce and schedule timeout W start() {record loaded}`() {
        // When
        load(fakeEvent)

        // Then
        verify(mockProfilingScope).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
        verify(mockHandler).postDelayed(any(), eq(PendingOomErrorStore.TIMEOUT_MS))
    }

    @Test
    fun `M announce again only while awaiting W onProfilingReady()`() {
        // Given
        load(fakeEvent)

        // When
        testedStore.onProfilingReady()
        testedStore.onOomProfileResolution(errorId, true)
        testedStore.onProfilingReady()

        // Then
        verify(mockProfilingScope, times(2)).sendEvent(ProfilerEvent.RumHeldOomErrorEvent(errorId))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M write with matching status and remove W onOomProfileResolution()`(isProfileWritten: Boolean) {
        // Given
        load(fakeEvent)

        // When
        testedStore.onOomProfileResolution(errorId, isProfileWritten)

        // Then
        val expected = if (isProfileWritten) ErrorEvent.ProfilingStatus.RUNNING else ErrorEvent.ProfilingStatus.STOPPED
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(expected)
        assertThat(writtenEvent().date).isEqualTo(fakeEvent.date)
        verify(mockDataStore).removeValue(eq(PendingOomErrorStore.KEY), anyOrNull())
    }

    @Test
    fun `M ignore W onOomProfileResolution() {other error, then second answer}`(@StringForgery fakeOtherId: String) {
        // Given
        load(fakeEvent)

        // When
        testedStore.onOomProfileResolution(fakeOtherId, true)
        testedStore.onOomProfileResolution(errorId, false)
        testedStore.onOomProfileResolution(errorId, true)

        // Then
        assertThat(writtenEvent().dd.profiling?.status).isEqualTo(ErrorEvent.ProfilingStatus.STOPPED)
    }

    @Test
    fun `M write without profiling and ignore later answer W timeout`() {
        // Given
        load(fakeEvent)

        // When
        runTimeout()
        testedStore.onOomProfileResolution(errorId, true)

        // Then
        assertThat(writtenEvent().dd.profiling).isNull()
        verify(mockDataStore).removeValue(eq(PendingOomErrorStore.KEY), anyOrNull())
    }

    @Test
    fun `M keep record and slot W onOomProfileResolution() {batch write fails}`() {
        // Given
        isBatchWriteSuccessful = false
        load(fakeEvent)

        // When
        testedStore.onOomProfileResolution(errorId, true)

        // Then
        verify(mockDataStore, never()).removeValue(any(), anyOrNull())
        assertThat(testedStore.tryHold(fakeEvent)).isFalse
    }

    @Test
    fun `M free the slot W onOomProfileResolution() {written}`() {
        // Given
        load(fakeEvent)

        // When
        testedStore.onOomProfileResolution(errorId, true)

        // Then
        assertThat(testedStore.tryHold(fakeEvent)).isTrue
    }

    // endregion

    @Test
    fun `M keep event W serialize then deserialize`() {
        // When
        val serialized = PendingOomErrorStore.ErrorEventSerializer().serialize(fakeEvent)
        val result = PendingOomErrorStore.ErrorEventDeserializer().deserialize(serialized)

        // Then
        assertThat(result?.error?.id).isEqualTo(errorId)
        assertThat(result?.view?.id).isEqualTo(fakeEvent.view.id)
    }

    @Test
    fun `M return null W deserialize() {corrupted}`(@StringForgery fakeGarbage: String) {
        // When
        val result = PendingOomErrorStore.ErrorEventDeserializer().deserialize("{$fakeGarbage")

        // Then
        assertThat(result).isNull()
    }

    private val errorId: String
        get() = fakeEvent.error.id.orEmpty()

    private fun load(record: ErrorEvent?) {
        testedStore.start(mockDataStore)
        readCallback?.onSuccess(record?.let { DataStoreContent(PendingOomErrorStore.VERSION, it) })
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
