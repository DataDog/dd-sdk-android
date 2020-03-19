package com.datadog.android.core.internal.data.upload

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DataUploadSchedulerTest {

    @Mock
    lateinit var mockScheduledThreadPoolExecutor: ScheduledThreadPoolExecutor

    lateinit var dataUploadScheduler: DataUploadScheduler

    @BeforeEach
    fun `set up`() {
        dataUploadScheduler = DataUploadScheduler(
            mock(),
            mock(),
            mock(),
            mock(),
            mockScheduledThreadPoolExecutor
        )
    }

    @Test
    fun `when start it will schedule a runnable`() {
        // when
        dataUploadScheduler.startScheduling()

        // then
        verify(mockScheduledThreadPoolExecutor).schedule(
            any(),
            eq(DataUploadRunnable.DEFAULT_DELAY),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `when stop it will try to remove the scheduled runnable`() {
        // given
        dataUploadScheduler.startScheduling()

        // when
        dataUploadScheduler.stopScheduling()

        // then
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockScheduledThreadPoolExecutor).schedule(
            argumentCaptor.capture(),
            eq(DataUploadRunnable.DEFAULT_DELAY),
            eq(TimeUnit.MILLISECONDS)
        )
        verify(mockScheduledThreadPoolExecutor).remove(argumentCaptor.firstValue)
    }
}
