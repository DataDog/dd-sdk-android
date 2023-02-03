/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.app.Application
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.storage.NoOpDataWriter
import com.datadog.android.v2.log.net.LogsRequestFactory
import com.datadog.android.v2.webview.internal.storage.WebViewLogsDataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewLogsFeatureTest {

    private lateinit var testedFeature: WebViewLogsFeature

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var endpointUrl: String

    @Mock
    lateinit var mockSdkCore: SdkCore

    @BeforeEach
    fun `set up`() {
        testedFeature = WebViewLogsFeature(endpointUrl)
    }

    @Test
    fun `𝕄 initialize data writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, mock())

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(WebViewLogsDataWriter::class.java)
    }

    @Test
    fun `𝕄 reset data writer 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, mock())

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(NoOpDataWriter::class.java)
    }

    @Test
    fun `𝕄 provide web view logs feature name 𝕎 name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(WebViewLogsFeature.WEB_LOGS_FEATURE_NAME)
    }

    @Test
    fun `𝕄 provide Logs request factory 𝕎 requestFactory()`() {
        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(LogsRequestFactory::class.java)
    }

    @Test
    fun `𝕄 provide default storage configuration 𝕎 storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
