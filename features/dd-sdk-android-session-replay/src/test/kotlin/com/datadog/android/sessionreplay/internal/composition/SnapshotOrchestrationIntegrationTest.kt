/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotOrchestrationIntegrationTest {

    @Test
    fun `M write completed snapshot W draw signal traverses orchestration boundary`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val scheduler = TestScheduler()
        val expiryScheduler = TestScheduler()
        val clock = CaptureTimeProvider { 0L }
        val executor = mock<ExecutorService>()
        val writer = mock<RecordWriter>()
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(fakeApplicationId, fakeSessionId, tree.scope.value)
        )
        val completionQueue = SnapshotCompletionQueue(
            executorService = executor,
            processor = DefaultSnapshotCompletionProcessor(
                rumContextProvider = rumContextProvider,
                recordWriter = writer,
                internalLogger = mock()
            ),
            internalLogger = mock<InternalLogger>()
        )
        val orchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { _, _ -> CaptureOutput(tree.snapshot, emptyList(), mock()) },
            processor = ImmediateCapturedSnapshotProcessor(),
            consumer = completionQueue,
            timeProvider = clock,
            captureScheduler = scheduler,
            mainThreadExecutor = CaptureMainThreadExecutor { task ->
                task()
                CancellableCaptureWork.NONE
            },
            expiryScheduler = expiryScheduler,
            captureDelayNs = 0
        )
        val listener = CompositionOnDrawListener(mock<View>()) { orchestrator.requestCapture() }

        // When
        orchestrator.start()
        listener.onDraw()
        scheduler.runNext()
        val completionTask = argumentCaptor<Runnable>()
        verify(executor).execute(completionTask.capture())
        completionTask.firstValue.run()

        // Then
        val record = argumentCaptor<EnrichedRecord>()
        verify(writer).write(record.capture(), any())
        assertThat(record.firstValue.applicationId).isEqualTo(fakeApplicationId)
        assertThat(record.firstValue.sessionId).isEqualTo(fakeSessionId)
        assertThat(record.firstValue.viewId).isEqualTo(tree.scope.value)
        assertThat(record.firstValue.records).hasSize(1)
    }

    private class TestScheduler : CaptureTaskScheduler {
        private val tasks = mutableListOf<TestTask>()

        override fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork =
            TestTask(task).also(tasks::add)

        fun runNext() {
            tasks.first { !it.cancelled }.run()
        }
    }

    private class TestTask(
        private val task: () -> Unit
    ) : CancellableCaptureWork {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }

        fun run() {
            task()
            cancelled = true
        }
    }
}
