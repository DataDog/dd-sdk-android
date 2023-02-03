/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import android.content.Context
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EnvironmentProvider
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogEnvironmentProviderTest {

    private lateinit var testedProvider: EnvironmentProvider

    @BeforeEach
    fun `set up`() {
        testedProvider = DatadogEnvironmentProvider(coreFeature.mockInstance)
    }

    @Test
    fun `𝕄 return tracking consent 𝕎 trackingConsent()`(
        @Forgery fakeTrackingConsent: TrackingConsent
    ) {
        // Given
        whenever(
            coreFeature.mockInstance.trackingConsentProvider.getConsent()
        ) doReturn fakeTrackingConsent

        // When + Then
        assertThat(testedProvider.trackingConsent).isEqualTo(fakeTrackingConsent)
    }

    @Test
    fun `𝕄 return root storage dir 𝕎 rootStorageDir()`() {
        // When + Then
        assertThat(testedProvider.rootStorageDir).isEqualTo(coreFeature.mockInstance.storageDir)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
