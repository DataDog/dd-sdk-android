/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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

    private lateinit var testedTelemetry: Telemetry

    @BeforeEach
    fun `set up`() {
        testedTelemetry = Telemetry(rumMonitor.mockSdkCore)
    }

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
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .sendErrorTelemetryEvent(message, throwable)
    }

    @Test
    fun `𝕄 report debug event 𝕎 debug()`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val fakeAdditionalProperties = forge.aNullable { exhaustiveAttributes() }
        // When
        testedTelemetry.debug(message, fakeAdditionalProperties)

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .sendDebugTelemetryEvent(message, fakeAdditionalProperties)
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
