/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoSerializer
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoSerializer
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ErrorEventAssert
import com.datadog.android.rum.assertj.ViewEventAssert
import com.datadog.android.rum.internal.data.file.RumFileWriter
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.google.gson.JsonParseException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isA
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
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.Mockito
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

    lateinit var lastNdkCrashLogFile: File
    lateinit var lastRumViewEventFile: File
    lateinit var lastUserInfoFile: File
    lateinit var lastNetworkInfoFile: File

    lateinit var mockDevLogHandler: LogHandler

    lateinit var testedHandler: DatadogNdkCrashHandler

    @Forgery
    lateinit var fakeNdkCrashLog: NdkCrashLog

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    lateinit var fakeSerializedUserInfo: String
    lateinit var fakeSerializedNetworkInfo: String

    lateinit var fakeRumViewEvent: RumEvent
    lateinit var fakeSerializedNdkCrashLog: String
    lateinit var fakeSerializedRumViewEvent: String

    private var rumEventSerializer = RumEventSerializer()
    private var userInfoEventSerializer = UserInfoSerializer()
    private var networkInfoSerializer = NetworkInfoSerializer()

    // region Unit Tests

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDevLogHandler = mockDevLogHandler()
        fakeRumViewEvent = forge.getForgery(RumEvent::class.java)
            .copy(
                event = forge.getForgery(ViewEvent::class.java)
                    .copy(date = System.currentTimeMillis())
            )
        fakeSerializedNdkCrashLog = fakeNdkCrashLog.toJson()
        fakeSerializedRumViewEvent = rumEventSerializer.serialize(fakeRumViewEvent)
        fakeSerializedUserInfo = userInfoEventSerializer.serialize(fakeUserInfo)
        fakeSerializedNetworkInfo = networkInfoSerializer.serialize(fakeNetworkInfo)
        lastNdkCrashLogFile = File(
            fakeNdkCrashReportsDirectory,
            DatadogNdkCrashHandler.LAST_CRASH_LOG_FILE_NAME
        )
        lastNetworkInfoFile = File(
            fakeNdkCrashReportsDirectory,
            DatadogNdkCrashHandler.LAST_NETWORK_INFORMATION_FILE_NAME
        )
        lastUserInfoFile = File(
            fakeNdkCrashReportsDirectory,
            DatadogNdkCrashHandler.LAST_USER_INFORMATION_FILE_NAME
        )
        lastRumViewEventFile = File(
            fakeNdkCrashReportsDirectory,
            RumFileWriter.LAST_VIEW_EVENT_FILE_NAME
        )
        lastNdkCrashLogFile.outputStream().use {
            it.write(fakeSerializedNdkCrashLog.toByteArray())
        }
        lastRumViewEventFile.outputStream().use {
            it.write(fakeSerializedRumViewEvent.toByteArray())
        }
        lastUserInfoFile.outputStream().use {
            it.write(fakeSerializedUserInfo.toByteArray())
        }
        lastNetworkInfoFile.outputStream().use {
            it.write(fakeSerializedNetworkInfo.toByteArray())
        }
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
                any(),
                anyOrNull(),
                anyOrNull()
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
            mockedLockGenerator
        )
    }

    @Test
    fun `M prepare all the crash data W prepareData`(forge: Forge) {
        // WHEN
        testedHandler.prepareData()

        // THEN
        assertThat(testedHandler.lastSerializedRumViewEvent).isEqualTo(fakeSerializedRumViewEvent)
        assertThat(testedHandler.lastSerializedNdkCrashLog).isEqualTo(fakeSerializedNdkCrashLog)
        assertThat(testedHandler.lastSerializedUserInformation).isEqualTo(fakeSerializedUserInfo)
        assertThat(testedHandler.lastSerializedNetworkInformation).isEqualTo(
            fakeSerializedNetworkInfo
        )
    }

    @Test
    fun `M log error W prepareData { throws SecurityException }`(forge: Forge) {
        // GIVEN
        val fakeSecurityException = SecurityException(forge.aString())
        val brokenNdkCrashDirectory: File = mock {
            whenever(it.exists()).thenReturn(true)
            whenever(it.listFiles()).thenThrow(fakeSecurityException)
        }
        testedHandler = DatadogNdkCrashHandler(
            brokenNdkCrashDirectory,
            mockedExecutorService,
            mockedLockGenerator
        )

        // WHEN
        testedHandler.prepareData()

        // THEN
        verify(mockDevLogHandler).handleLog(
            android.util.Log.ERROR,
            DatadogNdkCrashHandler.READ_NDK_DIRECTORY_ERROR_MESSAGE,
            fakeSecurityException
        )
        assertThat(testedHandler.lastSerializedRumViewEvent).isNull()
        assertThat(testedHandler.lastSerializedNdkCrashLog).isNull()
        assertThat(testedHandler.lastSerializedUserInformation).isNull()
        assertThat(testedHandler.lastSerializedNetworkInformation).isNull()
    }

    @Test
    fun `M do nothing W prepareData { directory does not exist}`() {
        // GIVEN
        val brokenNdkCrashDirectory: File = mock()
        testedHandler = DatadogNdkCrashHandler(
            brokenNdkCrashDirectory,
            mockedExecutorService,
            mockedLockGenerator
        )
        testedHandler.prepareData()

        // WHEN
        testedHandler.prepareData()

        // THEN
        assertThat(testedHandler.lastSerializedRumViewEvent).isNull()
        assertThat(testedHandler.lastSerializedNdkCrashLog).isNull()
        assertThat(testedHandler.lastSerializedUserInformation).isNull()
        assertThat(testedHandler.lastSerializedNetworkInformation).isNull()
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
            mockedLockGenerator
        )
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verifyZeroInteractions(mockedAsyncLogWriter)
        verifyZeroInteractions(mockedAsyncRumWriter)
    }

    @Test
    fun `M do nothing W handleNdkCrash { there was no NDK crash report persisted }`() {
        // GIVEN
        fakeNdkCrashReportsDirectory
            .listFiles()
            ?.firstOrNull() { it == lastNdkCrashLogFile }
            ?.delete()
        testedHandler = DatadogNdkCrashHandler(
            fakeNdkCrashReportsDirectory,
            mockedExecutorService,
            mockedLockGenerator
        )
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

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
        lastNdkCrashLogFile.outputStream().use {
            it.write(fakeBrokenJson.toByteArray())
        }
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verifyZeroInteractions(mockedAsyncLogWriter)
        verifyZeroInteractions(mockedAsyncRumWriter)
        verify(mockLogHandler).handleLog(
            eq(android.util.Log.ERROR),
            eq(DatadogNdkCrashHandler.DESERIALIZE_CRASH_EVENT_ERROR_MESSAGE),
            isA<JsonParseException>(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        restoreSdkLogHandler(originalLogHandler)
    }

    @Test
    fun `M send an error log W handleNdkCrash { does not have last view event }`(forge: Forge) {
        // GIVEN
        fakeNdkCrashReportsDirectory
            .listFiles()
            ?.firstOrNull() { it == lastRumViewEventFile }
            ?.delete()
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

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
            eq(false),
            eq(fakeUserInfo),
            eq(fakeNetworkInfo)
        )
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
    }

    @Test
    fun `M send an error log W handleNdkCrash { last view event is broken }`(forge: Forge) {
        // GIVEN
        val fakeBrokenJson = "{]"
        lastRumViewEventFile.outputStream().use {
            it.write(fakeBrokenJson.toByteArray())
        }
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

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
            eq(false),
            eq(fakeUserInfo),
            eq(fakeNetworkInfo)
        )
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
    }

    @Test
    fun `M send an error log W handleNdkCrash { does not have last user info }`(forge: Forge) {
        // GIVEN
        fakeNdkCrashReportsDirectory
            .listFiles()
            ?.firstOrNull() { it == lastUserInfoFile }
            ?.delete()
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verify(mockedLockGenerator).generateLog(
            eq(Log.CRASH),
            eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
            anyOrNull(),
            any(),
            eq(emptySet()),
            eq(fakeNdkCrashLog.timestamp),
            anyOrNull(),
            eq(false),
            eq(false),
            eq(null),
            eq(fakeNetworkInfo)
        )
    }

    @Test
    fun `M send an error log W handleNdkCrash { last user info is broken }`(forge: Forge) {
        // GIVEN
        val fakeBrokenJson = "{]"
        lastUserInfoFile.outputStream().use {
            it.write(fakeBrokenJson.toByteArray())
        }
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verify(mockedLockGenerator).generateLog(
            eq(Log.CRASH),
            eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
            anyOrNull(),
            any(),
            eq(emptySet()),
            eq(fakeNdkCrashLog.timestamp),
            anyOrNull(),
            eq(false),
            eq(false),
            eq(null),
            eq(fakeNetworkInfo)
        )
    }

    @Test
    fun `M send an error log W handleNdkCrash { does not have last network info }`(forge: Forge) {
        // GIVEN
        fakeNdkCrashReportsDirectory
            .listFiles()
            ?.firstOrNull() { it == lastNetworkInfoFile }
            ?.delete()
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verify(mockedLockGenerator).generateLog(
            eq(Log.CRASH),
            eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
            anyOrNull(),
            any(),
            eq(emptySet()),
            eq(fakeNdkCrashLog.timestamp),
            anyOrNull(),
            eq(false),
            eq(false),
            eq(fakeUserInfo),
            eq(null)
        )
    }

    @Test
    fun `M send an error log W handleNdkCrash { last network info is broken }`(forge: Forge) {
        // GIVEN
        val fakeBrokenJson = "{]"
        lastNetworkInfoFile.outputStream().use {
            it.write(fakeBrokenJson.toByteArray())
        }
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verify(mockedLockGenerator).generateLog(
            eq(Log.CRASH),
            eq(DatadogNdkCrashHandler.NDK_ERROR_LOG_MESSAGE.format(fakeNdkCrashLog.signalName)),
            anyOrNull(),
            any(),
            eq(emptySet()),
            eq(fakeNdkCrashLog.timestamp),
            anyOrNull(),
            eq(false),
            eq(false),
            eq(fakeUserInfo),
            eq(null)
        )
    }

    @Test
    fun `M send an error log W RUM context handleNdkCrash { has last view event }`(forge: Forge) {
        // GIVEN
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

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
            eq(false),
            eq(fakeUserInfo),
            eq(fakeNetworkInfo)
        )
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
    }

    @Test
    fun `M send the updated RUM ViewEvent W handleNdkCrash { has last view event }`(forge: Forge) {
        // GIVEN
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
        val argumentCaptor = argumentCaptor<RumEvent>()
        verify(mockedAsyncRumWriter, times(2)).write(argumentCaptor.capture())
        val currentCrash = fakeBundledViewEvent.view.crash
        val expectedCrash = currentCrash?.copy(count = currentCrash.count + 1)
            ?: ViewEvent.Crash(1)
        ViewEventAssert.assertThat(argumentCaptor.firstValue.event as ViewEvent)
            .isEqualTo(
                fakeBundledViewEvent.copy(
                    view = fakeBundledViewEvent.view.copy(
                        crash = expectedCrash,
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
        val fakeBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        mockTheLogGenerator(fakeBundledViewEvent)
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

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
            .hasErrorType(fakeNdkCrashLog.signalName)
    }

    @Test
    fun `M only send the RUM ErrorEvent W handleNdkCrash { last view event older than 4h }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeOldBundledViewEvent = fakeRumViewEvent.event as ViewEvent
        val fakeOldSerializedRumViewEvent = rumEventSerializer.serialize(
            fakeRumViewEvent.copy(
                event = fakeOldBundledViewEvent.copy(
                    date = fakeOldBundledViewEvent.date -
                        DatadogNdkCrashHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
                )
            )
        )
        mockTheLogGenerator(fakeOldBundledViewEvent)
        FileOutputStream(lastRumViewEventFile, false).use {
            it.write(fakeOldSerializedRumViewEvent.toByteArray())
        }
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        verify(mockedAsyncLogWriter).write(mockedGeneratedLog)
        val argumentCaptor = argumentCaptor<RumEvent>()
        verify(mockedAsyncRumWriter, times(1)).write(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue.event).isInstanceOf(ErrorEvent::class.java)
    }

    @Test
    fun `M clean the NDK crash reports folder`() {
        // GIVEN
        testedHandler.prepareData()

        // WHEN
        testedHandler.handleNdkCrash(mockedAsyncLogWriter, mockedAsyncRumWriter)

        // THEN
        assertThat(fakeNdkCrashReportsDirectory.listFiles()).isEmpty()
        assertThat(testedHandler.lastSerializedRumViewEvent).isNull()
        assertThat(testedHandler.lastSerializedNdkCrashLog).isNull()
        assertThat(testedHandler.lastSerializedUserInformation).isNull()
        assertThat(testedHandler.lastSerializedNetworkInformation).isNull()
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
                any(),
                anyOrNull(),
                anyOrNull()
            )
        ).thenReturn(mockedGeneratedLog)
    }

    // endregion

    fun eq(value: UserInfo): UserInfo {
        return Mockito.argThat {
            it?.name == value.name &&
                it?.id == value.id &&
                it?.email == value.email &&
                it?.extraInfo?.size == value.extraInfo.size
        } ?: value
    }
}
