/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.NoOpAdvancedRumMonitor
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TelemetryTest {

    private val testedTelemetry: Telemetry = Telemetry()

    @Test
    fun `𝕄 report error event 𝕎 error()`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val throwable = forge.aNullable { forge.aThrowable() }

        // When
        testedTelemetry.error(message, throwable)

        // Then
        verify(rumMonitor.mockInstance).sendErrorTelemetryEvent(message, throwable)
    }

    @Test
    fun `𝕄 report debug event 𝕎 debug()`(
        @StringForgery message: String
    ) {
        // When
        testedTelemetry.debug(message)

        // Then
        verify(rumMonitor.mockInstance).sendDebugTelemetryEvent(message)
    }

    @Test
    fun `𝕄 return NoOpAdvancedRumMonitor 𝕎 rumMonitor() { RUM monitor != AdvancedRumMonitor }`() {
        // Given
        GlobalRum.monitor = mock()

        // When
        val monitor = testedTelemetry.rumMonitor

        // Then
        assertThat(monitor).isInstanceOf(NoOpAdvancedRumMonitor::class.java)
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
