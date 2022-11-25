/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.data.upload.UploadWorker
import com.datadog.android.core.internal.thread.waitToIdle
import com.datadog.android.core.internal.utils.TAG_DATADOG_UPLOAD
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_NAME
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.model.LogEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.DatadogCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
import org.mockito.quality.Strictness
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

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
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Forgery
    lateinit var fakeThrowable: Throwable

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @StringForgery(regex = "([a-z]{2,8}\\.){1,4}[a-z]{2,8}")
    lateinit var fakeServiceName: String

    @StringForgery
    lateinit var fakeVariant: String

    @BeforeEach
    fun `set up`() {
        mockChoreographerInstance()

        whenever(mockSdkCore.getFeature(LogsFeature.LOGS_FEATURE_NAME)) doReturn mockLogsFeatureScope

        Datadog.initialize(
            appContext.mockInstance,
            Credentials(fakeToken, fakeEnvName, fakeVariant, null),
            Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            ).build(),
            TrackingConsent.GRANTED
        )

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
        Datadog.stop()
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `M log exception W caught with no previous handler`() {
        // Given
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()
        val now = System.currentTimeMillis()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        argumentCaptor<Any> {
            verify(mockLogsFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val logEvent =
                (lastValue as Map<String, Any?>).toMutableMap()
            val timestamp = logEvent.remove("timestamp") as? Long

            assertThat(timestamp).isCloseTo(now, Offset.offset(200))
            assertThat(logEvent).isEqualTo(
                mapOf(
                    "threadName" to currentThread.name,
                    "throwable" to fakeThrowable,
                    "syncWrite" to true,
                    "message" to fakeThrowable.message,
                    "type" to "crash",
                    "loggerName" to DatadogExceptionHandler.LOGGER_NAME
                )
            )
        }
        verifyZeroInteractions(mockPreviousHandler)
    }

    @Test
    fun `M wait for the executor to idle W exception caught`() {
        // Given
        val mockScheduledThreadExecutor: ThreadPoolExecutor = mock()
        (Datadog.globalSdkCore as DatadogCore).coreFeature
            .persistenceExecutorService = mockScheduledThreadExecutor
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        verify(mockScheduledThreadExecutor)
            .waitToIdle(DatadogExceptionHandler.MAX_WAIT_FOR_IDLE_TIME_IN_MS)
        verify(logger.mockDevLogHandler, never()).handleLog(
            Log.WARN,
            DatadogExceptionHandler.EXECUTOR_NOT_IDLED_WARNING_MESSAGE
        )
    }

    @Test
    fun `M log warning message W exception caught { executor could not be idled }`() {
        // Given
        val mockScheduledThreadExecutor: ThreadPoolExecutor = mock {
            whenever(it.taskCount).thenReturn(2)
            whenever(it.completedTaskCount).thenReturn(0)
        }
        (Datadog.globalSdkCore as DatadogCore).coreFeature
            .persistenceExecutorService = mockScheduledThreadExecutor
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
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
        verify(mockWorkManager)
            .enqueueUniqueWork(
                eq(UPLOAD_WORKER_NAME),
                eq(ExistingWorkPolicy.REPLACE),
                argThat<OneTimeWorkRequest> {
                    this.workSpec.workerClassName == UploadWorker::class.java.canonicalName &&
                        this.tags.contains(TAG_DATADOG_UPLOAD)
                }
            )
    }

    @Test
    fun `M log exception W caught { exception with message }`() {
        // Given
        val currentThread = Thread.currentThread()
        val now = System.currentTimeMillis()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        argumentCaptor<Any> {
            verify(mockLogsFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val logEvent =
                (lastValue as Map<String, Any?>).toMutableMap()
            val timestamp = logEvent.remove("timestamp") as? Long

            assertThat(timestamp).isCloseTo(now, Offset.offset(200))
            assertThat(logEvent).isEqualTo(
                mapOf(
                    "threadName" to currentThread.name,
                    "throwable" to fakeThrowable,
                    "syncWrite" to true,
                    "message" to fakeThrowable.message,
                    "type" to "crash",
                    "loggerName" to DatadogExceptionHandler.LOGGER_NAME
                )
            )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    @RepeatedTest(2)
    fun `M log exception W caught { exception without message }`(forge: Forge) {
        // Given
        val currentThread = Thread.currentThread()
        val now = System.currentTimeMillis()
        val throwable = forge.aThrowableWithoutMessage()

        // When
        testedHandler.uncaughtException(currentThread, throwable)

        // Then
        argumentCaptor<Any> {
            verify(mockLogsFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val logEvent =
                (lastValue as Map<String, Any?>).toMutableMap()
            val timestamp = logEvent.remove("timestamp") as? Long

            assertThat(timestamp).isCloseTo(now, Offset.offset(200))
            assertThat(logEvent).isEqualTo(
                mapOf(
                    "threadName" to currentThread.name,
                    "throwable" to throwable,
                    "syncWrite" to true,
                    "message" to "Application crash detected: ${throwable.javaClass.canonicalName}",
                    "type" to "crash",
                    "loggerName" to DatadogExceptionHandler.LOGGER_NAME
                )
            )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M log exception W caught { exception without message or class }`() {
        // Given
        val currentThread = Thread.currentThread()
        val now = System.currentTimeMillis()
        val throwable = object : RuntimeException() {}

        // When
        testedHandler.uncaughtException(currentThread, throwable)

        // Then
        argumentCaptor<Any> {
            verify(mockLogsFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val logEvent =
                (lastValue as Map<String, Any?>).toMutableMap()
            val timestamp = logEvent.remove("timestamp") as? Long

            assertThat(timestamp).isCloseTo(now, Offset.offset(200))
            assertThat(logEvent).isEqualTo(
                mapOf(
                    "threadName" to currentThread.name,
                    "throwable" to throwable,
                    "syncWrite" to true,
                    "message" to "Application crash detected: ${throwable.javaClass.simpleName}",
                    "type" to "crash",
                    "loggerName" to DatadogExceptionHandler.LOGGER_NAME
                )
            )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M log exception W caught on background thread`(forge: Forge) {
        // Given
        val latch = CountDownLatch(1)
        val threadName = forge.anAlphabeticalString()

        // When
        val thread = Thread(
            {
                testedHandler.uncaughtException(Thread.currentThread(), fakeThrowable)
                latch.countDown()
            },
            threadName
        )

        val now = System.currentTimeMillis()
        thread.start()
        latch.await(1, TimeUnit.SECONDS)

        // Then
        argumentCaptor<Any> {
            verify(mockLogsFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val logEvent =
                (lastValue as Map<String, Any?>).toMutableMap()
            val timestamp = logEvent.remove("timestamp") as? Long

            assertThat(timestamp).isCloseTo(now, Offset.offset(200))
            assertThat(logEvent).isEqualTo(
                mapOf(
                    "threadName" to thread.name,
                    "throwable" to fakeThrowable,
                    "syncWrite" to true,
                    "message" to fakeThrowable.message,
                    "type" to "crash",
                    "loggerName" to DatadogExceptionHandler.LOGGER_NAME
                )
            )
        }
        verify(mockPreviousHandler).uncaughtException(thread, fakeThrowable)
    }

    @Test
    fun `M register RUM Error W RumMonitor registered { exception with message }`() {
        // Given
        val currentThread = Thread.currentThread()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        verify(rumMonitor.mockInstance).addCrash(
            fakeThrowable.message!!,
            RumErrorSource.SOURCE,
            fakeThrowable
        )
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    @RepeatedTest(2)
    fun `M register RUM Error W RumMonitor registered { exception without message }`(
        forge: Forge
    ) {
        // Given
        val currentThread = Thread.currentThread()
        val throwable = forge.aThrowableWithoutMessage()

        // When
        testedHandler.uncaughtException(currentThread, throwable)

        // Then
        verify(rumMonitor.mockInstance).addCrash(
            "Application crash detected: ${throwable.javaClass.canonicalName}",
            RumErrorSource.SOURCE,
            throwable
        )
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M register RUM Error W RumMonitor registered { exception without message or class }`() {
        // Given
        val currentThread = Thread.currentThread()
        val throwable = object : RuntimeException() {}

        // When
        testedHandler.uncaughtException(currentThread, throwable)

        // Then
        verify(rumMonitor.mockInstance).addCrash(
            "Application crash detected: ${throwable.javaClass.simpleName}",
            RumErrorSource.SOURCE,
            throwable
        )
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M not add RUM information W no RUM Monitor registered`() {
        // Given
        val currentThread = Thread.currentThread()
        GlobalRum.isRegistered.set(false)
        val now = System.currentTimeMillis()

        // When
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        // Then
        argumentCaptor<Any> {
            verify(mockLogsFeatureScope).sendEvent(capture())

            assertThat(lastValue).isInstanceOf(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val logEvent =
                (lastValue as Map<String, Any?>).toMutableMap()
            val timestamp = logEvent.remove("timestamp") as? Long

            assertThat(timestamp).isCloseTo(now, Offset.offset(200))
            assertThat(logEvent).isEqualTo(
                mapOf(
                    "threadName" to currentThread.name,
                    "throwable" to fakeThrowable,
                    "syncWrite" to true,
                    "message" to fakeThrowable.message,
                    "type" to "crash",
                    "loggerName" to DatadogExceptionHandler.LOGGER_NAME
                )
            )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

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
            exceptionClass.newInstance()
        } else {
            exceptionClass.constructors
                .first {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                }
                .newInstance(anElementFrom("", aWhitespaceString())) as Throwable
        }
    }

    private fun Throwable.asLogError(): LogEvent.Error {
        return LogEvent.Error(
            kind = this.javaClass.canonicalName ?: this.javaClass.simpleName,
            message = this.message,
            stack = this.stackTraceToString()
        )
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val mainLooper = MainLooperTestConfiguration()
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, rumMonitor, mainLooper)
        }
    }
}
