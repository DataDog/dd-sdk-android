/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.utils

import com.datadog.android.log.Logger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.forge.aThrowable
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class LogUtilsTest {

    @Test
    fun `M build a SimpleDateFormat with the ISO format W buildSimpleDateFormat()`() {
        // When
        val simpleDateFormat = buildLogDateFormat()

        // Then
        assertThat(simpleDateFormat.toPattern()).isEqualTo(ISO_8601)
        assertThat(simpleDateFormat.timeZone).isEqualTo(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun `M log with ERROR + TELEMETRY level W errorWithTelemetry()`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val logger = mock<Logger>()
        val throwable = forge.aNullable { forge.aThrowable() }
        val attributes = forge.exhaustiveAttributes()

        // When
        logger.errorWithTelemetry(message, throwable, attributes)

        // Then
        verify(logger).log(
            ERROR_WITH_TELEMETRY_LEVEL,
            message,
            throwable,
            attributes
        )
    }

    @Test
    fun `M log with WARN + TELEMETRY level W warningWithTelemetry()`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val logger = mock<Logger>()
        val throwable = forge.aNullable { forge.aThrowable() }
        val attributes = forge.exhaustiveAttributes()

        // When
        logger.warningWithTelemetry(message, throwable, attributes)

        // Then
        verify(logger).log(
            WARN_WITH_TELEMETRY_LEVEL,
            message,
            throwable,
            attributes
        )
    }

    @Test
    fun `M log with DEBUG + TELEMETRY level W debugWithTelemetry()`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val logger = mock<Logger>()
        val throwable = forge.aNullable { forge.aThrowable() }
        val attributes = forge.exhaustiveAttributes()

        // When
        logger.debugWithTelemetry(message, throwable, attributes)

        // Then
        verify(logger).log(
            DEBUG_WITH_TELEMETRY_LEVEL,
            message,
            throwable,
            attributes
        )
    }
}
