/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.tracing.internal.domain.TracingFileStrategy
import com.datadog.android.tracing.internal.net.TracesOkHttpUploader
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
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
internal class TracesFeatureTest :
    SdkFeatureTest<DDSpan, Configuration.Feature.Tracing, TracesFeature>() {

    override fun createTestedFeature(): TracesFeature {
        return TracesFeature
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.Tracing {
        return forge.getForgery()
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(TracingFileStrategy::class.java)
    }

    @Test
    fun `𝕄 create a tracing uploader 𝕎 createUploader()`() {
        // Given
        testedFeature.endpointUrl = fakeConfigurationFeature.endpointUrl

        // When
        val uploader = testedFeature.createUploader()

        // Then
        assertThat(uploader).isInstanceOf(TracesOkHttpUploader::class.java)
        val tracesUploader = uploader as TracesOkHttpUploader
        assertThat(tracesUploader.url).startsWith(fakeConfigurationFeature.endpointUrl)
        assertThat(tracesUploader.client).isSameAs(CoreFeature.okHttpClient)
    }
}
