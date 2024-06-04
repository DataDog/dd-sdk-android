/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.app.Application
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CrashReportsFeatureTest {

    private lateinit var testedFeature: CrashReportsFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    var jvmExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun `set up crash reports`() {
        testedFeature = CrashReportsFeature(mockSdkCore)
        jvmExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @AfterEach
    fun `tear down crash reports`() {
        Thread.setDefaultUncaughtExceptionHandler(jvmExceptionHandler)
        testedFeature.originalUncaughtExceptionHandler = jvmExceptionHandler
    }

    @Test
    fun `M register crash handler W initialize`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(handler)
            .isInstanceOf(DatadogExceptionHandler::class.java)
    }

    @Test
    fun `M restore original crash handler W onStop()`() {
        // Given
        val mockOriginalHandler: Thread.UncaughtExceptionHandler = mock()
        Thread.setDefaultUncaughtExceptionHandler(mockOriginalHandler)

        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.onStop()

        // Then
        val finalHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(finalHandler).isSameAs(mockOriginalHandler)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
