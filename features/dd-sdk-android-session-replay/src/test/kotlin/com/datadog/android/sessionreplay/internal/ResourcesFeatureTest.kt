/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.net.ResourcesRequestFactory
import com.datadog.android.sessionreplay.internal.storage.NoOpResourcesWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.net.URL

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourcesFeatureTest {
    private lateinit var testedFeature: ResourcesFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockContext: Context

    @BeforeEach
    fun setup() {
        whenever(mockSdkCore.internalLogger)
            .thenReturn(mockInternalLogger)

        testedFeature = ResourcesFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = null
        )
    }

    @Test
    fun `M use customEndpointUrl W provided`(
        @Forgery fakeCustomUrl: URL
    ) {
        // When
        testedFeature = ResourcesFeature(
            sdkCore = mockSdkCore,
            customEndpointUrl = fakeCustomUrl.host
        )

        // Then
        val requestFactory = testedFeature.requestFactory as ResourcesRequestFactory
        assertThat(requestFactory.customEndpointUrl).isEqualTo(fakeCustomUrl.host)
    }

    @Test
    fun `M clean up W onStop()`() {
        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpResourcesWriter::class.java)
        assertThat(testedFeature.initialized).isFalse
    }

    @Test
    fun `M perform setup W onInitialize()`() {
        // When
        testedFeature.onInitialize(mockContext)

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(ResourcesWriter::class.java)
        assertThat(testedFeature.initialized).isTrue
    }
}
