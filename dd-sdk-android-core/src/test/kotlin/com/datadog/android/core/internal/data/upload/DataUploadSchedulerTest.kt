/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.system.SystemInfo
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataUploadSchedulerTest {

    private lateinit var testedScheduler: DataUploadScheduler

    @Mock
    lateinit var mockExecutor: ScheduledThreadPoolExecutor

    @StringForgery
    lateinit var fakeFeatureName: String

    @Mock
    lateinit var mockUploadSchedulerStrategy: UploadSchedulerStrategy

    @IntForgery(min = 1, max = 4)
    var fakeMaxBatchesPerJob: Int = 0

    @BeforeEach
    fun `set up`() {
        testedScheduler = DataUploadScheduler(
            featureName = fakeFeatureName,
            storage = mock(),
            dataUploader = mock(),
            contextProvider = mock(),
            networkInfoProvider = mock(),
            systemInfoProvider = mock(),
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            scheduledThreadPoolExecutor = mockExecutor,
            internalLogger = mock()
        )
    }

    @Test
    fun `when start it will execute a runnable`() {
        // When
        testedScheduler.startScheduling()

        // Then
        verify(mockExecutor).schedule(any(), eq(0L), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not cancel the pending upload W stopScheduling()`() {
        // Given - a local scheduler so the collaborators the upload task exercises can be stubbed
        val mockStorage: Storage = mock()
        val mockNetworkInfoProvider: NetworkInfoProvider = mock()
        val mockSystemInfoProvider: SystemInfoProvider = mock()
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn NetworkInfo(
            connectivity = NetworkInfo.Connectivity.NETWORK_WIFI
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn SystemInfo(
            batteryFullOrCharging = true,
            batteryLevel = 100,
            powerSaveMode = false,
            onExternalPowerSource = true
        )
        whenever(mockStorage.readNextBatch()) doReturn null
        whenever(
            mockUploadSchedulerStrategy.getMsDelayUntilNextUpload(any(), any(), anyOrNull(), anyOrNull())
        ) doReturn RESCHEDULE_DELAY_MS
        val mockFuture: ScheduledFuture<Any> = mock()
        whenever(mockExecutor.schedule(any(), any(), any())) doReturn mockFuture

        testedScheduler = DataUploadScheduler(
            featureName = fakeFeatureName,
            storage = mockStorage,
            dataUploader = mock(),
            contextProvider = mock(),
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            scheduledThreadPoolExecutor = mockExecutor,
            internalLogger = mock()
        )
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.startScheduling()
        verify(mockExecutor).schedule(runnableCaptor.capture(), eq(0L), eq(TimeUnit.MILLISECONDS))
        // run the captured task once, so a next cycle gets scheduled via scheduleNext()
        runnableCaptor.firstValue.run()

        // When
        testedScheduler.stopScheduling()

        // Then - cancel(false) on an already-executing future would only poison its terminal
        // state without actually stopping it, so we never call it; the stopped flag alone is
        // what prevents further work
        verify(mockFuture, never()).cancel(any())
    }

    @Test
    fun `M not run the upload again W stopScheduling()`() {
        // Given - a real executor and real DataUploadTask; stopScheduling() never cancels
        // the queued future, so this proves the stopped flag alone is enough to stop the loop.
        val realExecutor = ScheduledThreadPoolExecutor(1)
        try {
            val mockStorage: Storage = mock()
            val mockNetworkInfoProvider: NetworkInfoProvider = mock()
            val mockSystemInfoProvider: SystemInfoProvider = mock()
            whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn NetworkInfo(
                connectivity = NetworkInfo.Connectivity.NETWORK_WIFI
            )
            whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn SystemInfo(
                batteryFullOrCharging = true,
                batteryLevel = 100,
                powerSaveMode = false,
                onExternalPowerSource = true
            )
            whenever(
                mockUploadSchedulerStrategy.getMsDelayUntilNextUpload(any(), any(), anyOrNull(), anyOrNull())
            ) doReturn RESCHEDULE_DELAY_MS
            // The reschedule delay is short, so cycles fire in quick succession - a fixed-time
            // window can't tell "exactly one ran" from "many ran". Count them instead.
            val firstCycle = CountDownLatch(1)
            val cycleCount = AtomicInteger(0)
            whenever(mockStorage.readNextBatch()) doAnswer {
                cycleCount.incrementAndGet()
                firstCycle.countDown()
                null
            }

            val realScheduler = DataUploadScheduler(
                featureName = fakeFeatureName,
                storage = mockStorage,
                dataUploader = mock(),
                contextProvider = mock(),
                networkInfoProvider = mockNetworkInfoProvider,
                systemInfoProvider = mockSystemInfoProvider,
                uploadSchedulerStrategy = mockUploadSchedulerStrategy,
                maxBatchesPerJob = 1,
                scheduledThreadPoolExecutor = realExecutor,
                internalLogger = mock()
            )

            // When
            realScheduler.startScheduling()
            assertThat(firstCycle.await(1, TimeUnit.SECONDS)).isTrue()
            realScheduler.stopScheduling()
            val countAtStop = cycleCount.get()

            // Then - scheduleNext() re-checks `stopped` before arming, so at most one already
            // in-flight cycle can still complete; nothing new is ever scheduled after that.
            val noFurtherCycle = CountDownLatch(1)
            assertThat(noFurtherCycle.await(500, TimeUnit.MILLISECONDS)).isFalse()
            assertThat(cycleCount.get()).isLessThanOrEqualTo(countAtStop + 1)
        } finally {
            realExecutor.shutdownNow()
            realExecutor.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `M reschedule with the returned delay W the upload task runs`(
        @LongForgery(min = 1) fakeDelayMs: Long
    ) {
        // Given
        val stubTask: Callable<Long> = mock()
        whenever(stubTask.call()) doReturn fakeDelayMs
        testedScheduler = DataUploadScheduler(
            featureName = fakeFeatureName,
            storage = mock(),
            dataUploader = mock(),
            contextProvider = mock(),
            networkInfoProvider = mock(),
            systemInfoProvider = mock(),
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            scheduledThreadPoolExecutor = mockExecutor,
            internalLogger = mock(),
            runnable = stubTask
        )
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.startScheduling()
        verify(mockExecutor).schedule(runnableCaptor.capture(), eq(0L), eq(TimeUnit.MILLISECONDS))

        // When
        runnableCaptor.firstValue.run()

        // Then
        verify(mockExecutor).schedule(any(), eq(fakeDelayMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M does not run nor reschedule W the task runs {stopped}`() {
        // Given
        val mockTask: Callable<Long> = mock()
        testedScheduler = DataUploadScheduler(
            featureName = fakeFeatureName,
            storage = mock(),
            dataUploader = mock(),
            contextProvider = mock(),
            networkInfoProvider = mock(),
            systemInfoProvider = mock(),
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            scheduledThreadPoolExecutor = mockExecutor,
            internalLogger = mock(),
            runnable = mockTask
        )
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.startScheduling()
        verify(mockExecutor).schedule(runnableCaptor.capture(), eq(0L), eq(TimeUnit.MILLISECONDS))
        testedScheduler.stopScheduling()

        // When
        runnableCaptor.firstValue.run()

        // Then - only the initial schedule() from startScheduling() happened, no reschedule
        verifyNoInteractions(mockTask)
        verify(mockExecutor, times(1)).schedule(any(), any(), any())
    }

    @Test
    fun `M does not reschedule W stopScheduling() during an upload`(
        @LongForgery(min = 1) fakeDelayMs: Long
    ) {
        // Given - call() is the long-running call that occupies the window a concurrent
        // stopScheduling() could land in; stubbing it to call stopScheduling() itself
        // deterministically simulates that race landing mid-cycle, without real threads/timing
        val stubTask: Callable<Long> = mock()
        testedScheduler = DataUploadScheduler(
            featureName = fakeFeatureName,
            storage = mock(),
            dataUploader = mock(),
            contextProvider = mock(),
            networkInfoProvider = mock(),
            systemInfoProvider = mock(),
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            scheduledThreadPoolExecutor = mockExecutor,
            internalLogger = mock(),
            runnable = stubTask
        )
        whenever(stubTask.call()) doAnswer {
            testedScheduler.stopScheduling()
            fakeDelayMs
        }
        val runnableCaptor = argumentCaptor<Runnable>()
        testedScheduler.startScheduling()
        verify(mockExecutor).schedule(runnableCaptor.capture(), eq(0L), eq(TimeUnit.MILLISECONDS))

        // When
        runnableCaptor.firstValue.run()

        // Then - only the initial schedule() from startScheduling() happened, no reschedule
        verify(mockExecutor, times(1)).schedule(any(), any(), any())
    }

    companion object {
        private const val RESCHEDULE_DELAY_MS = 10L
    }
}
