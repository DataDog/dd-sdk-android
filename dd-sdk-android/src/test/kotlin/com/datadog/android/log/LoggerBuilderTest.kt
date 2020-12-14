/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import android.util.Log as AndroidLog
import com.datadog.android.Configuration
import com.datadog.android.Datadog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.CombinedLogHandler
import com.datadog.android.log.internal.logger.DatadogLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockCoreFeature
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LoggerBuilderTest {

    lateinit var mockContext: Context

    lateinit var fakePackageName: String

    @TempDir
    lateinit var tempRootDir: File

    @Forgery
    lateinit var fakeConfig: Configuration.Feature.Logs

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakePackageName = forge.anAlphabeticalString()
        mockContext = mockContext(fakePackageName, "")
        whenever(mockContext.filesDir) doReturn tempRootDir

        mockCoreFeature(packageName = fakePackageName)
        LogsFeature.initialize(mockContext, fakeConfig)
    }

    @AfterEach
    fun `tear down`() {
        LogsFeature.stop()
    }

    @Test
    fun `builder returns no op if SDK is not initialized`() {
        val mockDevLogHandler = mockDevLogHandler()
        LogsFeature.stop()
        val logger = Logger.Builder()
            .build()

        val handler: LogHandler = logger.getFieldValue("handler")

        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
        verify(mockDevLogHandler)
            .handleLog(AndroidLog.ERROR, Datadog.MESSAGE_NOT_INITIALIZED)
    }

    @Test
    fun `builder without custom settings uses defaults`() {
        val logger = Logger.Builder()
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.logGenerator.serviceName).isEqualTo(CoreFeature.serviceName)
        assertThat(handler.logGenerator.loggerName).isEqualTo(fakePackageName)
        assertThat(handler.logGenerator.networkInfoProvider).isNull()
        assertThat(handler.writer).isNotNull()
        assertThat(handler.bundleWithTraces).isTrue()
        assertThat(handler.sampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat((handler.sampler as RateBasedSampler).sampleRate).isEqualTo(1.0f)
    }

    @Test
    fun `builder can set a ServiceName`(@Forgery forge: Forge) {
        val serviceName = forge.anAlphabeticalString()

        val logger = Logger.Builder()
            .setServiceName(serviceName)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.logGenerator.serviceName).isEqualTo(serviceName)
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
    fun `builder can enable logcat logs`(
        @Forgery forge: Forge
    ) {
        val logcatLogsEnabled = true

        val logger = Logger.Builder()
            .setLogcatLogsEnabled(logcatLogsEnabled)
            .build()

        val handler: LogHandler = logger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(CombinedLogHandler::class.java)
        val handlers = (handler as CombinedLogHandler).handlers
        assertThat(handlers)
            .hasAtLeastOneElementOfType(LogcatLogHandler::class.java)
            .hasAtLeastOneElementOfType(DatadogLogHandler::class.java)
    }

    @Test
    fun `builder can enable only logcat logs`(
        @Forgery forge: Forge
    ) {
        val logcatLogsEnabled = true
        val fakeServiceName = forge.anAlphaNumericalString()

        val logger = Logger.Builder()
            .setDatadogLogsEnabled(false)
            .setLogcatLogsEnabled(logcatLogsEnabled)
            .setServiceName(fakeServiceName)
            .build()

        val handler: LogHandler = logger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(LogcatLogHandler::class.java)
        val logcatLogHandler = handler as LogcatLogHandler
        assertThat(logcatLogHandler.serviceName)
            .isEqualTo(fakeServiceName)
        assertThat(logcatLogHandler.useClassnameAsTag)
            .isTrue()
    }

    @Test
    fun `builder can enable network info`(@Forgery forge: Forge) {
        val networkInfoEnabled = true

        val logger = Logger.Builder()
            .setNetworkInfoEnabled(networkInfoEnabled)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.logGenerator.networkInfoProvider).isNotNull()
    }

    @Test
    fun `builder can set the logger name`(@Forgery forge: Forge) {
        val loggerName = forge.anAlphabeticalString()

        val logger = Logger.Builder()
            .setLoggerName(loggerName)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.logGenerator.loggerName).isEqualTo(loggerName)
    }

    @Test
    fun `buider can disable the bundle with trace feature`(@Forgery forge: Forge) {
        val logger = Logger.Builder()
            .setBundleWithTraceEnabled(false)
            .build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        assertThat(handler.bundleWithTraces).isFalse()
    }

    @Test
    fun `builder can set a sampling rate`(@Forgery forge: Forge) {
        val expectedSampleRate = forge.aFloat(min = 0.0f, max = 1.0f)

        val logger = Logger.Builder().setSampleRate(expectedSampleRate).build()

        val handler: DatadogLogHandler = logger.getFieldValue("handler")
        val sampler = handler.sampler
        assertThat(sampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat((sampler as RateBasedSampler).sampleRate).isEqualTo(expectedSampleRate)
    }
}
