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
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.core.internal.thread.waitToIdle
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.TAG_DATADOG_UPLOAD
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_NAME
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogEventAssert.Companion.assertThat
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.log.model.LogEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
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
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogExceptionHandlerTest {

    var originalHandler: Thread.UncaughtExceptionHandler? = null

    lateinit var testedHandler: DatadogExceptionHandler

    @Mock
    lateinit var mockPreviousHandler: Thread.UncaughtExceptionHandler

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockLogWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Mock
    lateinit var mockPackageVersionProvider: AppVersionProvider

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    @StringForgery(regex = "[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @StringForgery(regex = "([a-z]{2,8}\\.){1,4}[a-z]{2,8}")
    lateinit var fakeServiceName: String

    @StringForgery(regex = "[0-9](\\.[0-9]{1,2}){1,3}")
    lateinit var fakeSdkVersion: String

    @StringForgery
    lateinit var fakeVariant: String

    @BeforeEach
    fun `set up`() {
        mockChoreographerInstance()

        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockPackageVersionProvider.version) doReturn appContext.fakeVersionName

        Datadog.initialize(
            appContext.mockInstance,
            Credentials(fakeToken, fakeEnvName, fakeVariant, null),
            Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true,
                sessionReplayEnabled = true
            ).build(),
            TrackingConsent.GRANTED
        )

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(mockPreviousHandler)
        testedHandler = DatadogExceptionHandler(
            DatadogLogGenerator(
                fakeServiceName,
                DatadogExceptionHandler.LOGGER_NAME,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockPackageVersionProvider
            ),
            writer = mockLogWriter,
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
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        val now = System.currentTimeMillis()
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<LogEvent> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage(fakeThrowable.message!!)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasError(fakeThrowable.asLogError())
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasDateAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${appContext.fakeVersionName}",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
        }
        verifyZeroInteractions(mockPreviousHandler)
    }

    @Test
    fun `M wait for the executor to idle W exception caught`() {
        val mockScheduledThreadExecutor: ThreadPoolExecutor = mock()
        (Datadog.globalSDKCore as DatadogCore).coreFeature
            .persistenceExecutorService = mockScheduledThreadExecutor
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        verify(mockScheduledThreadExecutor)
            .waitToIdle(DatadogExceptionHandler.MAX_WAIT_FOR_IDLE_TIME_IN_MS)
        verify(logger.mockDevLogHandler, never()).handleLog(
            Log.WARN,
            DatadogExceptionHandler.EXECUTOR_NOT_IDLED_WARNING_MESSAGE
        )
    }

    @Test
    fun `M log warning message W exception caught { executor could not be idled }`() {
        val mockScheduledThreadExecutor: ThreadPoolExecutor = mock {
            whenever(it.taskCount).thenReturn(2)
            whenever(it.completedTaskCount).thenReturn(0)
        }
        (Datadog.globalSDKCore as DatadogCore).coreFeature
            .persistenceExecutorService = mockScheduledThreadExecutor
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
            DatadogExceptionHandler.EXECUTOR_NOT_IDLED_WARNING_MESSAGE
        )
    }

    @Test
    fun `M schedule the worker W logging an exception`() {
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

        testedHandler.uncaughtException(currentThread, fakeThrowable)

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
        val currentThread = Thread.currentThread()
        val now = System.currentTimeMillis()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<LogEvent> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage(fakeThrowable.message!!)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasError(fakeThrowable.asLogError())
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasDateAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${appContext.fakeVersionName}",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    @RepeatedTest(2)
    fun `M log exception W caught { exception without message }`(forge: Forge) {
        val currentThread = Thread.currentThread()
        val now = System.currentTimeMillis()
        val throwable = forge.aThrowableWithoutMessage()

        testedHandler.uncaughtException(currentThread, throwable)

        argumentCaptor<LogEvent> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage("Application crash detected: ${throwable.javaClass.canonicalName}")
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasError(throwable.asLogError())
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasDateAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${appContext.fakeVersionName}",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M log exception W caught { exception without message or class }`() {
        val currentThread = Thread.currentThread()

        val now = System.currentTimeMillis()

        val throwable = object : RuntimeException() {}

        testedHandler.uncaughtException(currentThread, throwable)

        argumentCaptor<LogEvent> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage("Application crash detected: ${throwable.javaClass.simpleName}")
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasError(throwable.asLogError())
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasDateAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${appContext.fakeVersionName}",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M log exception W caught on background thread`(forge: Forge) {
        val latch = CountDownLatch(1)
        val threadName = forge.anAlphabeticalString()
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

        argumentCaptor<LogEvent> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(threadName)
                .hasMessage(fakeThrowable.message!!)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasError(fakeThrowable.asLogError())
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasDateAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${appContext.fakeVersionName}",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
        }
        verify(mockPreviousHandler).uncaughtException(thread, fakeThrowable)
    }

    @Test
    fun `M add current span information W tracer is active`(
        @StringForgery operation: String
    ) {
        val currentThread = Thread.currentThread()
        val tracer = AndroidTracer.Builder().build()
        val span = tracer.buildSpan(operation).start()
        tracer.activateSpan(span)
        GlobalTracer.registerIfAbsent(tracer)

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<LogEvent> {
            verify(mockLogWriter).write(capture())

            assertThat(lastValue)
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.DD_TRACE_ID to tracer.traceId,
                        LogAttributes.DD_SPAN_ID to tracer.spanId,
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
        }
        Datadog.stop()
    }

    @Test
    fun `M register RUM Error W RumMonitor registered { exception with message }`() {
        val currentThread = Thread.currentThread()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

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
        val currentThread = Thread.currentThread()
        val throwable = forge.aThrowableWithoutMessage()

        testedHandler.uncaughtException(currentThread, throwable)

        verify(rumMonitor.mockInstance).addCrash(
            "Application crash detected: ${throwable.javaClass.canonicalName}",
            RumErrorSource.SOURCE,
            throwable
        )
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M register RUM Error W RumMonitor registered { exception without message or class }`() {
        val currentThread = Thread.currentThread()
        val throwable = object : RuntimeException() {}

        testedHandler.uncaughtException(currentThread, throwable)

        verify(rumMonitor.mockInstance).addCrash(
            "Application crash detected: ${throwable.javaClass.simpleName}",
            RumErrorSource.SOURCE,
            throwable
        )
        verify(mockPreviousHandler).uncaughtException(currentThread, throwable)
    }

    @Test
    fun `M not add RUM information W no RUM Monitor registered`() {
        val currentThread = Thread.currentThread()
        GlobalRum.isRegistered.set(false)

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<LogEvent> {
            verify(mockLogWriter).write(capture())

            assertThat(lastValue)
                .hasExactlyAttributes(emptyMap())
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
