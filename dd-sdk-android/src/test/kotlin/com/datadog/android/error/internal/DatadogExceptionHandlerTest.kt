/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.upload.UploadWorker
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.utils.TAG_DATADOG_UPLOAD
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_NAME
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.noop.NoopTracerFactory
import io.opentracing.util.GlobalTracer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
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
    lateinit var mockLogWriter: Writer<Log>

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeToken: String

    @RegexForgery("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
    lateinit var fakeEnvName: String

    @BeforeEach
    fun `set up`() {
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        val mockContext: Application = mockContext()
        val config = DatadogConfig.Builder(fakeToken, fakeEnvName).build()
        Datadog.initialize(mockContext(), config)

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(mockPreviousHandler)
        testedHandler = DatadogExceptionHandler(
            LogGenerator(
                CoreFeature.serviceName,
                DatadogExceptionHandler.LOGGER_NAME,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                CoreFeature.envName,
                CoreFeature.packageVersion
            ),
            writer = mockLogWriter,
            appContext = mockContext
        )
        testedHandler.register()
    }

    @AfterEach
    fun `tear down`() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)
        Datadog.invokeMethod("stop")
        GlobalTracer.get().setFieldValue("isRegistered", false)
        GlobalTracer::class.java.setStaticValue("tracer", NoopTracerFactory.create())
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `M log exception W caught with no previous handler`(forge: Forge) {
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        val now = System.currentTimeMillis()
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage("Application crash detected")
                .hasLevel(Log.CRASH)
                .hasThrowable(fakeThrowable)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasTimestampAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${BuildConfig.VERSION_NAME}"
                    )
                )
                .hasExactlyAttributes(emptyMap())
        }
        verifyZeroInteractions(mockPreviousHandler)
    }

    @Test
    fun `M schedule the worker W logging an exception`(forge: Forge) {
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        val now = System.currentTimeMillis()
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
    fun `M log exception W caught`(forge: Forge) {
        val currentThread = Thread.currentThread()

        val now = System.currentTimeMillis()
        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage("Application crash detected")
                .hasLevel(Log.CRASH)
                .hasThrowable(fakeThrowable)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasTimestampAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${BuildConfig.VERSION_NAME}"
                    )
                )
                .hasExactlyAttributes(emptyMap())
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
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

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(threadName)
                .hasMessage("Application crash detected")
                .hasLevel(Log.CRASH)
                .hasThrowable(fakeThrowable)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasTimestampAround(now)
                .hasExactlyTags(
                    listOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:${BuildConfig.VERSION_NAME}"
                    )
                )
                .hasExactlyAttributes(emptyMap())
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

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())

            assertThat(lastValue)
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.DD_TRACE_ID to tracer.traceId,
                        LogAttributes.DD_SPAN_ID to tracer.spanId
                    )
                )
        }
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `M register RUM Error with crash W RumMonitor registered`() {
        val currentThread = Thread.currentThread()
        GlobalRum.registerIfAbsent(mockRumMonitor)

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        verify(mockRumMonitor).addCrash(
            "Application crash detected",
            RumErrorSource.SOURCE,
            fakeThrowable
        )
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    @Test
    fun `M add current RUM information W GlobalRum is active`(
        @Forgery rumContext: RumContext
    ) {
        val currentThread = Thread.currentThread()
        GlobalRum.updateRumContext(rumContext)
        GlobalRum.registerIfAbsent(mockRumMonitor)

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())

            assertThat(lastValue)
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumContext.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumContext.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumContext.viewId
                    )
                )
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }
}
