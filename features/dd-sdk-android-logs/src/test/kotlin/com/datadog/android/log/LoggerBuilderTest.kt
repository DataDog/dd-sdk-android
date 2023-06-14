/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.internal.logger.CombinedLogHandler
import com.datadog.android.log.internal.logger.DatadogLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.storage.DataWriter
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LoggerBuilderTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockLogsFeature: LogsFeature

    @Mock
    lateinit var mockDataWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeServiceName: String

    @StringForgery(regex = "[a-z]{2,4}(\\.[a-z]{3,8}){2,4}")
    lateinit var fakePackageName: String

    @BeforeEach
    fun `set up`() {
        whenever(mockLogsFeature.packageName) doReturn fakePackageName
        whenever(mockLogsFeature.dataWriter) doReturn mockDataWriter
        whenever(mockSdkCore.service) doReturn fakeServiceName
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn mockLogsFeatureScope
        whenever(mockLogsFeatureScope.unwrap<LogsFeature>()) doReturn mockLogsFeature
    }

    @Test
    fun `builder returns no-op if logs feature is missing`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null

        // When
        val testedLogger = Logger.Builder(mockSdkCore).build()

        // Then
        val handler = testedLogger.handler

        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Logger.SDK_NOT_INITIALIZED_WARNING_MESSAGE
        )
    }

    @Test
    fun `builder without custom settings uses defaults`() {
        val logger = Logger.Builder(mockSdkCore)
            .build()

        val handler: DatadogLogHandler = logger.handler as DatadogLogHandler
        assertThat(handler.writer).isSameAs(mockDataWriter)
        assertThat(handler.bundleWithTraces).isTrue
        assertThat(handler.sampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat((handler.sampler as RateBasedSampler).getSampleRate()).isEqualTo(100.0f)
        assertThat(handler.minLogPriority).isEqualTo(-1)
        assertThat(handler.loggerName).isEqualTo(fakePackageName)
        assertThat(handler.attachNetworkInfo).isFalse

        val logGenerator: DatadogLogGenerator = handler.logGenerator as DatadogLogGenerator
        assertThat(logGenerator.serviceName).isEqualTo(fakeServiceName)
    }

    @Test
    fun `builder can set a ServiceName`(@Forgery forge: Forge) {
        val serviceName = forge.anAlphabeticalString()

        val logger = Logger.Builder(mockSdkCore)
            .setService(serviceName)
            .build()

        val handler: DatadogLogHandler = logger.handler as DatadogLogHandler
        val logGenerator: DatadogLogGenerator = handler.logGenerator as DatadogLogGenerator
        assertThat(logGenerator.serviceName).isEqualTo(serviceName)
    }

    @Test
    fun `builder can disable datadog logs`() {
        val datadogLogsEnabled = false

        val logger: Logger = Logger.Builder(mockSdkCore)
            .setDatadogLogsEnabled(datadogLogsEnabled)
            .build()

        val handler: LogHandler = logger.handler
        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
    }

    @Test
    fun `builder can set min datadog logs priority`(
        @IntForgery minLogPriority: Int
    ) {
        val logger: Logger = Logger.Builder(mockSdkCore)
            .setDatadogLogsMinPriority(minLogPriority)
            .build()

        val handler: DatadogLogHandler = logger.handler as DatadogLogHandler
        assertThat(handler.minLogPriority).isEqualTo(minLogPriority)
    }

    @Test
    fun `builder can enable logcat logs`() {
        val logcatLogsEnabled = true

        val logger = Logger.Builder(mockSdkCore)
            .setLogcatLogsEnabled(logcatLogsEnabled)
            .build()

        val handler: LogHandler = logger.handler
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

        val logger = Logger.Builder(mockSdkCore)
            .setDatadogLogsEnabled(false)
            .setLogcatLogsEnabled(logcatLogsEnabled)
            .setService(fakeServiceName)
            .build()

        val handler: LogHandler = logger.handler
        assertThat(handler).isInstanceOf(LogcatLogHandler::class.java)
        val logcatLogHandler = handler as LogcatLogHandler
        assertThat(logcatLogHandler.serviceName)
            .isEqualTo(fakeServiceName)
        assertThat(logcatLogHandler.useClassnameAsTag)
            .isTrue
    }

    @Test
    fun `builder can enable network info`() {
        val networkInfoEnabled = true

        val logger = Logger.Builder(mockSdkCore)
            .setNetworkInfoEnabled(networkInfoEnabled)
            .build()

        val handler: DatadogLogHandler = logger.handler as DatadogLogHandler
        assertThat(handler.attachNetworkInfo).isTrue
    }

    @Test
    fun `builder can set the logger name`(@Forgery forge: Forge) {
        val loggerName = forge.anAlphabeticalString()

        val logger = Logger.Builder(mockSdkCore)
            .setLoggerName(loggerName)
            .build()

        val handler: DatadogLogHandler = logger.handler as DatadogLogHandler
        assertThat(handler.loggerName).isEqualTo(loggerName)
    }

    @Test
    fun `builder can disable the bundle with trace feature`() {
        val logger = Logger.Builder(mockSdkCore)
            .setBundleWithTraceEnabled(false)
            .build()

        val handler: DatadogLogHandler = logger.handler as DatadogLogHandler
        assertThat(handler.bundleWithTraces).isFalse
    }

    @Test
    fun `builder can set a sample rate`(@Forgery forge: Forge) {
        val expectedSampleRate = forge.aFloat(min = 0.0f, max = 100.0f)

        val logger = Logger.Builder(mockSdkCore).setSampleRate(expectedSampleRate).build()

        val handler: DatadogLogHandler = logger.handler as DatadogLogHandler
        val sampler = handler.sampler
        assertThat(sampler).isInstanceOf(RateBasedSampler::class.java)
        assertThat((sampler as RateBasedSampler).getSampleRate()).isEqualTo(expectedSampleRate)
    }
}
