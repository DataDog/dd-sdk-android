/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.sessionreplay.embedded.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.embedded.internal.rum.EmbeddedRumEventContextProvider
import com.datadog.android.sessionreplay.embedded.internal.rum.RumContext
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedRecordsWriterTest {

    private lateinit var testedWriter: EmbeddedRecordsWriter

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockDataWriter: DataWriter<JsonObject>

    @Mock
    lateinit var mockRumContextProvider: EmbeddedRumEventContextProvider

    @Mock
    lateinit var mockEmbeddedReplayFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    @StringForgery
    lateinit var fakeSlotId: String

    @StringForgery
    lateinit var fakeViewId: String

    lateinit var fakeRecords: List<JsonObject>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeRecords = forge.aList(size = forge.anInt(min = 1, max = 5)) { getForgery<JsonObject>() }
        fakeRumContext = fakeRumContext.copy(sessionState = RumContext.SESSION_TRACKED_STATE)
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to forge.aMap {
                    EmbeddedRecordsWriter.SESSION_REPLAY_ENABLED_KEY to true
                }
            }
        )
        whenever(mockRumContextProvider.getRumContext(any())) doReturn fakeRumContext
        whenever(
            mockSdkCore.getFeature(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
        ) doReturn mockEmbeddedReplayFeatureScope
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(
            mockEmbeddedReplayFeatureScope.withWriteContext(
                eq(setOf(Feature.RUM_FEATURE_NAME, Feature.SESSION_REPLAY_FEATURE_NAME)),
                any()
            )
        ) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        testedWriter = EmbeddedRecordsWriter(mockSdkCore, mockDataWriter, mockRumContextProvider)
    }

    @Test
    fun `M write an enriched record W write() { valid records }`() {
        // When
        testedWriter.write(fakeSlotId, fakeViewId, fakeRecords)

        // Then
        val captor = argumentCaptor<JsonObject>()
        verify(mockDataWriter).write(eq(mockEventBatchWriter), captor.capture(), eq(EventType.DEFAULT))
        val written = captor.firstValue
        assertThat(written.get(EmbeddedRecordsWriter.ENRICHED_RECORD_APPLICATION_ID_KEY).asString)
            .isEqualTo(fakeRumContext.applicationId)
        assertThat(written.get(EmbeddedRecordsWriter.ENRICHED_RECORD_SESSION_ID_KEY).asString)
            .isEqualTo(fakeRumContext.sessionId)
        assertThat(written.get(EmbeddedRecordsWriter.ENRICHED_RECORD_VIEW_ID_KEY).asString)
            .isEqualTo(fakeViewId)
        val writtenRecords = written.getAsJsonArray(EmbeddedRecordsWriter.RECORDS_KEY)
        assertThat(writtenRecords).hasSize(fakeRecords.size)
        writtenRecords.forEach {
            assertThat(it.asJsonObject.get(EmbeddedRecordsWriter.SLOT_ID_KEY).asString).isEqualTo(fakeSlotId)
        }
    }

    @Test
    fun `M leave the input records unmutated W write() { valid records }`() {
        // When
        testedWriter.write(fakeSlotId, fakeViewId, fakeRecords)

        // Then
        fakeRecords.forEach {
            assertThat(it.has(EmbeddedRecordsWriter.SLOT_ID_KEY)).isFalse()
        }
    }

    @Test
    fun `M do nothing W write() { records empty }`() {
        // When
        testedWriter.write(fakeSlotId, fakeViewId, emptyList())

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W write() { embedded replay feature not registered }`() {
        // Given
        whenever(
            mockSdkCore.getFeature(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
        ) doReturn null

        // When
        testedWriter.write(fakeSlotId, fakeViewId, fakeRecords)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W write() { session replay not enabled }`(forge: Forge) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to forge.aMap {
                    EmbeddedRecordsWriter.SESSION_REPLAY_ENABLED_KEY to false
                }
            }
        )

        // When
        testedWriter.write(fakeSlotId, fakeViewId, fakeRecords)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W write() { rum context missing }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext(fakeDatadogContext)) doReturn null

        // When
        testedWriter.write(fakeSlotId, fakeViewId, fakeRecords)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W write() { rum session not tracked }`(forge: Forge) {
        // Given
        whenever(mockRumContextProvider.getRumContext(fakeDatadogContext)) doReturn
            fakeRumContext.copy(sessionState = forge.anAlphabeticalString())

        // When
        testedWriter.write(fakeSlotId, fakeViewId, fakeRecords)

        // Then
        verifyNoInteractions(mockDataWriter)
    }
}
