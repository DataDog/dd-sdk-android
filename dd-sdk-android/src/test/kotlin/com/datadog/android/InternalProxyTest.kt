/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
@ForgeConfiguration(Configurator::class)
internal class InternalProxyTest {

    @Test
    fun `M proxy addLongTask to RumMonitor W addLongTask()`(
        @LongForgery time: Long,
        @StringForgery target: String
    ) {
        // Given
        val proxy = _InternalProxy(GlobalRum.get() as? AdvancedRumMonitor)

        // When
        proxy._rumProxy?.addLongTask(time, target)

        // Then
        verify(rumMonitor.mockInstance).addLongTask(time, target)
    }

    @Test
    fun `M proxy telemetry to RumMonitor W debug()`(
        @StringForgery message: String
    ) {
        // Given
        val proxy = _InternalProxy(GlobalRum.get() as? AdvancedRumMonitor)

        // When
        proxy._telemetry.debug(message)

        // Then
        verify(rumMonitor.mockInstance).sendDebugTelemetryEvent(message)
    }

    @Test
    fun `M proxy telemetry to RumMonitor W error()`(
        @StringForgery message: String,
        @StringForgery stack: String,
        @StringForgery kind: String
    ) {
        // Given
        val proxy = _InternalProxy(GlobalRum.get() as? AdvancedRumMonitor)

        // When
        proxy._telemetry.error(message, stack, kind)

        // Then
        verify(rumMonitor.mockInstance).sendErrorTelemetryEvent(message, stack, kind)
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
