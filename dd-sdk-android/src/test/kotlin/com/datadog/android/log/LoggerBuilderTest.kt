/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import android.util.Log as AndroidLog
import com.datadog.android.Datadog
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.CombinedLogHandler
import com.datadog.android.log.internal.logger.DatadogLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings()
internal class LoggerBuilderTest {

    lateinit var mockContext: Context

    lateinit var packageName: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up Datadog`(forge: Forge) {
        packageName = forge.anAlphabeticalString()
        mockContext = mockContext(packageName, "")
        whenever(mockContext.filesDir) doReturn rootDir

        Datadog.initialize(mockContext, forge.anHexadecimalString())
        Datadog.setVerbosity(AndroidLog.VERBOSE)
    }

    @AfterEach
    fun `tear down Datadog`() {
        try {
            Datadog.invokeMethod("stop")
        } catch (e: IllegalStateException) {
            // ignore
        }
    }

    @Test
    fun `builder returns no op if SDK is not initialized`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        Datadog.invokeMethod("stop") // simulate non initialized SDK
        val logger = Logger.Builder()
            .build()

        val handler: LogHandler = logger.getFieldValue("handler")

        assertThat(handler).isSameAs(NoOpLogHandler)
        assertThat(outputStream.toString())
            .isEqualTo(
                "E/Datadog: Datadog has not been initialized.\n" +
                    "Please add the following code in your application's onCreate() method:\n" +
                    "Datadog.initialize(context, \"<CLIENT_TOKEN>\");\n"
            )
    }

    @Test
    fun `builder without custom settings uses defaults`() {
        val logger = Logger.Builder()
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.serviceName).isEqualTo(LogsFeature.serviceName)
        assertThat(handler.loggerName).isEqualTo(packageName)
        assertThat(handler.networkInfoProvider).isNull()
        assertThat(handler.timeProvider).isNotNull()
        assertThat(handler.writer).isNotNull()
        assertThat(handler.bundleWithTraces).isTrue()
    }

    @Test
    fun `builder can set a ServiceName`(@Forgery forge: Forge) {
        val serviceName = forge.anAlphabeticalString()

        val logger = Logger.Builder()
            .setServiceName(serviceName)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.serviceName).isEqualTo(serviceName)
    }

    @Test
    fun `builder can disable datadog logs`(@Forgery forge: Forge) {
        val datadogLogsEnabled = false

        val logger: Logger = Logger.Builder()
            .setDatadogLogsEnabled(datadogLogsEnabled)
            .build()

        val handler: LogHandler = logger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
    }

    @Test
    fun `builder can enable logcat logs`(@Forgery forge: Forge) {
        val logcatLogsEnabled = true

        val logger = Logger.Builder()
            .setLogcatLogsEnabled(logcatLogsEnabled)
            .build()

        val handler: CombinedLogHandler = logger.getFieldValue("handler")
        assertThat(handler.handlers)
            .hasAtLeastOneElementOfType(DatadogLogHandler::class.java)
            .hasAtLeastOneElementOfType(LogcatLogHandler::class.java)
    }

    @Test
    fun `builder can enable network info`(@Forgery forge: Forge) {
        val networkInfoEnabled = true

        val logger = Logger.Builder()
            .setNetworkInfoEnabled(networkInfoEnabled)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.networkInfoProvider).isNotNull()
    }

    @Test
    fun `builder can set the logger name`(@Forgery forge: Forge) {
        val loggerName = forge.anAlphabeticalString()

        val logger = Logger.Builder()
            .setLoggerName(loggerName)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.loggerName).isEqualTo(loggerName)
    }

    @Test
    fun `buider can disable the bundle with trace feature`(@Forgery forge: Forge) {
        val loggerName = forge.anAlphabeticalString()

        val logger = Logger.Builder()
            .setBundleWithTraceEnabled(false)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.bundleWithTraces).isFalse()
    }
}
