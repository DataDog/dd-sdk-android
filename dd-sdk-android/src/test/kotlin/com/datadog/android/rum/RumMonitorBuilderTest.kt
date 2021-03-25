/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.content.Context
import android.os.Looper
import android.util.Log
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockCoreFeature
import com.datadog.android.utils.mockDevLogHandler
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumMonitorBuilderTest {

    lateinit var testedBuilder: RumMonitor.Builder

    @Mock
    lateinit var mockContext: Context

    @Forgery
    lateinit var fakeConfig: Configuration.Feature.RUM

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    lateinit var mockDevLogHandler: LogHandler

    @BeforeEach
    fun `set up`() {
        mockDevLogHandler = mockDevLogHandler()
        mockContext = mockContext()
        mockCoreFeature(rumApplicationId = fakeApplicationId)
        RumFeature.initialize(mockContext, fakeConfig)

        testedBuilder = RumMonitor.Builder()
    }

    @AfterEach
    fun `tear down`() {
        RumFeature.stop()
        CoreFeature.stop()
    }

    @Test
    fun `𝕄 builds a default RumMonitor 𝕎 build()`() {
        // When
        val monitor = testedBuilder.build()

        // Then
        check(monitor is DatadogRumMonitor)
        assertThat(monitor.rootScope).isInstanceOf(RumApplicationScope::class.java)
        assertThat(monitor.rootScope)
            .overridingErrorMessage("Expecting rootscope to have applicationId $fakeApplicationId")
            .matches {
                (it as RumApplicationScope)
                    .getRumContext()
                    .applicationId == fakeApplicationId
            }
        assertThat(monitor.handler.looper).isSameAs(Looper.getMainLooper())
        assertThat(monitor.samplingRate).isEqualTo(fakeConfig.samplingRate)
    }

    @Test
    fun `𝕄 builds a RumMonitor with custom sampling 𝕎 build()`(
        @FloatForgery(0f, 100f) samplingRate: Float
    ) {
        // When
        val monitor = testedBuilder
            .sampleRumSessions(samplingRate)
            .build()

        // Then
        check(monitor is DatadogRumMonitor)
        assertThat(monitor.rootScope).isInstanceOf(RumApplicationScope::class.java)
        assertThat(monitor.rootScope)
            .overridingErrorMessage("Expecting rootscope to have applicationId $fakeApplicationId")
            .matches {
                (it as RumApplicationScope)
                    .getRumContext()
                    .applicationId == fakeApplicationId
            }
        assertThat(monitor.handler.looper).isSameAs(Looper.getMainLooper())
        assertThat(monitor.samplingRate).isEqualTo(samplingRate)
    }

    @Test
    fun `𝕄 builds nothing 𝕎 build() and RumFeature is not initialized`() {
        // Given
        RumFeature.stop()

        // When
        val monitor = testedBuilder.build()

        // Then
        verify(mockDevLogHandler).handleLog(
            Log.ERROR,
            RumMonitor.Builder.RUM_NOT_ENABLED_ERROR_MESSAGE
        )
        check(monitor is NoOpRumMonitor)
    }
}
