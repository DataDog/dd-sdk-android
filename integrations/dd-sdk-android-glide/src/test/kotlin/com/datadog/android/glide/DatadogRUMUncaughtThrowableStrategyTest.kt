/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.glide

import com.datadog.android.glide.utils.config.DatadogSingletonTestConfiguration
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.RuntimeException
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class,
        TestConfigurationExtension::class
    )
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogRUMUncaughtThrowableStrategyTest {

    lateinit var testedStrategy: DatadogRUMUncaughtThrowableStrategy

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @StringForgery
    lateinit var fakeName: String

    @BeforeEach
    fun `set up`() {
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockRumMonitor, datadog.mockInstance)
        }

        testedStrategy = DatadogRUMUncaughtThrowableStrategy(fakeName)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `handles throwable`(
        @StringForgery message: String
    ) {
        val throwable = RuntimeException(message)

        testedStrategy.handle(throwable)

        verify(mockRumMonitor)
            .addError("Glide $fakeName error", RumErrorSource.SOURCE, throwable, emptyMap())
    }

    @Test
    fun `handles null throwable`() {
        testedStrategy.handle(null)

        verifyNoInteractions(mockRumMonitor)
    }

    companion object {
        val datadog = DatadogSingletonTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(datadog)
        }
    }
}
