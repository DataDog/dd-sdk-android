/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.ResourceProcessor
import com.datadog.android.sessionreplay.internal.storage.EmbeddedContentRecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.google.gson.JsonParser
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedContentReceiverTest {

    private lateinit var testedReceiver: EmbeddedContentReceiver

    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockRecordWriter: EmbeddedContentRecordWriter

    @Mock
    lateinit var mockResourceProcessor: ResourceProcessor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private var isRecording: Boolean = true

    private val fakeRumContext = SessionReplayRumContext(
        applicationId = FAKE_NATIVE_APPLICATION_ID,
        sessionId = FAKE_NATIVE_SESSION_ID,
        viewId = FAKE_NATIVE_VIEW_ID
    )

    @BeforeEach
    fun `set up`() {
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        testedReceiver = EmbeddedContentReceiver(
            rumContextProvider = mockRumContextProvider,
            recordWriter = { mockRecordWriter },
            resourceProcessor = { mockResourceProcessor },
            isRecording = { isRecording },
            executor = { Executor(Runnable::run) },
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M enrich and write records W receive { record batch }`() {
        // Given
        val event = EmbeddedContentEvent.RecordBatch(
            records = listOf(
                mapOf(
                    "timestamp" to 123L,
                    "type" to 10L,
                    FAKE_FUTURE_FIELD_NAME to FAKE_FUTURE_FIELD_VALUE
                ),
                mapOf("type" to 11L, EmbeddedContentReceiver.RECORD_SLOT_ID_KEY to "stale-slot")
            ),
            slotId = FAKE_NATIVE_SLOT_ID,
            viewId = FAKE_EMBEDDED_VIEW_ID
        )

        // When
        testedReceiver.receive(event)

        // Then
        argumentCaptor<ByteArray> {
            verify(mockRecordWriter).writeRaw(capture(), eq(FAKE_EMBEDDED_VIEW_ID), eq(2))
            val json = JsonParser.parseString(firstValue.toString(Charsets.UTF_8)).asJsonObject
            assertThat(json[EmbeddedContentReceiver.APPLICATION_ID_KEY].asString)
                .isEqualTo(FAKE_NATIVE_APPLICATION_ID)
            assertThat(json[EmbeddedContentReceiver.SESSION_ID_KEY].asString)
                .isEqualTo(FAKE_NATIVE_SESSION_ID)
            assertThat(json[EmbeddedContentReceiver.VIEW_ID_KEY].asString)
                .isEqualTo(FAKE_EMBEDDED_VIEW_ID)
            val records = json[EmbeddedContentReceiver.RECORDS_KEY].asJsonArray
            assertThat(records).hasSize(2)
            assertThat(records[0].asJsonObject[EmbeddedContentReceiver.RECORD_SLOT_ID_KEY].asString)
                .isEqualTo(FAKE_NATIVE_SLOT_ID)
            assertThat(
                records[0].asJsonObject[FAKE_FUTURE_FIELD_NAME].asString
            ).isEqualTo(FAKE_FUTURE_FIELD_VALUE)
            assertThat(records[1].asJsonObject[EmbeddedContentReceiver.RECORD_SLOT_ID_KEY].asString)
                .isEqualTo(FAKE_NATIVE_SLOT_ID)
        }
        verifyNoInteractions(mockResourceProcessor)
    }

    @Test
    fun `M write records W receive { slot has no native container }`() {
        // When
        testedReceiver.receive(
            EmbeddedContentEvent.RecordBatch(
                records = listOf(mapOf("type" to 10L)),
                slotId = "unmaterialized-slot",
                viewId = FAKE_EMBEDDED_VIEW_ID
            )
        )

        // Then
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
    }

    @Test
    fun `M process records asynchronously W receive { record batch }`() {
        // Given
        lateinit var queuedTask: Runnable
        val replacementRecordWriter = mock<EmbeddedContentRecordWriter>()
        var currentRecordWriter = mockRecordWriter
        testedReceiver = EmbeddedContentReceiver(
            rumContextProvider = mockRumContextProvider,
            recordWriter = { currentRecordWriter },
            resourceProcessor = { mockResourceProcessor },
            isRecording = { isRecording },
            executor = { Executor { queuedTask = it } },
            internalLogger = mockInternalLogger
        )

        // When
        testedReceiver.receive(
            EmbeddedContentEvent.RecordBatch(
                records = listOf(mapOf("type" to 10L)),
                slotId = FAKE_NATIVE_SLOT_ID,
                viewId = FAKE_EMBEDDED_VIEW_ID
            )
        )

        // Then
        verifyNoInteractions(mockRecordWriter)

        // When
        currentRecordWriter = replacementRecordWriter
        queuedTask.run()

        // Then
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
        verifyNoInteractions(replacementRecordWriter)
    }

    @Test
    fun `M write resource W receive { resource }`() {
        // Given
        val resourceData = byteArrayOf(1, 2, 3)

        // When
        testedReceiver.receive(
            EmbeddedContentEvent.Resource(
                identifier = FAKE_RESOURCE_ID,
                data = resourceData,
                mimeType = FAKE_MIME_TYPE
            )
        )

        // Then
        verify(mockResourceProcessor).process(FAKE_RESOURCE_ID, resourceData, FAKE_MIME_TYPE)
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M retain scheduled processor W receive { processor changes before resource processing }`() {
        // Given
        lateinit var queuedTask: Runnable
        val replacementResourceProcessor = mock<ResourceProcessor>()
        var currentResourceProcessor = mockResourceProcessor
        val resourceData = byteArrayOf(1, 2, 3)
        testedReceiver = EmbeddedContentReceiver(
            rumContextProvider = mockRumContextProvider,
            recordWriter = { mockRecordWriter },
            resourceProcessor = { currentResourceProcessor },
            isRecording = { isRecording },
            executor = { Executor { queuedTask = it } },
            internalLogger = mockInternalLogger
        )

        // When
        testedReceiver.receive(
            EmbeddedContentEvent.Resource(
                identifier = FAKE_RESOURCE_ID,
                data = resourceData,
                mimeType = FAKE_MIME_TYPE
            )
        )
        currentResourceProcessor = replacementResourceProcessor
        queuedTask.run()

        // Then
        verify(mockResourceProcessor).process(FAKE_RESOURCE_ID, resourceData, FAKE_MIME_TYPE)
        verifyNoInteractions(replacementResourceProcessor)
    }

    @Test
    fun `M drop event W receive { recording inactive }`() {
        // Given
        isRecording = false

        // When
        testedReceiver.receive(
            EmbeddedContentEvent.RecordBatch(
                records = listOf(mapOf("type" to 10L)),
                slotId = FAKE_SLOT_ID,
                viewId = FAKE_VIEW_ID
            )
        )

        // Then
        verifyNoInteractions(mockRumContextProvider, mockRecordWriter, mockResourceProcessor)
    }

    @Test
    fun `M drop records W receive { invalid RUM context }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())

        // When
        testedReceiver.receive(
            EmbeddedContentEvent.RecordBatch(
                records = listOf(mapOf("type" to 10L)),
                slotId = FAKE_SLOT_ID,
                viewId = FAKE_VIEW_ID
            )
        )

        // Then
        verifyNoInteractions(mockRecordWriter, mockResourceProcessor)
    }

    @Test
    fun `M drop records W receive { empty record batch }`() {
        // When
        testedReceiver.receive(
            EmbeddedContentEvent.RecordBatch(
                records = emptyList(),
                slotId = FAKE_SLOT_ID,
                viewId = FAKE_VIEW_ID
            )
        )

        // Then
        verifyNoInteractions(mockRumContextProvider, mockRecordWriter, mockResourceProcessor)
    }

    @Test
    fun `M drop resource W receive { invalid RUM application and session context }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())
        val resourceData = byteArrayOf(1, 2, 3)

        // When
        testedReceiver.receive(
            EmbeddedContentEvent.Resource(
                identifier = FAKE_RESOURCE_ID,
                data = resourceData,
                mimeType = FAKE_MIME_TYPE
            )
        )

        // Then
        verifyNoInteractions(mockRecordWriter, mockResourceProcessor)
    }

    @Test
    fun `M write resource W receive { RUM application and session context is valid without view }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(
                applicationId = FAKE_APPLICATION_ID,
                sessionId = FAKE_SESSION_ID
            )
        )
        val resourceData = byteArrayOf(1, 2, 3)

        // When
        testedReceiver.receive(
            EmbeddedContentEvent.Resource(
                identifier = FAKE_RESOURCE_ID,
                data = resourceData,
                mimeType = FAKE_MIME_TYPE
            )
        )

        // Then
        verify(mockResourceProcessor).process(FAKE_RESOURCE_ID, resourceData, FAKE_MIME_TYPE)
        verifyNoInteractions(mockRecordWriter)
    }

    private companion object {
        const val FAKE_APPLICATION_ID = "application-id"
        const val FAKE_SESSION_ID = "session-id"
        const val FAKE_VIEW_ID = "view-id"
        const val FAKE_SLOT_ID = "slot-id"
        const val FAKE_NATIVE_APPLICATION_ID = "native-application-id"
        const val FAKE_NATIVE_SESSION_ID = "native-session-id"
        const val FAKE_NATIVE_VIEW_ID = "native-view-id"
        const val FAKE_EMBEDDED_VIEW_ID = "embedded-view-id"
        const val FAKE_NATIVE_SLOT_ID = "native-slot"
        const val FAKE_RESOURCE_ID = "resource-id"
        const val FAKE_MIME_TYPE = "image/png"
        const val FAKE_FUTURE_FIELD_NAME = "futureField"
        const val FAKE_FUTURE_FIELD_VALUE = "preserved"
    }
}
