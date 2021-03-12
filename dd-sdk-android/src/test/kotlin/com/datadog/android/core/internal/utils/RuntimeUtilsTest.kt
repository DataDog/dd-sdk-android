/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.log.internal.logger.CombinedLogHandler
import com.datadog.android.log.internal.logger.ConditionalLogHandler
import com.datadog.android.log.internal.logger.DatadogLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.log.internal.user.NoOpUserInfoProvider
import com.datadog.android.monitoring.internal.InternalLogsFeature
import com.datadog.android.utils.extension.EnableLogcat
import com.datadog.android.utils.extension.EnableLogcatExtension
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(EnableLogcatExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
class RuntimeUtilsTest {

    @BeforeEach
    fun `set up`() {
        devLogger.setFieldValue("handler", buildDevLogHandler())
    }

    @AfterEach
    fun `tear down`() {
        Datadog.setFieldValue("isDebug", false)
        InternalLogsFeature.stop()

        devLogger.setFieldValue("handler", buildDevLogHandler())
        rebuildSdkLogger()
    }

    // region sdkLogger

    @Test
    @EnableLogcat(isEnabled = false)
    fun `M build noop sdkLogger W buildSdkLogger() {LOGCAT_ENABLED=false, InternalLogs off}`() {
        // When
        val logger = buildSdkLogger()

        // Then
        val handler: LogHandler = logger.handler
        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
    }

    @Test
    @EnableLogcat(isEnabled = true)
    fun `M build LogCat sdkLogger W buildSdkLogger() {LOGCAT_ENABLED=true, InternalLogs off}`() {
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
    @EnableLogcat(isEnabled = false)
    fun `M build internal sdkLogger W buildSdkLogger() {LOGCAT_ENABLED=false, InternalLogs on}`() {
        // Given
        InternalLogsFeature.initialized.set(true)

        // When
        val logger = buildSdkLogger()

        // Then
        val handler: LogHandler = logger.handler
        assertThat(handler).isInstanceOf(DatadogLogHandler::class.java)
    }

    @Test
    @EnableLogcat(isEnabled = true)
    fun `M build combined sdkLogger W buildSdkLogger() {LOGCAT_ENABLED=true, InternalLogs on}`() {
        // Given
        InternalLogsFeature.initialized.set(true)

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
            .hasSize(1)
            .allMatch { it.logGenerator.serviceName == InternalLogsFeature.SERVICE_NAME }
            .allMatch { it.logGenerator.envTag == "env:prod" }
            .allMatch { it.logGenerator.loggerName == SDK_LOGGER_NAME }
            .allMatch { it.logGenerator.userInfoProvider is NoOpUserInfoProvider }
    }

    // endregion

    // region devLogger

    @Test
    fun `M build conditional Log handler W buildDevLogger()`(
        @IntForgery(min = Log.VERBOSE, max = (Log.ASSERT + 1)) level: Int
    ) {
        // Given
        Datadog.setVerbosity(level)

        // When
        val handler = buildDevLogHandler()

        // Then
        assertThat(handler).isInstanceOf(ConditionalLogHandler::class.java)

        val condition = handler.condition

        for (i in 0..10) {
            if (i >= level) {
                assertThat(condition(i, null)).isTrue()
            } else {
                assertThat(condition(i, null)).isFalse()
            }
        }
    }

    // endregion

    // region warnDeprecated

    @Test
    fun `M log a warning W warnDeprecated()`(
        @StringForgery target: String,
        @StringForgery since: String,
        @StringForgery until: String
    ) {
        // Given
        val handler = mockDevLogHandler()

        // When
        warnDeprecated(target, since, until)

        // Then
        verify(handler).handleLog(
            Log.WARN,
            WARN_DEPRECATED.format(
                Locale.US,
                target,
                since,
                until
            )
        )
    }

    @Test
    fun `M log a warning W warnDeprecated() with alternative`(
        @StringForgery target: String,
        @StringForgery since: String,
        @StringForgery until: String,
        @StringForgery alternative: String
    ) {
        // Given
        val handler = mockDevLogHandler()

        // When
        warnDeprecated(target, since, until, alternative)

        // Then
        verify(handler).handleLog(
            Log.WARN,
            WARN_DEPRECATED_WITH_ALT.format(
                Locale.US,
                target,
                since,
                until,
                alternative
            )
        )
    }

    // endregion
}
