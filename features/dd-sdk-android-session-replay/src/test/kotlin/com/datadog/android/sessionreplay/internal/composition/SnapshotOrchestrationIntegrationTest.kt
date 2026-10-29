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
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.lang.ref.Reference
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class SnapshotOrchestrationIntegrationTest {

    @Test
    fun `M write completed snapshot W onDraw() { draw signal traverses orchestration boundary }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @Forgery fakeTree: CompositionTestTree
    ) {
        // Given
        val fakeFixture = RecordingFixture(fakeApplicationId, fakeSessionId, fakeTree)

        // When
        fakeFixture.orchestrator.start()
        fakeFixture.listener.onDraw()
        Reference.reachabilityFence(fakeFixture.mockView)
        fakeFixture.captureScheduler.runNext()
        fakeFixture.runCompletion()

        // Then
        val record = argumentCaptor<EnrichedRecord>()
        verify(fakeFixture.mockWriter).write(record.capture(), isA())
        assertThat(record.firstValue.applicationId).isEqualTo(fakeApplicationId)
        assertThat(record.firstValue.sessionId).isEqualTo(fakeSessionId)
        assertThat(record.firstValue.viewId).isEqualTo(fakeFixture.fakeTree.scope.value)
        assertThat(record.firstValue.records).hasSize(1)
    }

    private class RecordingFixture(
        fakeApplicationId: String,
        fakeSessionId: String,
        val fakeTree: CompositionTestTree
    ) {
        val captureScheduler = TestScheduler()
        private val mockExecutor: ExecutorService = mock()
        val mockWriter: RecordWriter = mock()
        private val mockRumContextProvider: RumContextProvider = mock {
            on { getRumContext() } doReturn
                SessionReplayRumContext(fakeApplicationId, fakeSessionId, fakeTree.scope.value)
        }
        private val completionQueue = SnapshotCompletionQueue(
            executorService = mockExecutor,
            processor = DefaultSnapshotCompletionProcessor(mockRumContextProvider, mockWriter, mock()),
            internalLogger = mock<InternalLogger>()
        )
        val orchestrator = SnapshotCaptureOrchestrator(
            producer = CapturedSnapshotProducer { fakeTree.snapshot },
            processor = ImmediateCapturedSnapshotProcessor(),
            consumer = completionQueue,
            timeProvider = CaptureTimeProvider { 0L },
            captureScheduler = captureScheduler,
            mainThreadExecutor = CaptureMainThreadExecutor { task -> task(); CancellableCaptureWork.NONE },
            expiryScheduler = TestScheduler(),
            captureDelayNs = 0
        )
        val mockView: View = mock()
        val listener = CompositionOnDrawListener(mockView) { orchestrator.requestCapture() }

        fun runCompletion() {
            val fakeCompletion = argumentCaptor<Runnable>()
            verify(mockExecutor).execute(fakeCompletion.capture())
            fakeCompletion.firstValue.run()
        }
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
