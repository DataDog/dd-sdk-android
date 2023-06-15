/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.sessionreplay.SessionReplayFeature
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.context.DatadogContext
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
    lateinit var mockSessionReplayFeature: FeatureScope

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(mockSessionReplayFeature)
        testedWriter = SessionReplayRecordWriter(mockSdkCore)
    }

    @Test
    fun `M write the record in a new batch W write { first view in session }`(forge: Forge) {
        // Given
        val fakeRecord = forge.forgeEnrichedRecord()
        whenever(mockSessionReplayFeature.withWriteContext(eq(true), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        // When
        testedWriter.write(fakeRecord)

        // Then
        verify(mockEventBatchWriter).write(fakeRecord.toJson().toByteArray(), null)
        verifyNoMoreInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M write the record in the same batch W write { when same view }`(forge: Forge) {
        // Given
        val fakeRecord1 = forge.forgeEnrichedRecord()
        val fakeRecord2 = fakeRecord1.copy()
        testedWriter.write(fakeRecord1)

        // When
        testedWriter.write(fakeRecord2)

        // Then
        verify(mockSessionReplayFeature)
            .withWriteContext(eq(true), any())
        verify(mockSessionReplayFeature)
            .withWriteContext(eq(false), any())
        verifyNoMoreInteractions(mockSessionReplayFeature)
    }

    @Test
    fun `M write the record in new batch W write { when different view }`(forge: Forge) {
        // Given
        val fakeRecord1 = forge.forgeEnrichedRecord()
        val fakeRecord2 = forge.forgeEnrichedRecord()
        whenever(mockSessionReplayFeature.withWriteContext(eq(true), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        testedWriter.write(fakeRecord1)

        // When
        testedWriter.write(fakeRecord2)

        // Then
        verify(mockEventBatchWriter).write(fakeRecord1.toJson().toByteArray(), null)
        verify(mockEventBatchWriter).write(fakeRecord2.toJson().toByteArray(), null)
        verifyNoMoreInteractions(mockEventBatchWriter)
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
        verifyNoInteractions(mockSessionReplayFeature)
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
