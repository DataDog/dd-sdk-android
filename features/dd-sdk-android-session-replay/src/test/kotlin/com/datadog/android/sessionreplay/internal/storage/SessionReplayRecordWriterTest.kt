/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRecordWriterTest {
    lateinit var testedWriter: SessionReplayRecordWriter

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockRecordCallback: RecordCallback

    @Mock
    lateinit var mockSessionReplayFeature: FeatureScope

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @BeforeEach
    fun `set up`() {
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any()))
            .thenReturn(true)

        whenever(mockSdkCore.getFeature(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(mockSessionReplayFeature)

        testedWriter = SessionReplayRecordWriter(mockSdkCore, mockRecordCallback)
    }

    @Test
    fun `M write the record in a batch W write`(forge: Forge) {
        // Given
        val fakeRecord = forge.forgeEnrichedRecord()
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockSessionReplayFeature.withWriteContext(any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(0)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        // When
        testedWriter.write(fakeRecord)

        // Then
        verify(mockEventBatchWriter).write(
            event = RawBatchEvent(data = fakeRecord.toJson().toByteArray()),
            batchMetadata = null,
            eventType = EventType.DEFAULT
        )
        verifyNoMoreInteractions(mockEventBatchWriter)

        verify(mockRecordCallback).onRecordForViewSent(fakeRecord)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M do nothing W write { feature not properly initialized }`(forge: Forge) {
        // Given
        val fakeRecord = forge.forgeEnrichedRecord()
        whenever(mockSdkCore.getFeature(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(null)

        // When
        testedWriter.write(fakeRecord)

        // Then
        verifyNoMoreInteractions(mockSessionReplayFeature)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M not call record callback W write { eventBatchWriter write failed }`(forge: Forge) {
        // Given
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any()))
            .thenReturn(false)

        val fakeRecord = forge.forgeEnrichedRecord()
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockSessionReplayFeature.withWriteContext(any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(0)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        // When
        testedWriter.write(fakeRecord)

        // Then
        verify(mockEventBatchWriter).write(
            event = RawBatchEvent(data = fakeRecord.toJson().toByteArray()),
            batchMetadata = null,
            eventType = EventType.DEFAULT
        )
        verifyNoMoreInteractions(mockEventBatchWriter)

        verifyNoMoreInteractions(mockRecordCallback)
    }

    private fun Forge.forgeEnrichedRecord(): EnrichedRecord {
        // We don't want to create a forgery for this as this lives in the session-replay module
        // and we will need to copy all the records forgeries. Instead we just forge this record
        // here with empty records as it will not matter in the tests. Later if we need it we might
        // end up doing that.
        val applicationId = getForgery<UUID>().toString()
        val sessionId = getForgery<UUID>().toString()
        val viewId = getForgery<UUID>().toString()
        return EnrichedRecord(applicationId, sessionId, viewId, emptyList())
    }
}
