/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
internal class RuntimeUtilsTest {

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
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
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
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
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
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
