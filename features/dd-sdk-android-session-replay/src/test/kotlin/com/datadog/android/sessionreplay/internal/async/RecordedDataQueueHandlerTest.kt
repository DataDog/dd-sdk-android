/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler.Companion.ITEM_DROPPED_FROM_QUEUE_ERROR_MESSAGE
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler.Companion.MAX_DELAY_MS
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RecordedQueuedItemContext
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RecordedDataQueueHandlerTest {
    private lateinit var testedHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockProcessor: RecordedDataProcessor

    @Mock
    lateinit var mockRumContextDataHandler: RumContextDataHandler

    private lateinit var spyExecutorService: ExecutorService

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    @Mock
    lateinit var mockTimeProvider: SessionReplayTimeProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeRecordedQueuedItemContext: RecordedQueuedItemContext

    @Forgery
    lateinit var fakeApplicationId: UUID

    @Forgery
    lateinit var fakeIdentifier: UUID

    @Forgery
    lateinit var fakeSnapshotQueueItem: SnapshotRecordedDataQueueItem

    @Spy
    private lateinit var fakeRecordedDataQueue: ConcurrentLinkedQueue<RecordedDataQueueItem>

    private lateinit var fakeTouchData: List<MobileSegment.MobileRecord>

    private lateinit var fakeNodeData: List<Node>

    private val snapshotItemCaptor = argumentCaptor<SnapshotRecordedDataQueueItem>()
    private val touchEventItemCaptor = argumentCaptor<TouchEventRecordedDataQueueItem>()
    private val resourceEventItemCaptor = argumentCaptor<ResourceRecordedDataQueueItem>()

    @BeforeEach
    fun setup(forge: Forge) {
        fakeRecordedDataQueue = ConcurrentLinkedQueue<RecordedDataQueueItem>()

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(fakeRecordedQueuedItemContext)

        spyExecutorService = spy(
            ThreadPoolExecutor(
                1,
                1,
                100,
                TimeUnit.MILLISECONDS,
                LinkedBlockingDeque()
            )
        )

        fakeTouchData = forge.aList { mock() }

        fakeNodeData = forge.aList { mock() }

        testedHandler = RecordedDataQueueHandler(
            processor = mockProcessor,
            rumContextDataHandler = mockRumContextDataHandler,
            timeProvider = mockTimeProvider,
            executorService = spyExecutorService,
            internalLogger = mockInternalLogger,
            recordedQueue = fakeRecordedDataQueue
        )
    }

    @ParameterizedTest
    @ValueSource(
        classes = [
            NullPointerException::class,
            RejectedExecutionException::class
        ]
    )
    fun `M log exception W executor service throws`(exceptionType: Class<Throwable>) {
        // Given
        val fakeThrowable = exceptionType.getDeclaredConstructor().newInstance()
        val mockExecutorService = mock<ExecutorService>()
        testedHandler = RecordedDataQueueHandler(
            processor = mockProcessor,
            rumContextDataHandler = mockRumContextDataHandler,
            timeProvider = mockTimeProvider,
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger
        )
        testedHandler.recordedDataQueue.add(fakeSnapshotQueueItem)
        whenever(mockExecutorService.execute(any())).thenThrow(fakeThrowable)

        // When
        testedHandler.tryToConsumeItems()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            RecordedDataQueueHandler.FAILED_TO_CONSUME_RECORDS_QUEUE_ERROR_MESSAGE,
            fakeThrowable
        )
    }

    @ParameterizedTest
    @ValueSource(
        classes = [
            IllegalArgumentException::class,
            ClassCastException::class,
            NullPointerException::class
        ]
    )
    fun `M log exception W addSnapshotItem { queue throws }`(exceptionType: Class<Throwable>) {
        // Given
        val fakeThrowable = exceptionType.getDeclaredConstructor().newInstance()
        val mockQueue: Queue<RecordedDataQueueItem> = mock()
        whenever(mockQueue.offer(any())).thenThrow(fakeThrowable)
        testedHandler = RecordedDataQueueHandler(
            processor = mockProcessor,
            rumContextDataHandler = mockRumContextDataHandler,
            timeProvider = mockTimeProvider,
            executorService = spyExecutorService,
            internalLogger = mockInternalLogger,
            recordedQueue = mockQueue
        )
        testedHandler.recordedDataQueue.add(fakeSnapshotQueueItem)

        // When
        testedHandler.addSnapshotItem(mockSystemInformation)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            RecordedDataQueueHandler.FAILED_TO_ADD_RECORDS_TO_QUEUE_ERROR_MESSAGE,
            fakeThrowable
        )
    }

    @Test
    fun `M use executorService W tryToConsumeItems() { queue has snapshot items }`() {
        // Given
        val item = testedHandler.addSnapshotItem(mockSystemInformation)
        testedHandler.recordedDataQueue.offer(item)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        verify(spyExecutorService).execute(any())
    }

    @Test
    fun `M use executorService W tryToConsumeItems() { queue has touch event items }`() {
        // Given
        val item = testedHandler.addTouchEventItem(fakeTouchData)
        testedHandler.recordedDataQueue.offer(item)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        verify(spyExecutorService).execute(any())
    }

    @Test
    fun `M no threads spawned W tryToConsumeItems() { queue is empty }`() {
        // When
        testedHandler.tryToConsumeItems()

        // Then
        verifyNoMoreInteractions(spyExecutorService)
    }

    @Test
    fun `M do not call processor W tryToConsumeItems() { empty queue }`() {
        // When
        testedHandler.tryToConsumeItems()

        // Then
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M do nothing W add() { snapshot with invalid RumContextData }`() {
        // Given
        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(null)

        // When
        val item = testedHandler.addSnapshotItem(mockSystemInformation)

        // Then
        assertThat(item).isNull()
    }

    @Test
    fun `M do nothing W add() { touch event with invalid RumContextData }`() {
        // Given
        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(null)

        // When
        val item = testedHandler.addTouchEventItem(fakeTouchData)

        // Then
        assertThat(item).isNull()
    }

    @Test
    fun `M snapshot item contains correct fields W add() { valid RumContextData }`() {
        // Given
        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(fakeRecordedQueuedItemContext)
        val currentRumContextData = mockRumContextDataHandler.createRumContextData()

        // When
        val item = testedHandler.addSnapshotItem(mockSystemInformation)

        // Then
        assertThat(item!!.recordedQueuedItemContext).isEqualTo(currentRumContextData)
        assertThat(item.systemInformation).isEqualTo(mockSystemInformation)
        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(1)
    }

    @Test
    fun `M touch event item contains correct fields W add() { valid RumContextData }`() {
        // Given
        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(fakeRecordedQueuedItemContext)
        val currentRumContextData = mockRumContextDataHandler.createRumContextData()

        // When
        val item =
            testedHandler.addTouchEventItem(fakeTouchData)

        // Then
        assertThat(item!!.recordedQueuedItemContext).isEqualTo(currentRumContextData)
        assertThat(item.touchData).isEqualTo(fakeTouchData)
        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(1)
    }

    @Test
    fun `M remove item from queue W tryToConsumeItems() { expired item }`() {
        // Given
        val item = testedHandler.addSnapshotItem(mockSystemInformation)
            ?: fail("item is null")
        item.nodes = fakeNodeData

        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(item.recordedQueuedItemContext.timestamp + MAX_DELAY_MS + 1)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(testedHandler.recordedDataQueue.isEmpty()).isTrue
        val expectedLogMessage = ITEM_DROPPED_FROM_QUEUE_ERROR_MESSAGE
            .format(Locale.US, true)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            expectedLogMessage
        )
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M remove item from queue W tryToConsumeItems() { invalid snapshot item }`(
        @Mock mockSnapshotItem: SnapshotRecordedDataQueueItem
    ) {
        // Given
        whenever(mockSnapshotItem.nodes).thenReturn(emptyList())
        whenever(mockSnapshotItem.isValid()).thenReturn(false)
        whenever(mockSnapshotItem.recordedQueuedItemContext).thenReturn(fakeRecordedQueuedItemContext)

        testedHandler.recordedDataQueue.offer(mockSnapshotItem)

        val timestamp = mockSnapshotItem.recordedQueuedItemContext.timestamp
        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(timestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(testedHandler.recordedDataQueue).isEmpty()
        val expectedLogMessage = ITEM_DROPPED_FROM_QUEUE_ERROR_MESSAGE.format(Locale.US, false)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            expectedLogMessage
        )
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M remove item from queue W tryToConsumeItems() { invalid touch event item }`(
        @Mock mockTouchEventItem: TouchEventRecordedDataQueueItem
    ) {
        // Given
        whenever(mockTouchEventItem.isValid()).thenReturn(false)
        whenever(mockTouchEventItem.recordedQueuedItemContext).thenReturn(fakeRecordedQueuedItemContext)

        testedHandler.recordedDataQueue.offer(mockTouchEventItem)

        val spyTimestamp = mockTouchEventItem.recordedQueuedItemContext.timestamp
        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(spyTimestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(testedHandler.recordedDataQueue).isEmpty()
        val expectedLogMessage = ITEM_DROPPED_FROM_QUEUE_ERROR_MESSAGE
            .format(Locale.US, false)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            expectedLogMessage
        )
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M remove item from queue W tryToConsumeItems() { invalid resource item }`(
        @Mock mockResourceItem: ResourceRecordedDataQueueItem
    ) {
        // Given
        whenever(mockResourceItem.isValid()).thenReturn(false)
        whenever(mockResourceItem.recordedQueuedItemContext).thenReturn(fakeRecordedQueuedItemContext)
        testedHandler.recordedDataQueue.offer(mockResourceItem)

        val timestamp = mockResourceItem.recordedQueuedItemContext.timestamp
        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(timestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(testedHandler.recordedDataQueue).isEmpty()
        val expectedLogMessage = ITEM_DROPPED_FROM_QUEUE_ERROR_MESSAGE
            .format(Locale.US, false)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            expectedLogMessage
        )
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M do nothing W tryToConsumeItems() { snapshot item not ready }`(
        @Mock mockSnapshotItem: SnapshotRecordedDataQueueItem
    ) {
        // Given
        doReturn(true).whenever(mockSnapshotItem).isValid()
        doReturn(false).whenever(mockSnapshotItem).isReady()

        testedHandler.recordedDataQueue.add(mockSnapshotItem)

        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(fakeRecordedQueuedItemContext.timestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M call processor W tryToConsumeItems() { valid snapshot item }`() {
        // Given
        val item = testedHandler.addSnapshotItem(mockSystemInformation) ?: fail("item is null")
        item.nodes = fakeNodeData
        item.isFinishedTraversal = true

        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(item.recordedQueuedItemContext.timestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        verify(mockProcessor).processScreenSnapshots(snapshotItemCaptor.capture())

        assertThat(snapshotItemCaptor.firstValue.nodes).isEqualTo(fakeNodeData)
        assertThat(snapshotItemCaptor.firstValue.systemInformation).isEqualTo(mockSystemInformation)
        assertThat(snapshotItemCaptor.firstValue.recordedQueuedItemContext).isEqualTo(item.recordedQueuedItemContext)
    }

    @Test
    fun `M call processor W tryToConsumeItems() { valid Touch Event item }`() {
        // Given
        val item = testedHandler.addTouchEventItem(fakeTouchData) ?: fail("item is null")

        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(item.recordedQueuedItemContext.timestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        verify(mockProcessor).processTouchEventsRecords(touchEventItemCaptor.capture())

        assertThat(touchEventItemCaptor.firstValue.recordedQueuedItemContext).isEqualTo(item.recordedQueuedItemContext)
        assertThat(touchEventItemCaptor.firstValue.touchData).isEqualTo(fakeTouchData)
    }

    @Test
    fun `M call processor W tryToConsumeItems() { valid Resource Event item }`(
        @StringForgery fakeIdentifier: String,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakePayload: String
    ) {
        // Given
        val item = testedHandler.addResourceItem(
            fakeIdentifier,
            fakeApplicationId,
            fakePayload.toByteArray()
        ) ?: fail("item is null")

        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(item.recordedQueuedItemContext.timestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        verify(mockProcessor).processResources(resourceEventItemCaptor.capture())

        assertThat(resourceEventItemCaptor.firstValue.recordedQueuedItemContext)
            .isEqualTo(item.recordedQueuedItemContext)
        assertThat(resourceEventItemCaptor.firstValue.identifier).isEqualTo(fakeIdentifier)
        assertThat(resourceEventItemCaptor.firstValue.applicationId).isEqualTo(fakeApplicationId)
        assertThat(resourceEventItemCaptor.firstValue.resourceData).isEqualTo(fakePayload.toByteArray())
    }

    @Test
    fun `M consume items in the correct order W tryToConsumeItems() { spawn multiple threads }`() {
        // Given
        val item1 = createFakeSnapshotItemWithDelayMs(1)
        val item2 = createFakeSnapshotItemWithDelayMs(2)
        val item3 = createFakeSnapshotItemWithDelayMs(3)

        item1.isFinishedTraversal = true
        item2.isFinishedTraversal = true
        item3.isFinishedTraversal = true

        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(3)
        val itemTimestamp = item1.recordedQueuedItemContext.timestamp

        // When
        repeat(50) {
            whenever(mockTimeProvider.getDeviceTimestamp())
                .thenReturn(itemTimestamp + it)

            testedHandler.tryToConsumeItems()
            spyExecutorService.shutdown()
            spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)
        }

        // Then
        inOrder(mockProcessor) {
            verifySnapshotItemProcessed(item1)
            verifySnapshotItemProcessed(item2)
            verifySnapshotItemProcessed(item3)
            verifyNoMoreInteractions(mockProcessor)
        }
    }

    @Test
    fun `M not consume items that are not ready W tryToConsumeItems() { some items not ready }`(
        @Mock mockSnapshotItem1: SnapshotRecordedDataQueueItem,
        @Mock mockSnapshotItem2: SnapshotRecordedDataQueueItem,
        @Mock mockSnapshotItem3: SnapshotRecordedDataQueueItem
    ) {
        // Given
        // item1
        val item1RumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 1)

        whenever(mockSnapshotItem1.recordedQueuedItemContext).thenReturn(item1RumContextData)
        whenever(mockSnapshotItem1.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSnapshotItem1.nodes).thenReturn(fakeNodeData)
        doReturn(true).whenever(mockSnapshotItem1).isValid()
        doReturn(true).whenever(mockSnapshotItem1).isReady()

        // item2
        val item2RumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 2)

        whenever(mockSnapshotItem2.recordedQueuedItemContext).thenReturn(item2RumContextData)
        whenever(mockSnapshotItem2.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSnapshotItem2.nodes).thenReturn(emptyList())
        doReturn(true).whenever(mockSnapshotItem2).isValid()
        doReturn(false).whenever(mockSnapshotItem2).isReady()

        // item3
        val item3RumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 3)

        whenever(mockSnapshotItem3.recordedQueuedItemContext).thenReturn(item3RumContextData)
        whenever(mockSnapshotItem3.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSnapshotItem3.nodes).thenReturn(fakeNodeData)
        doReturn(true).whenever(mockSnapshotItem3).isValid()
        doReturn(true).whenever(mockSnapshotItem3).isReady()

        testedHandler.recordedDataQueue.offer(mockSnapshotItem1)
        testedHandler.recordedDataQueue.offer(mockSnapshotItem2)
        testedHandler.recordedDataQueue.offer(mockSnapshotItem3)

        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(3)
        val item1Time = mockSnapshotItem1.recordedQueuedItemContext.timestamp

        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(item1Time + 1)

        // When
        repeat(3) {
            testedHandler.tryToConsumeItems()
        }

        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(2)
    }

    @Test
    fun `M clear pending queue and stop processor W clearAndStopProcessing() { pending items }`() {
        // Given
        createFakeSnapshotItemWithDelayMs(1)
        createFakeSnapshotItemWithDelayMs(2)
        createFakeSnapshotItemWithDelayMs(3)

        // When
        testedHandler.clearAndStopProcessingQueue()

        // Then
        assertThat(testedHandler.recordedDataQueue).isEmpty()
        verify(spyExecutorService).shutdown()
    }

    @Test
    fun `M handle concurrency W clearAndStopProcessing() { pending items }`(
        @Mock mockSnapshotItem1: SnapshotRecordedDataQueueItem,
        @Mock mockSnapshotItem2: SnapshotRecordedDataQueueItem
    ) {
        // Given

        val itemRumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 1)

        whenever(mockSnapshotItem1.recordedQueuedItemContext).thenReturn(itemRumContextData)
        whenever(mockSnapshotItem1.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSnapshotItem1.nodes).thenReturn(fakeNodeData)
        whenever(mockSnapshotItem1.isValid()).thenReturn(true)
        whenever(mockSnapshotItem1.isReady()).thenReturn(false)

        whenever(mockSnapshotItem2.recordedQueuedItemContext).thenReturn(itemRumContextData)
        whenever(mockSnapshotItem2.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSnapshotItem2.nodes).thenReturn(fakeNodeData)
        whenever(mockSnapshotItem2.isValid()).thenReturn(true)
        whenever(mockSnapshotItem2.isReady()).thenReturn(false)

        testedHandler.recordedDataQueue.offer(mockSnapshotItem1)
        testedHandler.recordedDataQueue.offer(mockSnapshotItem2)

        // When
        val countDownLatch = CountDownLatch(3)
        assertDoesNotThrow {
            Thread {
                testedHandler.tryToConsumeItems()
                countDownLatch.countDown()
            }.start()
            Thread {
                testedHandler.clearAndStopProcessingQueue()
                countDownLatch.countDown()
            }.start()
            Thread {
                testedHandler.tryToConsumeItems()
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await(1, TimeUnit.SECONDS)

        // Then
        verifyNoInteractions(mockProcessor)
        assertThat(testedHandler.recordedDataQueue).isEmpty()
    }

    // region resourceItem

    @Test
    fun `M do nothing W addResourceItem { cannot get RUM context }`() {
        // Given
        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(null)

        // When
        val result = testedHandler.addResourceItem(
            fakeIdentifier.toString(),
            fakeApplicationId.toString(),
            ByteArray(0)
        )

        // Then
        assertThat(fakeRecordedDataQueue).isEmpty()
        assertThat(result).isNull()
    }

    @Test
    fun `M insert resource item W addResourceItem`() {
        // Given
        val fakeResourceData = ByteArray(0)

        // When
        val result = testedHandler.addResourceItem(
            fakeIdentifier.toString(),
            fakeApplicationId.toString(),
            fakeResourceData
        ) as ResourceRecordedDataQueueItem

        // Then
        assertThat(fakeRecordedDataQueue.size).isEqualTo(1)
        assertThat(result.recordedQueuedItemContext)
            .isEqualTo(fakeRecordedQueuedItemContext)
        assertThat(result.applicationId)
            .isEqualTo(fakeApplicationId.toString())
        assertThat(result.identifier)
            .isEqualTo(fakeIdentifier.toString())
        assertThat(result.resourceData)
            .isEqualTo(fakeResourceData)
    }

    // endregion

    private fun createFakeSnapshotItemWithDelayMs(delay: Int): SnapshotRecordedDataQueueItem {
        val newRumContext = RecordedQueuedItemContext(
            timestamp = System.currentTimeMillis() + delay,
            newRumContext = fakeRecordedQueuedItemContext.newRumContext
        )

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(newRumContext)

        val item = testedHandler.addSnapshotItem(mockSystemInformation)
        item!!.nodes = fakeNodeData
        return item
    }

    private fun verifySnapshotItemProcessed(item: SnapshotRecordedDataQueueItem) {
        verify(mockProcessor, times(1)).processScreenSnapshots(item)
    }
}
