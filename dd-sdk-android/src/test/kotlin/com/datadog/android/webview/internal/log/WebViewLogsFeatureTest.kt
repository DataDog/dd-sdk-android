/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.log.internal.net.LogsRequestFactory
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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
internal class WebViewLogsFeatureTest :
    SdkFeatureTest<JsonObject, Configuration.Feature.Logs, WebViewLogsFeature>() {

    override fun createTestedFeature(): WebViewLogsFeature {
        return WebViewLogsFeature(coreFeature.mockInstance)
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.Logs {
        return forge.getForgery()
    }

    override fun featureDirName(): String {
        return "web-logs"
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(WebViewLogFilePersistenceStrategy::class.java)
    }

    @Test
    fun `𝕄 create a logs request factory 𝕎 createRequestFactory()`() {
        // When
        val requestFactory = testedFeature.createRequestFactory(fakeConfigurationFeature)

        // Then
        assertThat(requestFactory).isInstanceOf(LogsRequestFactory::class.java)
    }
}
