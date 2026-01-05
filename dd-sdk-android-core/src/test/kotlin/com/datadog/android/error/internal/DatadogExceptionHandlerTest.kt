/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.UploadWorker
import com.datadog.android.core.feature.event.JvmCrash
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.thread.waitToIdle
import com.datadog.android.core.internal.utils.TAG_DATADOG_UPLOAD
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_NAME
import com.datadog.android.internal.tests.stub.StubTimeProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ThreadPoolExecutor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogExceptionHandlerTest {

    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    lateinit var testedHandler: DatadogExceptionHandler

    @Mock
    lateinit var mockPreviousHandler: Thread.UncaughtExceptionHandler

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    lateinit var stubTimeProvider: StubTimeProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Forgery
    lateinit var fakeThrowable: Throwable

    @StringForgery
    lateinit var fakeInstanceName: String

    @LongForgery(min = 0L)
    var fakeElapsedTimeNs = 0L

    @BeforeEach
    fun `set up`() {
        stubTimeProvider = StubTimeProvider(elapsedTimeNs = fakeElapsedTimeNs)

        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn mockLogsFeatureScope
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.name) doReturn fakeInstanceName
        whenever(mockSdkCore.timeProvider) doReturn stubTimeProvider

        CoreFeature.disableKronosBackgroundSync = true

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(mockPreviousHandler)
        testedHandler = DatadogExceptionHandler(
            sdkCore = mockSdkCore,
            appContext = appContext.mockInstance
        )
        testedHandler.register()
    }

    @AfterEach
    fun `tear down`() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)
        Datadog.stopInstance()
    }

    @Test
    fun `M do not send exception to logs W uncaughtException() {no previous handler}`() {
        // Given
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        verifyNoInteractions(mockLogsFeatureScope)
    }

    @Test
    fun `M do not send exception to logs W uncaughtException() {previous handler}`() {
        // Given
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        verifyNoInteractions(mockLogsFeatureScope)
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    // endregion

    @Test
    fun `M wait for the executor to idle W exception caught`() {
        // Given
        val mockScheduledThreadExecutor: ThreadPoolExecutor = mock()
        whenever(mockSdkCore.getPersistenceExecutorService()) doReturn mockScheduledThreadExecutor
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        verify(mockScheduledThreadExecutor)
            .waitToIdle(DatadogExceptionHandler.MAX_WAIT_FOR_IDLE_TIME_IN_MS, mockInternalLogger, stubTimeProvider)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogExceptionHandler.EXECUTOR_NOT_IDLED_WARNING_MESSAGE,
            mode = never()
        )
    }

    @Test
    fun `M log warning message W exception caught { executor could not be idled }`() {
        // Given
        val mockScheduledThreadExecutor: ThreadPoolExecutor = mock {
            whenever(it.taskCount).thenReturn(2)
            whenever(it.completedTaskCount).thenReturn(0)
        }
        whenever(mockSdkCore.getPersistenceExecutorService()) doReturn mockScheduledThreadExecutor
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        val timeoutNs = DatadogExceptionHandler.MAX_WAIT_FOR_IDLE_TIME_IN_MS * 1_000_000L
        whenever(mockTimeProvider.getDeviceElapsedTimeNanos())
            .thenReturn(fakeElapsedTimeNs)
            .thenReturn(fakeElapsedTimeNs + timeoutNs + 1)
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogExceptionHandler.EXECUTOR_NOT_IDLED_WARNING_MESSAGE
        )
    }

    @Test
    fun `M schedule the worker W logging an exception`() {
        // Given
        whenever(
            mockWorkManager.enqueueUniqueWork(
                ArgumentMatchers.anyString(),
                any(),
                any<OneTimeWorkRequest>()
            )
        ) doReturn mock()

        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        argumentCaptor<OneTimeWorkRequest> {
            verify(mockWorkManager).enqueueUniqueWork(
                eq(UPLOAD_WORKER_NAME),
                eq(ExistingWorkPolicy.REPLACE),
                capture()
            )
            val workSpec = lastValue.workSpec
            assertThat(workSpec.workerClassName).isEqualTo(UploadWorker::class.java.canonicalName)
            assertThat(workSpec.input.getString(UploadWorker.DATADOG_INSTANCE_NAME)).isEqualTo(fakeInstanceName)
            assertThat(lastValue.tags).contains("$TAG_DATADOG_UPLOAD/$fakeInstanceName")
        }
    }

    // region Forward to RUM

    @Test
    fun `M log dev info W caught exception { no RUM feature registered }`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogExceptionHandler.MISSING_RUM_FEATURE_INFO
        )
    }

    @Test
    fun `M register RUM Error W RUM feature is registered { exception with message }`() {
        // Given
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        argumentCaptor<Any> {
            verify(mockRumFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(JvmCrash.Rum::class.java)

            val crashEvent = lastValue as JvmCrash.Rum

            assertThat(crashEvent.throwable).isSameAs(fakeThrowable)
            assertThat(crashEvent.message).isEqualTo(fakeThrowable.message)
            with(crashEvent.threads) {
                assertThat(this).isNotEmpty
                assertThat(filter { it.crashed }).hasSize(1)
                val crashedThread = first { it.crashed }
                assertThat(crashedThread.name).isEqualTo(currentThread.name)
                assertThat(crashedThread.stack).isEqualTo(fakeThrowable.loggableStackTrace())
                val nonCrashedThreadNames = filterNot { it.crashed }.map { it.name }
                assertThat(nonCrashedThreadNames).isNotEmpty
                assertThat(nonCrashedThreadNames).doesNotContain(crashedThread.name)
            }
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    @RepeatedTest(2)
    fun `M register RUM Error W RUM feature is registered { exception without message }`(
        forge: Forge
    ) {
        // Given
        val currentThread = Thread.currentThread()
        val throwable = forge.aThrowableWithoutMessage()

        // When
        testedHandler.uncaughtException(currentThread, throwable)

        // Then
        argumentCaptor<Any> {
            verify(mockRumFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(JvmCrash.Rum::class.java)

            val crashEvent = lastValue as JvmCrash.Rum

            assertThat(crashEvent.throwable).isSameAs(throwable)
            assertThat(crashEvent.message)
                .isEqualTo("Application crash detected: ${throwable.javaClass.canonicalName}")
            with(crashEvent.threads) {
                assertThat(this).isNotEmpty
                assertThat(filter { it.crashed }).hasSize(1)
                val crashedThread = first { it.crashed }
                assertThat(crashedThread.name).isEqualTo(currentThread.name)
                assertThat(crashedThread.stack).isEqualTo(throwable.loggableStackTrace())
                val nonCrashedThreadNames = filterNot { it.crashed }.map { it.name }
                assertThat(nonCrashedThreadNames).isNotEmpty
                assertThat(nonCrashedThreadNames).doesNotContain(crashedThread.name)
            }
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M register RUM Error W RUM feature is registered { exception without message or class }`() {
        // Given
        val currentThread = Thread.currentThread()
        val throwable = object : RuntimeException() {}

        // When
        testedHandler.uncaughtException(currentThread, throwable)

        // Then
        argumentCaptor<Any> {
            verify(mockRumFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(JvmCrash.Rum::class.java)

            val crashEvent = lastValue as JvmCrash.Rum

            assertThat(crashEvent.throwable).isSameAs(throwable)
            assertThat(crashEvent.message)
                .isEqualTo("Application crash detected: ${throwable.javaClass.simpleName}")
            with(crashEvent.threads) {
                assertThat(this).isNotEmpty
                assertThat(filter { it.crashed }).hasSize(1)
                val crashedThread = first { it.crashed }
                assertThat(crashedThread.name).isEqualTo(currentThread.name)
                assertThat(crashedThread.stack).isEqualTo(throwable.loggableStackTrace())
                val nonCrashedThreadNames = filterNot { it.crashed }.map { it.name }
                assertThat(nonCrashedThreadNames).isNotEmpty
                assertThat(nonCrashedThreadNames).doesNotContain(crashedThread.name)
            }
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    // endregion

    private fun Forge.aThrowableWithoutMessage(): Throwable {
        val exceptionClass = anElementFrom(
            IOException::class.java,
            IllegalStateException::class.java,
            UnknownError::class.java,
            ArrayIndexOutOfBoundsException::class.java,
            NullPointerException::class.java,
            UnsupportedOperationException::class.java,
            FileNotFoundException::class.java
        )

        return if (aBool()) {
            exceptionClass.getDeclaredConstructor().newInstance()
        } else {
            exceptionClass.constructors
                .first {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                }
                .newInstance(anElementFrom("", aWhitespaceString())) as Throwable
        }
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
