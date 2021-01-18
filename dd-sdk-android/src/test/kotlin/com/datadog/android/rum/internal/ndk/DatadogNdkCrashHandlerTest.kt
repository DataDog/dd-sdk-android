/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ErrorEventAssert
import com.datadog.android.rum.assertj.ViewEventAssert
import com.datadog.android.rum.internal.data.file.RumFileWriter
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogNdkCrashHandlerTest {

    @Mock
    lateinit var mockedExecutorService: ExecutorService

    @TempDir
    lateinit var fakeNdkCrashReportsDirectory: File

    @Mock
    lateinit var mockedAsyncLogWriter: Writer<Log>

    @Mock
    lateinit var mockedAsyncRumWriter: Writer<RumEvent>

    @Mock
    lateinit var mockedLockGenerator: LogGenerator

    @Mock
    lateinit var mockedGeneratedLog: Log

    lateinit var testedHandler: DatadogNdkCrashHandler

    @Forgery
    lateinit var fakeNdkCrashLog: NdkCrashLog

    lateinit var fakeRumViewEvent: RumEvent
    lateinit var fakeSerializedNdkCrashLog: String
    lateinit var fakeSerializedRumViewEvent: String

    private var rumEventSerializer = RumEventSerializer()

    // region Unit Tests

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeRumViewEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        fakeSerializedNdkCrashLog = fakeNdkCrashLog.toJson()
        fakeSerializedRumViewEvent = rumEventSerializer.serialize(fakeRumViewEvent)
        whenever(
            mockedLockGenerator.generateLog(
                eq(Log.CRASH),
                eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
                anyOrNull(),
                eq(
                    mapOf(
                        LogAttributes.ERROR_STACK to fakeNdkCrashLog.stacktrace
                    )
                ),
                anyOrNull(),
                eq(fakeNdkCrashLog.timestamp),
                anyOrNull(),
                any(),
                any()
            )
        ).thenReturn(mockedGeneratedLog)

        whenever(mockedExecutorService.submit(any())).then {
            val runnable = it.getArgument<Runnable>(0)
            runnable.run()
            mock<Future<Any>>()
        }
        testedHandler = DatadogNdkCrashHandler(
            fakeNdkCrashReportsDirectory,
            mockedExecutorService,
            mockedAsyncLogWriter,
            mockedAsyncRumWriter,
            mockedLockGenerator
        )
    }

    @Test
    fun `M do nothing W handleNdkCrash { NDK crash reports directory does not exist }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeNonExistingDir = File(forge.aStringMatching("[a-b]{3,10}"))
        testedHandler = DatadogNdkCrashHandler(
            fakeNonExistingDir,
            mockedExecutorService,
            mockedAsyncLogWriter,
            mockedAsyncRumWriter,
            mockedLockGenerator
        )

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verifyZeroInteractions(mockedAsyncLogWriter)
        verifyZeroInteractions(mockedAsyncRumWriter)
    }

    @Test
    fun `M do nothing W handleNdkCrash { there was no NDK crash report persisted }`() {
        // GIVEN
        testedHandler = DatadogNdkCrashHandler(
            fakeNdkCrashReportsDirectory,
            mockedExecutorService,
            mockedAsyncLogWriter,
            mockedAsyncRumWriter,
            mockedLockGenerator
        )

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verifyZeroInteractions(mockedAsyncLogWriter)
        verifyZeroInteractions(mockedAsyncRumWriter)
    }

    @Test
    fun `M do nothing and log exception W handleNdkCrash { persisted crash report is broken }`(
        forge: Forge
    ) {
        // GIVEN
        val mockLogHandler: LogHandler = mock()
        val originalLogHandler: LogHandler = mockSdkLogHandler(mockLogHandler)
        val fakeBrokenJson = "{]"
        val crashLogFile =
            File(fakeNdkCrashReportsDirectory, DatadogNdkCrashHandler.CRASH_LOG_FILE_NAME)
        crashLogFile.outputStream().use {
            it.write(fakeBrokenJson.toByteArray())
        }
        testedHandler = DatadogNdkCrashHandler(
            fakeNdkCrashReportsDirectory,
            mockedExecutorService,
            mockedAsyncLogWriter,
            mockedAsyncRumWriter,
            mockedLockGenerator
        )

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verifyZeroInteractions(mockedAsyncLogWriter)
        verifyZeroInteractions(mockedAsyncRumWriter)
        verify(mockLogHandler).handleLog(
            eq(android.util.Log.ERROR),
            eq("Malformed ndk crash error log"),
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        restoreSdkLogHandler(originalLogHandler)
    }

    @Test
    fun `M send an error log W handleNdkCrash { found a persisted crash report }`(forge: Forge) {
        // GIVEN
        val crashLogFile =
            File(fakeNdkCrashReportsDirectory, DatadogNdkCrashHandler.CRASH_LOG_FILE_NAME)
        crashLogFile.outputStream().use {
            it.write(fakeSerializedNdkCrashLog.toByteArray())
        }

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verify(mockedLockGenerator).generateLog(
            eq(Log.CRASH),
            eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
            anyOrNull(),
            eq(
                mapOf(
                    LogAttributes.ERROR_STACK to fakeNdkCrashLog.stacktrace
                )
            ),
            eq(emptySet()),
            eq(fakeNdkCrashLog.timestamp),
            anyOrNull(),
            eq(false),
            eq(false)
        )
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
    }

    @Test
    fun `M send an error log W RUM context handleNdkCrash { has last view event }`(forge: Forge) {
        // GIVEN
        val crashLogFile =
            File(fakeNdkCrashReportsDirectory, DatadogNdkCrashHandler.CRASH_LOG_FILE_NAME)
        val lastViewEventFile =
            File(fakeNdkCrashReportsDirectory, RumFileWriter.LAST_VIEW_EVENT_FILE_NAME)

        crashLogFile.outputStream().use {
            it.write(fakeSerializedNdkCrashLog.toByteArray())
        }
        lastViewEventFile.outputStream().use {
            it.write(fakeSerializedRumViewEvent.toByteArray())
        }
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verify(mockedLockGenerator).generateLog(
            eq(Log.CRASH),
            eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
            anyOrNull(),
            eq(
                mapOf(
                    LogAttributes.RUM_VIEW_ID to fakeBundledViewEvent.view.id,
                    LogAttributes.RUM_SESSION_ID to fakeBundledViewEvent.session.id,
                    LogAttributes.RUM_APPLICATION_ID to fakeBundledViewEvent.application.id,
                    LogAttributes.ERROR_STACK to fakeNdkCrashLog.stacktrace
                )
            ),
            eq(emptySet()),
            eq(fakeNdkCrashLog.timestamp),
            anyOrNull(),
            eq(false),
            eq(false)
        )
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
    }

    @Test
    fun `M send the updated RUM ViewEvent W handleNdkCrash { has last view event }`(forge: Forge) {
        // GIVEN
        val crashLogFile =
            File(fakeNdkCrashReportsDirectory, DatadogNdkCrashHandler.CRASH_LOG_FILE_NAME)
        val lastViewEventFile =
            File(fakeNdkCrashReportsDirectory, RumFileWriter.LAST_VIEW_EVENT_FILE_NAME)

        crashLogFile.outputStream().use {
            it.write(fakeSerializedNdkCrashLog.toByteArray())
        }
        lastViewEventFile.outputStream().use {
            it.write(fakeSerializedRumViewEvent.toByteArray())
        }
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
        val argumentCaptor = argumentCaptor<RumEvent>()
        verify(mockedAsyncRumWriter, times(2)).write(argumentCaptor.capture())
        ViewEventAssert.assertThat(argumentCaptor.firstValue.event as ViewEvent)
            .isEqualTo(
                fakeBundledViewEvent.copy(
                    view = fakeBundledViewEvent.view.copy(
                        error = fakeBundledViewEvent.view.error.copy(
                            count = fakeBundledViewEvent.view.error.count + 1
                        ),
                        isActive = false
                    ),
                    dd = fakeBundledViewEvent.dd.copy(
                        documentVersion = fakeBundledViewEvent.dd.documentVersion + 1
                    )
                )
            )
    }

    @Test
    fun `M send the updated RUM ErrorEvent W handleNdkCrash { has last view event }`(forge: Forge) {
        // GIVEN
        val crashLogFile =
            File(fakeNdkCrashReportsDirectory, DatadogNdkCrashHandler.CRASH_LOG_FILE_NAME)
        val lastViewEventFile =
            File(fakeNdkCrashReportsDirectory, RumFileWriter.LAST_VIEW_EVENT_FILE_NAME)

        crashLogFile.outputStream().use {
            it.write(fakeSerializedNdkCrashLog.toByteArray())
        }
        lastViewEventFile.outputStream().use {
            it.write(fakeSerializedRumViewEvent.toByteArray())
        }
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
        val argumentCaptor = argumentCaptor<RumEvent>()
        verify(mockedAsyncRumWriter, times(2)).write(argumentCaptor.capture())
        ErrorEventAssert.assertThat(argumentCaptor.secondValue.event as ErrorEvent)
            .hasApplicationId(fakeBundledViewEvent.application.id)
            .hasSessionId(fakeBundledViewEvent.session.id)
            .hasView(fakeBundledViewEvent.view.id, fakeBundledViewEvent.view.url)
            .hasMessage(
                DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)
            )
            .hasStackTrace(fakeNdkCrashLog.stacktrace)
            .isCrash(true)
            .hasSource(RumErrorSource.SOURCE)
            .hasTimestamp(fakeNdkCrashLog.timestamp)
            .hasUserInfo(
                UserInfo(
                    fakeBundledViewEvent.usr?.id,
                    fakeBundledViewEvent.usr?.name,
                    fakeBundledViewEvent.usr?.email
                )
            )
            .hasConnectivityStatus(
                fakeBundledViewEvent.connectivity?.status?.let {
                    ErrorEvent.Status.valueOf(it.name)
                }
            )
            .hasConnectivityCellular(
                fakeBundledViewEvent.connectivity?.cellular?.let {
                    ErrorEvent.Cellular(it.technology, it.carrierName)
                }
            )
            .hasConnectivityInterface(
                fakeBundledViewEvent.connectivity?.interfaces?.map {
                    ErrorEvent.Interface.valueOf(
                        it.name
                    )
                }
            )
    }

    @Test
    fun `M only send the RUM ErrorEvent W handleNdkCrash { last view event older than 4h }`(
        forge: Forge
    ) {
        // GIVEN
        fakeNdkCrashLog =
            fakeNdkCrashLog.copy(
                timestamp = System.currentTimeMillis() -
                    DatadogNdkCrashHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
            )
        fakeSerializedNdkCrashLog = fakeNdkCrashLog.toJson()
        val crashLogFile =
            File(fakeNdkCrashReportsDirectory, DatadogNdkCrashHandler.CRASH_LOG_FILE_NAME)
        val lastViewEventFile =
            File(fakeNdkCrashReportsDirectory, RumFileWriter.LAST_VIEW_EVENT_FILE_NAME)

        crashLogFile.outputStream().use {
            it.write(fakeSerializedNdkCrashLog.toByteArray())
        }
        lastViewEventFile.outputStream().use {
            it.write(fakeSerializedRumViewEvent.toByteArray())
        }
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
        val argumentCaptor = argumentCaptor<RumEvent>()
        verify(mockedAsyncRumWriter, times(1)).write(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue.event).isInstanceOf(ErrorEvent::class.java)
    }

    @Test
    fun `M clean the NDK crash reports folder`() {
        // GIVEN
        val crashLogFile =
            File(fakeNdkCrashReportsDirectory, DatadogNdkCrashHandler.CRASH_LOG_FILE_NAME)
        val lastViewEventFile =
            File(fakeNdkCrashReportsDirectory, RumFileWriter.LAST_VIEW_EVENT_FILE_NAME)

        crashLogFile.outputStream().use {
            it.write(fakeSerializedNdkCrashLog.toByteArray())
        }
        lastViewEventFile.outputStream().use {
            it.write(fakeSerializedRumViewEvent.toByteArray())
        }
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)

        // WHEN
        testedHandler.handleNdkCrash()

        // THEN
        assertThat(fakeNdkCrashReportsDirectory.listFiles()).isEmpty()
    }

    // endregion

    // region Internal

    private fun mockTheLogGenerator(fakeBundledViewEvent: ViewEvent) {
        whenever(
            mockedLockGenerator.generateLog(
                eq(Log.CRASH),
                eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
                anyOrNull(),
                eq(
                    mapOf(
                        LogAttributes.RUM_VIEW_ID to fakeBundledViewEvent.view.id,
                        LogAttributes.RUM_SESSION_ID to fakeBundledViewEvent.session.id,
                        LogAttributes.RUM_APPLICATION_ID to fakeBundledViewEvent.application.id,
                        LogAttributes.ERROR_STACK to fakeNdkCrashLog.stacktrace
                    )
                ),
                anyOrNull(),
                eq(fakeNdkCrashLog.timestamp),
                anyOrNull(),
                any(),
                any()
            )
        ).thenReturn(mockedGeneratedLog)
    }

    // endregion
}
