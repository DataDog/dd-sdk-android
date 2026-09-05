/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.storage.ExposureEventRecordWriter
import com.datadog.android.flags.internal.storage.NoOpRecordWriter
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsFeatureTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockContext: Context

    @StringForgery
    lateinit var fakeApplicationId: String

    private lateinit var testedFeature: FlagsFeature

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockExecutorService

        // Setup mockContext with default release build (flags = 0)
        val applicationInfo = ApplicationInfo()
        whenever(mockContext.applicationInfo) doReturn applicationInfo

        testedFeature = FlagsFeature(
            sdkCore = mockSdkCore,
            flagsConfiguration = FlagsConfiguration.Builder().build()
        )
    }

    // region Lifecycle Methods

    @Test
    fun `M initialize processor and dataWriter W onInitialize`() {
        // Given
        assertThat(testedFeature.processor).isInstanceOf(NoOpEventsProcessor::class.java)
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpRecordWriter::class.java)

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        assertThat(testedFeature.processor).isInstanceOf(ExposureEventsProcessor::class.java)
        assertThat(testedFeature.dataWriter).isInstanceOf(ExposureEventRecordWriter::class.java)
    }

    @Test
    fun `M initialize precomputedRequestFactory W constructor`() {
        // When

        // Then
        assertThat(
            testedFeature.precomputedRequestFactory
        ).isNotNull()
    }

    @Test
    fun `M reset dataWriter to NoOp W onStop`() {
        // Given
        testedFeature.onInitialize(mockContext) // Initialize with real dataWriter
        assertThat(testedFeature.dataWriter).isInstanceOf(ExposureEventRecordWriter::class.java)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpRecordWriter::class.java)
    }

    @Test
    fun `M preserve pending initialization deadlines W onStop()`() {
        // Given
        val timeoutExecutor = mock<ScheduledExecutorService>()
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn timeoutExecutor
        testedFeature.initializationTimeoutScheduler.schedule(2_500L) {}

        // When
        testedFeature.onStop()

        // Then
        verify(timeoutExecutor).shutdown()
    }

    @Test
    fun `M execute accepted timeout W onStop() { scheduling races shutdown }`() {
        // Given
        val timeoutExecutor = BlockingScheduleExecutor()
        whenever(mockSdkCore.createScheduledExecutorService(any())) doReturn timeoutExecutor
        val timeoutExecuted = CountDownLatch(1)
        val scheduleFinished = CountDownLatch(1)
        val scheduleThread = Thread {
            testedFeature.initializationTimeoutScheduler.schedule(0) {
                timeoutExecuted.countDown()
            }
            scheduleFinished.countDown()
        }

        // When
        scheduleThread.start()
        assertThat(timeoutExecutor.scheduleStarted.await(1, TimeUnit.SECONDS)).isTrue()
        val stopThread = Thread { testedFeature.onStop() }
        stopThread.start()
        val releaseThread = Thread {
            timeoutExecutor.shutdownStarted.await(250, TimeUnit.MILLISECONDS)
            timeoutExecutor.releaseSchedule.countDown()
        }
        releaseThread.start()
        val didExecute = timeoutExecuted.await(1, TimeUnit.SECONDS)

        // Then
        timeoutExecutor.releaseSchedule.countDown()
        scheduleThread.join(1_000)
        stopThread.join(1_000)
        releaseThread.join(1_000)
        timeoutExecutor.shutdownNow()
        assertThat(didExecute).isTrue()
        assertThat(scheduleFinished.count).isZero()
        assertThat(scheduleThread.isAlive).isFalse()
        assertThat(stopThread.isAlive).isFalse()
    }

    // endregion

    // region Graceful Mode Policy

    @Test
    fun `M log through internalLogger W logErrorWithPolicy() { release build }`() {
        // Given - release build (not debuggable)
        val releaseAppInfo = ApplicationInfo()
        val releaseContext = mock<Context>()
        whenever(releaseContext.applicationInfo).thenReturn(releaseAppInfo)
        val config = FlagsConfiguration.Builder().build()
        testedFeature = FlagsFeature(mockSdkCore, config)
        testedFeature.onInitialize(releaseContext)

        // When
        testedFeature.logErrorWithPolicy("test message", InternalLogger.Level.ERROR)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.USER),
            argThat { invoke() == "[Datadog Flags] test message" },
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M log through internalLogger W logErrorWithPolicy() { release build, gracefulModeEnabled false }`() {
        // Given - release build should ignore gracefulModeEnabled setting
        val releaseAppInfo = ApplicationInfo()
        val releaseContext = mock<Context>()
        whenever(releaseContext.applicationInfo).thenReturn(releaseAppInfo)
        val config = FlagsConfiguration.Builder()
            .gracefulModeEnabled(false)
            .build()
        testedFeature = FlagsFeature(mockSdkCore, config)
        testedFeature.onInitialize(releaseContext)

        // When
        testedFeature.logErrorWithPolicy("test message", InternalLogger.Level.ERROR)

        // Then - should still use graceful policy, not crash
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.USER),
            argThat { invoke() == "[Datadog Flags] test message" },
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M log to Android Logcat W logErrorWithPolicy() { debug build, graceful enabled }`() {
        // Given - debug build with gracefulModeEnabled=true
        val debugAppInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_DEBUGGABLE }
        val debugContext = mock<Context>()
        whenever(debugContext.applicationInfo).thenReturn(debugAppInfo)
        val config = FlagsConfiguration.Builder()
            .gracefulModeEnabled(true)
            .build()
        testedFeature = FlagsFeature(mockSdkCore, config)
        testedFeature.onInitialize(debugContext)

        // When
        testedFeature.logErrorWithPolicy("test message", InternalLogger.Level.ERROR)

        // Then - uses android.util.Log.e which can't be easily verified in unit tests
        // Just verify it doesn't crash and doesn't use internalLogger
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M crash W logErrorWithPolicy() { debug build, graceful disabled, shouldCrash true }`() {
        // Given - debug build with strict mode
        val debugAppInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_DEBUGGABLE }
        val debugContext = mock<Context>()
        whenever(debugContext.applicationInfo).thenReturn(debugAppInfo)
        val config = FlagsConfiguration.Builder()
            .gracefulModeEnabled(false)
            .build()
        testedFeature = FlagsFeature(mockSdkCore, config)
        testedFeature.onInitialize(debugContext)

        // When/Then
        assertThrows<IllegalStateException> {
            testedFeature.logErrorWithPolicy(
                "test message",
                InternalLogger.Level.ERROR,
                shouldCrashInStrict = true
            )
        }
    }

    @Test
    fun `M log to Android Logcat W logErrorWithPolicy() { debug build, graceful disabled, shouldCrash false }`() {
        // Given - debug build with strict mode but shouldCrashInStrict=false
        val debugAppInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_DEBUGGABLE }
        val debugContext = mock<Context>()
        whenever(debugContext.applicationInfo).thenReturn(debugAppInfo)
        val config = FlagsConfiguration.Builder()
            .gracefulModeEnabled(false)
            .build()
        testedFeature = FlagsFeature(mockSdkCore, config)
        testedFeature.onInitialize(debugContext)

        // When
        assertDoesNotThrow {
            testedFeature.logErrorWithPolicy(
                "test message",
                InternalLogger.Level.ERROR,
                shouldCrashInStrict = false
            )
        }

        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region General

    @Test
    fun `M have correct feature name W constructor`() {
        // Then
        assertThat(testedFeature.name).isEqualTo(FLAGS_FEATURE_NAME)
    }

    // endregion
}

private class BlockingScheduleExecutor : ScheduledThreadPoolExecutor(
    1,
    RejectedExecutionHandler { _, _ -> }
) {
    val scheduleStarted = CountDownLatch(1)
    val releaseSchedule = CountDownLatch(1)
    val shutdownStarted = CountDownLatch(1)

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        scheduleStarted.countDown()
        check(releaseSchedule.await(2, TimeUnit.SECONDS))
        return super.schedule(command, delay, unit)
    }

    override fun shutdown() {
        shutdownStarted.countDown()
        super.shutdown()
    }
}
