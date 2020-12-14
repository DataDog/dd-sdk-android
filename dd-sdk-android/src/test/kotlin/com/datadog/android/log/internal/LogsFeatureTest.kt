/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import com.datadog.android.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsFeatureTest : SdkFeatureTest<Log, Configuration.Feature.Logs, LogsFeature>() {

    override fun createTestedFeature(): LogsFeature {
        return LogsFeature
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.Logs {
        return forge.getForgery()
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfig)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(LogFileStrategy::class.java)
    }

    @Test
    fun `𝕄 create a logs uploader 𝕎 createUploader()`() {
        // Given
        testedFeature.endpointUrl = fakeConfig.endpointUrl

        // When
        val uploader = testedFeature.createUploader()

        // Then
        assertThat(uploader).isInstanceOf(LogsOkHttpUploader::class.java)
        val logsUploader = uploader as LogsOkHttpUploader
        assertThat(logsUploader.url).startsWith(fakeConfig.endpointUrl)
        assertThat(logsUploader.client).isSameAs(CoreFeature.okHttpClient)
    }
}
