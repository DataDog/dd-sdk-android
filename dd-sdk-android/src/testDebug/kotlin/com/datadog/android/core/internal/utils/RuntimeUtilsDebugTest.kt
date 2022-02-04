/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.Datadog
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.CombinedLogHandler
import com.datadog.android.log.internal.logger.DatadogLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.log.internal.user.NoOpUserInfoProvider
import com.datadog.android.monitoring.internal.InternalLogsFeature
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RuntimeUtilsDebugTest {

    @BeforeEach
    fun `set up`(){
        Datadog.initialized.set(true)
        LogsFeature.initialized.set(true)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.initialized.set(false)
        LogsFeature.initialized.set(false)
        Datadog.isDebug = false
        InternalLogsFeature.stop()
    }

    // region sdkLogger

    @Test
    fun `M build LogCat sdkLogger W buildSdkLogger() {InternalLogs off}`() {
        // Given
        InternalLogsFeature.initialized.set(false)

        // When
        val logger = buildSdkLogger()

        // Then
        val handler: LogHandler = logger.handler
        assertThat(handler).isInstanceOf(CombinedLogHandler::class.java)
        val handlers = (handler as CombinedLogHandler).handlers.toList()
        assertThat(handlers.filterIsInstance<LogcatLogHandler>())
            .hasSize(1)
            .allMatch { it.serviceName == SDK_LOG_PREFIX }
        assertThat(handlers.filterIsInstance<DatadogLogHandler>())
            .hasSize(0)
        assertThat(handlers.filterIsInstance<NoOpLogHandler>())
            .hasSize(1)
    }

    @Test
    fun `M build combined sdkLogger W buildSdkLogger() {InternalLogs on}`() {
        // Given
        InternalLogsFeature.initialized.set(true)
        InternalLogsFeature.persistenceStrategy = mock()
        whenever(InternalLogsFeature.persistenceStrategy.getWriter()) doReturn mock()

        // When
        val logger = buildSdkLogger()

        // Then
        val handler: LogHandler = logger.handler
        assertThat(handler).isInstanceOf(CombinedLogHandler::class.java)
        val handlers = (handler as CombinedLogHandler).handlers.toList()
        println("Handlers : ${handlers.joinToString()}")
        assertThat(handlers.filterIsInstance<LogcatLogHandler>())
            .hasSize(1)
            .allMatch { it.serviceName == SDK_LOG_PREFIX }
        assertThat(handlers.filterIsInstance<DatadogLogHandler>())
            .hasSize(1)
            .allMatch { it.logGenerator.serviceName == InternalLogsFeature.SERVICE_NAME }
            .allMatch { it.logGenerator.envTag == "env:prod" }
            .allMatch { it.logGenerator.loggerName == SDK_LOGGER_NAME }
            .allMatch { it.logGenerator.userInfoProvider is NoOpUserInfoProvider }
    }

    // endregion
}
