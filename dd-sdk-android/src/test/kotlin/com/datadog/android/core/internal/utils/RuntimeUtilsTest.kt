/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.ConditionalLogHandler
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
internal class RuntimeUtilsTest {

    @BeforeEach
    fun `set up`() {
        Datadog.initialized.set(true)
        LogsFeature.initialized.set(true)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.initialized.set(false)
        LogsFeature.initialized.set(false)
        Datadog.isDebug = false
    }

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
        // When
        warnDeprecated(target, since, until)

        // Then
        verify(logger.mockDevLogHandler).handleLog(
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
        // When
        warnDeprecated(target, since, until, alternative)

        // Then
        verify(logger.mockDevLogHandler).handleLog(
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

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
