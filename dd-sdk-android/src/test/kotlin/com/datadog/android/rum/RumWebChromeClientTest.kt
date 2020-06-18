/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.webview.RumWebChromeClient
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumWebChromeClientTest {

    private lateinit var testedClient: WebChromeClient

    @Mock
    private lateinit var mockLogHandler: LogHandler

    @Mock
    private lateinit var mockRumMonitor: RumMonitor

    @Mock
    private lateinit var mockConsoleMessage: ConsoleMessage

    @StringForgery(StringForgeryType.ALPHABETICAL)
    private lateinit var fakeMessage: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    private lateinit var fakeSource: String

    @IntForgery(min = 0)
    private var fakeLine: Int = 0

    @BeforeEach
    fun `set up`() {
        whenever(mockConsoleMessage.message()) doReturn fakeMessage
        whenever(mockConsoleMessage.sourceId()) doReturn fakeSource
        whenever(mockConsoleMessage.lineNumber()) doReturn fakeLine

        GlobalRum.registerIfAbsent(mockRumMonitor)
        testedClient = RumWebChromeClient(
            Logger(mockLogHandler)
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    @Test
    fun `onConsoleMessage forwards verbose log`() {
        whenever(mockConsoleMessage.messageLevel()) doReturn ConsoleMessage.MessageLevel.LOG

        val result = testedClient.onConsoleMessage(mockConsoleMessage)

        verify(mockLogHandler).handleLog(
            Log.VERBOSE,
            fakeMessage,
            null,
            mapOf(
                RumWebChromeClient.SOURCE_ID to fakeSource,
                RumWebChromeClient.SOURCE_LINE to fakeLine
            ),
            emptySet(),
            null
        )
        verifyZeroInteractions(mockRumMonitor)
        assertThat(result).isFalse()
    }

    @Test
    fun `onConsoleMessage forwards debug log`() {
        whenever(mockConsoleMessage.messageLevel()) doReturn ConsoleMessage.MessageLevel.DEBUG

        val result = testedClient.onConsoleMessage(mockConsoleMessage)

        verify(mockLogHandler).handleLog(
            Log.DEBUG,
            fakeMessage,
            null,
            mapOf(
                RumWebChromeClient.SOURCE_ID to fakeSource,
                RumWebChromeClient.SOURCE_LINE to fakeLine
            ),
            emptySet(),
            null
        )
        verifyZeroInteractions(mockRumMonitor)
        assertThat(result).isFalse()
    }

    @Test
    fun `onConsoleMessage forwards info log`() {
        whenever(mockConsoleMessage.messageLevel()) doReturn ConsoleMessage.MessageLevel.TIP

        val result = testedClient.onConsoleMessage(mockConsoleMessage)

        verify(mockLogHandler).handleLog(
            Log.INFO,
            fakeMessage,
            null,
            mapOf(
                RumWebChromeClient.SOURCE_ID to fakeSource,
                RumWebChromeClient.SOURCE_LINE to fakeLine
            ),
            emptySet(),
            null
        )
        verifyZeroInteractions(mockRumMonitor)
        assertThat(result).isFalse()
    }

    @Test
    fun `onConsoleMessage forwards warning log`() {
        whenever(mockConsoleMessage.messageLevel()) doReturn ConsoleMessage.MessageLevel.WARNING

        val result = testedClient.onConsoleMessage(mockConsoleMessage)

        verify(mockLogHandler).handleLog(
            Log.WARN,
            fakeMessage,
            null,
            mapOf(
                RumWebChromeClient.SOURCE_ID to fakeSource,
                RumWebChromeClient.SOURCE_LINE to fakeLine
            ),
            emptySet(),
            null
        )
        verifyZeroInteractions(mockRumMonitor)
        assertThat(result).isFalse()
    }

    @Test
    fun `onConsoleMessage forwards error log and sends Rum Error`() {
        whenever(mockConsoleMessage.messageLevel()) doReturn ConsoleMessage.MessageLevel.ERROR

        val result = testedClient.onConsoleMessage(mockConsoleMessage)

        verify(mockLogHandler).handleLog(
            Log.ERROR,
            fakeMessage,
            null,
            mapOf(
                RumWebChromeClient.SOURCE_ID to fakeSource,
                RumWebChromeClient.SOURCE_LINE to fakeLine
            ),
            emptySet(),
            null
        )
        verify(mockRumMonitor).addError(
            fakeMessage,
            RumErrorSource.CONSOLE,
            null,
            mapOf(
                RumWebChromeClient.SOURCE_ID to fakeSource,
                RumWebChromeClient.SOURCE_LINE to fakeLine
            )
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `onConsoleMessage with null message doesn't do anything`() {
        val result = testedClient.onConsoleMessage(null)

        verifyZeroInteractions(mockLogHandler, mockRumMonitor)
        assertThat(result).isFalse()
    }

    @Test
    fun testOnConsoleMessage() {
    }
}
