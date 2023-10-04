/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.async

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler.Companion.MAX_DELAY_MS
import com.datadog.android.sessionreplay.internal.processor.RecordedDataProcessor
import com.datadog.android.sessionreplay.internal.processor.RecordedQueuedItemContext
import com.datadog.android.sessionreplay.internal.processor.RumContextDataHandler
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import java.util.Queue
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

    lateinit var spyExecutorService: ExecutorService

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    @Mock
    lateinit var mockTimeProvider: SessionReplayTimeProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeRecordedQueuedItemContext: RecordedQueuedItemContext

    @Forgery
    lateinit var fakeSnapshotQueueItem: SnapshotRecordedDataQueueItem

    @Forgery
    lateinit var fakeTouchEventItem: TouchEventRecordedDataQueueItem

    private lateinit var fakeTouchData: List<MobileSegment.MobileRecord>

    private lateinit var fakeNodeData: List<Node>

    private val snapshotItemCaptor = argumentCaptor<SnapshotRecordedDataQueueItem>()
    private val touchEventItemCaptor = argumentCaptor<TouchEventRecordedDataQueueItem>()

    @BeforeEach
    fun setup(forge: Forge) {
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
            internalLogger = mockInternalLogger
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
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M remove item from queue W tryToConsumeItems() { invalid snapshot item }`() {
        // Given
        val spy = spy(fakeSnapshotQueueItem)
        spy.nodes = emptyList()
        doReturn(false).whenever(spy).isValid()
        testedHandler.recordedDataQueue.offer(spy)

        val spyTimestamp = spy.recordedQueuedItemContext.timestamp
        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(spyTimestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(0)
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M remove item from queue W tryToConsumeItems() { invalid touch event item }`() {
        // Given
        val spy = spy(fakeTouchEventItem)

        doReturn(false).whenever(spy).isValid()
        testedHandler.recordedDataQueue.offer(spy)

        val spyTimestamp = spy.recordedQueuedItemContext.timestamp
        whenever(mockTimeProvider.getDeviceTimestamp())
            .thenReturn(spyTimestamp)

        // When
        testedHandler.tryToConsumeItems()
        spyExecutorService.shutdown()
        spyExecutorService.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(0)
        verifyNoMoreInteractions(mockProcessor)
    }

    @Test
    fun `M do nothing W tryToConsumeItems() { snapshot item not ready }`() {
        // Given
        val spy = spy(fakeSnapshotQueueItem)

        doReturn(true).whenever(spy).isValid()
        doReturn(false).whenever(spy).isReady()

        testedHandler.recordedDataQueue.add(spy)

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
    fun `M consume items in the correct order W tryToConsumeItems() { spawn multiple threads }`() {
        // Given
        val item1 = createFakeSnapshotItemWithDelayMs(1)
        val item2 = createFakeSnapshotItemWithDelayMs(2)
        val item3 = createFakeSnapshotItemWithDelayMs(3)

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
    fun `M not consume items that are not ready W tryToConsumeItems() { some items not ready }`() {
        // Given
        // item1
        val item1RumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 1)

        val item1 = spy(
            SnapshotRecordedDataQueueItem(
                recordedQueuedItemContext = item1RumContextData,
                systemInformation = mockSystemInformation
            )
        )

        item1.nodes = fakeNodeData
        doReturn(true).whenever(item1).isValid()
        doReturn(true).whenever(item1).isReady()

        // item2
        val item2RumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 2)

        val item2 = spy(
            SnapshotRecordedDataQueueItem(
                recordedQueuedItemContext = item2RumContextData,
                systemInformation = mockSystemInformation
            )
        )

        item2.nodes = emptyList()
        doReturn(true).whenever(item2).isValid()
        doReturn(false).whenever(item2).isReady()

        // item3
        val item3RumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 3)

        val item3 = spy(
            SnapshotRecordedDataQueueItem(
                recordedQueuedItemContext = item3RumContextData,
                systemInformation = mockSystemInformation
            )
        )

        item3.nodes = fakeNodeData
        doReturn(true).whenever(item3).isValid()
        doReturn(true).whenever(item3).isReady()

        testedHandler.recordedDataQueue.offer(item1)
        testedHandler.recordedDataQueue.offer(item2)
        testedHandler.recordedDataQueue.offer(item3)

        assertThat(testedHandler.recordedDataQueue.size).isEqualTo(3)
        val item1Time = item1.recordedQueuedItemContext.timestamp

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
    fun `M handle concurrency W clearAndStopProcessing() { pending items }`() {
        // Given
        List(2) {
            val itemRumContextData = fakeRecordedQueuedItemContext.copy(timestamp = 1)
            spy(
                SnapshotRecordedDataQueueItem(
                    recordedQueuedItemContext = itemRumContextData,
                    systemInformation = mockSystemInformation
                )
            ).apply {
                this.nodes = fakeNodeData
                doReturn(true).whenever(this).isValid()
                doReturn(false).whenever(this).isReady()
            }
        }.forEach {
            testedHandler.recordedDataQueue.offer(it)
        }

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
