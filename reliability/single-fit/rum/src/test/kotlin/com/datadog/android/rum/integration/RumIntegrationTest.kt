/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.api.StubSdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.integration.config.MainLooperTestConfiguration
import com.datadog.android.rum.integration.forge.RumIntegrationForgeConfigurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

/**
 * This class is a Hello World integration test
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RumIntegrationTest {

    private lateinit var testedRumMonitor: RumMonitor

    @StringForgery
    lateinit var fakeRumAppId: String

    private lateinit var stubSdkCore: StubSdkCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSdkCore(forge)

        val fakeRumConfiguration = RumConfiguration.Builder(fakeRumAppId).build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
        testedRumMonitor = GlobalRumMonitor.get(stubSdkCore)
    }

    @Test
    fun `M write a view event W startView() + stopView()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given

        // When
        testedRumMonitor.startView(viewKey, viewName)
        Thread.sleep(500)
        testedRumMonitor.stopView(viewKey)
        Thread.sleep(100)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(2)
        val event0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        assertThat(event0.getAsJsonPrimitive("type")).isEqualTo(JsonPrimitive("view"))
        assertThat(event0.getAsJsonObject("view").getAsJsonPrimitive("name")).isEqualTo(JsonPrimitive(viewName))

        val event1 = JsonParser.parseString(eventsWritten[1].eventData) as JsonObject
        assertThat(event1.getAsJsonPrimitive("type")).isEqualTo(JsonPrimitive("view"))
        assertThat(event1.getAsJsonObject("view").getAsJsonPrimitive("name")).isEqualTo(JsonPrimitive(viewName))
        assertThat(event1.getAsJsonObject("view").getAsJsonPrimitive("name")).isEqualTo(JsonPrimitive(viewName))
        assertThat(event1.getAsJsonObject("view").getAsJsonPrimitive("is_active")).isEqualTo(JsonPrimitive(false))
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
