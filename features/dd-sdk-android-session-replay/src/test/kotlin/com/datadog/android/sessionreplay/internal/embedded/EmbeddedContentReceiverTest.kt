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
import org.mockito.kotlin.atLeastOnce
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

    private val capturedSlotIds = mutableListOf<String>()

    private val captureRequests: Int get() = capturedSlotIds.size

    private val fakeSlotRegistry = EmbeddedContentSlotRegistry()

    private val fakeRumContext = SessionReplayRumContext(
        applicationId = FAKE_NATIVE_APPLICATION_ID,
        sessionId = FAKE_NATIVE_SESSION_ID,
        viewId = FAKE_NATIVE_VIEW_ID
    )

    @BeforeEach
    fun `set up`() {
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        testedReceiver = receiver()
    }

    @Test
    fun `M enrich and write records W receive { record batch }`() {
        // Given
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)
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
    fun `M process records asynchronously W receive { record batch }`() {
        // Given
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)
        lateinit var queuedTask: Runnable
        val replacementRecordWriter = mock<EmbeddedContentRecordWriter>()
        var currentRecordWriter = mockRecordWriter
        testedReceiver = receiver(
            recordWriter = { currentRecordWriter },
            executor = { Executor { queuedTask = it } }
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
        testedReceiver = receiver(
            resourceProcessor = { currentResourceProcessor },
            executor = { Executor { queuedTask = it } }
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

    // region placeholder ordering

    @Test
    fun `M hold records W receive { no placeholder for slot }`() {
        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M hold records W receive { placeholder in another view }`() {
        // Given
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_NATIVE_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)

        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M write held records W onPlaceholdersWritten { placeholder emitted for the slot }`() {
        // Given
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))
        verifyNoInteractions(mockRecordWriter)

        // When
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)

        // Then
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
    }

    @Test
    fun `M keep records held W onPlaceholdersWritten { placeholder emitted for another slot }`() {
        // Given
        // Still registered, so its placeholder is only late, not never coming.
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_NATIVE_SLOT_ID)
        fakeSlotRegistry.notifySlotChanged(null, fakeRegistration)
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))

        // When
        givenPlaceholder(FAKE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M carry records past the placeholder W onPlaceholdersWritten { captured before it }`() {
        // Given
        testedReceiver.receive(recordBatch(timestamps = listOf(100L, 168L, 207L)))

        // When
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, 1000L)

        // Then
        // The earliest record lands one millisecond past the placeholder, and the 68/39 ms
        // intervals between the three are preserved.
        assertThat(writtenTimestamps()).containsExactly(1001L, 1069L, 1108L)
    }

    @Test
    fun `M leave timestamps alone W receive { records already past the placeholder }`() {
        // Given
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, 1000L)

        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(1500L, 1600L)))

        // Then
        assertThat(writtenTimestamps()).containsExactly(1500L, 1600L)
    }

    @Test
    fun `M add view time offset W receive { record batch }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeRumContext.copy(viewTimeOffsetMs = FAKE_VIEW_TIME_OFFSET_MS))
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, 1000L)

        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(2000L)))

        // Then
        assertThat(writtenTimestamps()).containsExactly(2000L + FAKE_VIEW_TIME_OFFSET_MS)
    }

    @Test
    fun `M shift offset-corrected timestamps W onPlaceholdersWritten { held records }`() {
        // Given
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeRumContext.copy(viewTimeOffsetMs = FAKE_VIEW_TIME_OFFSET_MS))
        testedReceiver.receive(recordBatch(timestamps = listOf(100L, 150L)))

        // When
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, 10_000L)

        // Then
        // The floor applies to the offset-corrected timestamps, not the raw ones.
        assertThat(writtenTimestamps()).containsExactly(10_001L, 10_051L)
    }

    @Test
    fun `M shift each view on its own floor W onPlaceholdersWritten { held across views }`() {
        // Given
        testedReceiver.receive(
            recordBatch(timestamps = listOf(100L), viewId = FAKE_EMBEDDED_VIEW_ID)
        )
        testedReceiver.receive(
            recordBatch(timestamps = listOf(200L), viewId = FAKE_OTHER_EMBEDDED_VIEW_ID)
        )

        // When
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_OTHER_EMBEDDED_VIEW_ID, 5000L)

        // Then
        // Only the batch sharing the placeholder's view is shifted onto its floor; the batch from
        // the earlier view has no placeholder of its own and keeps its timestamp.
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_OTHER_EMBEDDED_VIEW_ID), eq(1))
        assertThat(writtenTimestamps()).containsExactlyInAnyOrder(100L, 5001L)
    }

    @Test
    fun `M write oldest held batch W receive { more batches than the bound }`() {
        // When
        repeat(EmbeddedContentReceiver.MAX_PENDING_BATCHES + 1) { index ->
            testedReceiver.receive(recordBatch(timestamps = listOf(index.toLong())))
        }

        // Then
        // A mis-ordered record beats a missing one: the evicted batch is written as captured.
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
        assertThat(writtenTimestamps()).containsExactly(0L)
    }

    @Test
    fun `M hold every batch W receive { as many batches as the bound }`() {
        // When
        repeat(EmbeddedContentReceiver.MAX_PENDING_BATCHES) { index ->
            testedReceiver.receive(recordBatch(timestamps = listOf(index.toLong())))
        }

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M write oldest slot's held batches W receive { more slots than the bound }`() {
        // When
        repeat(EmbeddedContentReceiver.MAX_PENDING_SLOTS + 1) { index ->
            testedReceiver.receive(
                recordBatch(timestamps = listOf(index.toLong()), slotId = "slot-$index")
            )
        }

        // Then
        // A placeholder may never come for a slot, so the oldest slot makes room for the newest and
        // what it was holding is written rather than left pending forever.
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
        assertThat(writtenTimestamps()).containsExactly(0L)
    }

    @Test
    fun `M hold every slot W receive { as many slots as the bound }`() {
        // When
        repeat(EmbeddedContentReceiver.MAX_PENDING_SLOTS) { index ->
            testedReceiver.receive(
                recordBatch(timestamps = listOf(index.toLong()), slotId = "slot-$index")
            )
        }

        // Then
        verifyNoInteractions(mockRecordWriter)
    }

    @Test
    fun `M write held batch once W receive { placeholder lands between the check and the hold }`() {
        // Given
        // The registry publishes a placeholder and notifies its listeners outside the lock the hold
        // takes, so a placeholder can appear after the check found none and before the batch is
        // held — with no listener left to fire for it. Consecutive returns reproduce exactly that:
        // the first check sees nothing, every later one sees the placeholder.
        val mockSlotRegistry = mock<EmbeddedContentSlotRegistry>()
        whenever(mockSlotRegistry.placeholder(FAKE_NATIVE_SLOT_ID)).thenReturn(
            null,
            EmbeddedContentSlotRegistry.Placeholder(FAKE_EMBEDDED_VIEW_ID, 1000L)
        )
        testedReceiver = receiver(registry = mockSlotRegistry)

        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(100L)))

        // Then
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
        assertThat(writtenTimestamps()).containsExactly(1001L)
    }

    // endregion

    // region requested captures

    @Test
    fun `M request a capture W receive { batch held with no placeholder }`() {
        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))

        // Then
        // Nothing else is guaranteed to draw the placeholder the batch is waiting for.
        assertThat(captureRequests).isEqualTo(1)
    }

    @Test
    fun `M request a capture once W receive { several batches held for the same slot }`() {
        // When
        repeat(5) { index ->
            testedReceiver.receive(recordBatch(timestamps = listOf(index.toLong())))
        }

        // Then
        // The queue stays non-empty until it is released, so the batches after the first ask again
        // for a capture that is already on its way.
        assertThat(captureRequests).isEqualTo(1)
    }

    @Test
    fun `M request a capture per slot W receive { batches held for several slots }`() {
        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(123L), slotId = FAKE_NATIVE_SLOT_ID))
        testedReceiver.receive(recordBatch(timestamps = listOf(123L), slotId = FAKE_SLOT_ID))

        // Then
        assertThat(captureRequests).isEqualTo(2)
    }

    @Test
    fun `M not request a capture W receive { placeholder covers the batch }`() {
        // Given
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)

        // When
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))

        // Then
        assertThat(captureRequests).isZero()
    }

    @Test
    fun `M request a capture again W receive { batch held after the queue was released }`() {
        // Given
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)

        // When
        // The placeholder of the previous view is no help to a batch addressed to this one.
        testedReceiver.receive(
            recordBatch(timestamps = listOf(456L), viewId = FAKE_OTHER_EMBEDDED_VIEW_ID)
        )

        // Then
        assertThat(captureRequests).isEqualTo(2)
    }

    // endregion

    // region abandoned slots

    @Test
    fun `M write held batches W snapshot { slot neither drawn nor registered }`() {
        // Given
        // The slot was torn down before its placeholder was ever drawn, so nothing will draw it
        // again — holding its batches only delays the same unshifted write.
        testedReceiver.receive(recordBatch(timestamps = listOf(123L)))

        // When
        givenPlaceholder(FAKE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)

        // Then
        verify(mockRecordWriter).writeRaw(any(), eq(FAKE_EMBEDDED_VIEW_ID), eq(1))
        assertThat(writtenTimestamps()).containsExactly(123L)
    }

    @Test
    fun `M shift held batches W snapshot { slot drawn }`() {
        // Given
        testedReceiver.receive(recordBatch(timestamps = listOf(1L)))

        // When
        givenPlaceholder(FAKE_NATIVE_SLOT_ID, FAKE_EMBEDDED_VIEW_ID, FAKE_PLACEHOLDER_TIMESTAMP)

        // Then
        // Released by the placeholder rather than flushed, so it lands after the wireframe.
        assertThat(writtenTimestamps()).containsExactly(FAKE_PLACEHOLDER_TIMESTAMP + 1)
    }

    // endregion

    // region Internal

    private fun receiver(
        registry: EmbeddedContentSlotRegistry = fakeSlotRegistry,
        recordWriter: () -> EmbeddedContentRecordWriter = { mockRecordWriter },
        resourceProcessor: () -> ResourceProcessor = { mockResourceProcessor },
        executor: () -> Executor = { Executor(Runnable::run) }
    ): EmbeddedContentReceiver {
        return EmbeddedContentReceiver(
            rumContextProvider = mockRumContextProvider,
            recordWriter = recordWriter,
            resourceProcessor = resourceProcessor,
            isRecording = { isRecording },
            executor = executor,
            requestCapture = { slotId -> capturedSlotIds += slotId },
            embeddedContentSlotRegistry = registry,
            internalLogger = mockInternalLogger
        )
    }

    private fun givenPlaceholder(slotId: String, viewId: String, timestamp: Long) {
        fakeSlotRegistry.onPlaceholdersWritten(viewId, timestamp, setOf(slotId))
    }

    private fun recordBatch(
        timestamps: List<Long>,
        slotId: String = FAKE_NATIVE_SLOT_ID,
        viewId: String = FAKE_EMBEDDED_VIEW_ID
    ): EmbeddedContentEvent.RecordBatch {
        return EmbeddedContentEvent.RecordBatch(
            records = timestamps.map { mapOf("type" to 10L, "timestamp" to it) },
            slotId = slotId,
            viewId = viewId
        )
    }

    /** Every record timestamp handed to the writer, in the order it was written. */
    private fun writtenTimestamps(): List<Long> {
        val captor = argumentCaptor<ByteArray>()
        verify(mockRecordWriter, atLeastOnce()).writeRaw(captor.capture(), any(), any())
        return captor.allValues.flatMap { bytes ->
            JsonParser.parseString(bytes.toString(Charsets.UTF_8))
                .asJsonObject[EmbeddedContentReceiver.RECORDS_KEY]
                .asJsonArray
                .map { it.asJsonObject[EmbeddedRecordTimeline.RECORD_TIMESTAMP_KEY].asLong }
        }
    }

    // endregion

    private companion object {
        const val FAKE_APPLICATION_ID = "application-id"
        const val FAKE_SESSION_ID = "session-id"
        const val FAKE_VIEW_ID = "view-id"
        const val FAKE_SLOT_ID = "slot-id"
        const val FAKE_NATIVE_APPLICATION_ID = "native-application-id"
        const val FAKE_NATIVE_SESSION_ID = "native-session-id"
        const val FAKE_NATIVE_VIEW_ID = "native-view-id"
        const val FAKE_EMBEDDED_VIEW_ID = "embedded-view-id"
        const val FAKE_OTHER_EMBEDDED_VIEW_ID = "other-embedded-view-id"
        const val FAKE_PLACEHOLDER_TIMESTAMP = 100L
        const val FAKE_VIEW_TIME_OFFSET_MS = 500L
        const val FAKE_NATIVE_SLOT_ID = "native-slot"
        const val FAKE_RESOURCE_ID = "resource-id"
        const val FAKE_MIME_TYPE = "image/png"
        const val FAKE_FUTURE_FIELD_NAME = "futureField"
        const val FAKE_FUTURE_FIELD_VALUE = "preserved"
    }
}
