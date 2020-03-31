/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.log.internal.logger.ConditionalLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.utils.extension.EnableLogcat
import com.datadog.android.utils.extension.EnableLogcatExtension
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    }

    @Test
    @EnableLogcat(isEnabled = false)
    fun `the sdk logger should disable the logcat logs if the BuildConfig flag is false`() {
        val logger = buildSdkLogger()
        val handler: LogHandler = logger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(NoOpLogHandler::class.java)
    }

    @Test
    @EnableLogcat(isEnabled = true)
    fun `the sdk logger should enable the logcat logs if the BuildConfig flag is true`() {
        val logger = buildSdkLogger()
        val handler: LogHandler = logger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(LogcatLogHandler::class.java)
        assertThat((handler as LogcatLogHandler).serviceName)
            .isEqualTo(SDK_LOG_PREFIX)
    }

    @Test
    fun `the dev logger should always be enabled`() {
        val handler: LogHandler = devLogger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(ConditionalLogHandler::class.java)
        assertThat((handler as ConditionalLogHandler).delegateHandler)
            .isInstanceOf(LogcatLogHandler::class.java)
    }

    @Test
    fun `the dev logger handler should use the Datadog verbosity level`(
        @IntForgery(min = Log.VERBOSE, max = (Log.ASSERT + 1)) level: Int
    ) {
        Datadog.setVerbosity(level)
        val handler: LogHandler = devLogger.getFieldValue("handler")
        assertThat(handler).isInstanceOf(ConditionalLogHandler::class.java)

        val condition = (handler as ConditionalLogHandler).condition

        for (i in 0..10) {
            if (i >= level) {
                assertThat(condition(i, null)).isTrue()
            } else {
                assertThat(condition(i, null)).isFalse()
            }
        }
    }
}
