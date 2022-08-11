/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(Configurator::class)
internal class SessionReplayContextProviderTest {

    lateinit var testedSessionReplayContextProvider: SessionReplayContextProvider

    @BeforeEach
    fun `set up`() {
        testedSessionReplayContextProvider = SessionReplayContextProvider()
    }

    @Test
    fun `M provide a valid Rum context W getRumContext()`() {
        // When
        rumMonitor.context = rumMonitor.context.copy(viewId = UUID.randomUUID().toString())
        GlobalRum.updateRumContext(rumMonitor.context)
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(rumMonitor.context.applicationId)
        assertThat(context.sessionId).isEqualTo(rumMonitor.context.sessionId)
        assertThat(context.viewId).isEqualTo(rumMonitor.context.viewId)
    }

    @Test
    fun `M provide an invalid Rum context W getRumContext() { RUM not initialized }`() {
        // Given
        GlobalRum.updateRumContext(RumContext())

        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.viewId).isEqualTo(RumContext.NULL_UUID)
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
