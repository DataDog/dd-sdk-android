/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.logger

import com.datadog.android.api.InternalLogger
import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(value = Configurator::class)
internal class LoggerFactoryTest {

    @StringForgery
    lateinit var fakeName: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return NoOpLogger W getLogger { name }`() {
        // When
        val logger = LoggerFactory.getLogger(fakeName)

        // Then
        assertThat(logger).isInstanceOf(NoOpLogger::class.java)
    }

    @Test
    fun `M return NoOpLogger W getLogger { class }`() {
        // When
        val logger = LoggerFactory.getLogger(Any::class.java)

        // Then
        assertThat(logger).isInstanceOf(NoOpLogger::class.java)
    }

    @Test
    fun `M return DatadogTracerLogger W getLogger { name, internalLogger }`() {
        // When
        val logger = LoggerFactory.getLogger(fakeName, mockInternalLogger)

        // Then
        assertThat(logger).isInstanceOf(DatadogCoreTracerLogger::class.java)
    }
}
