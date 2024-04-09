/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.rum.domain.NativeRumViewsCache
import com.datadog.android.webview.internal.storage.WebViewDataWriter
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewRumFeatureTest {

    private lateinit var testedFeature: WebViewRumFeature

    @Mock
    lateinit var mockRequestFactory: RequestFactory

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockNativeRumViewsCache: NativeRumViewsCache

    @BeforeEach
    fun `set up`() {
        testedFeature = WebViewRumFeature(mockSdkCore, mockRequestFactory, mockNativeRumViewsCache)
        whenever(mockSdkCore.internalLogger) doReturn mock()
    }

    @Test
    fun `𝕄 initialize data writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(WebViewDataWriter::class.java)
    }

    @Test
    fun `𝕄 provide web view RUM feature name 𝕎 name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(WebViewRumFeature.WEB_RUM_FEATURE_NAME)
    }

    @Test
    fun `𝕄 provide initial request factory 𝕎 requestFactory()`() {
        // When+Then
        assertThat(testedFeature.requestFactory)
            .isSameAs(mockRequestFactory)
    }

    @Test
    fun `𝕄 provide default storage configuration 𝕎 storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }

    @Test
    fun `𝕄 reset data writer 𝕎 onStop()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpDataWriter::class.java)
    }

    @Test
    fun `M register the context to the native cache W onContextUpdate { RUM }`() {
        // Given
        val fakeContext = mock<Map<String, Any?>>()

        // When
        testedFeature.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)

        // Then
        verify(mockNativeRumViewsCache).addToCache(fakeContext)
    }

    @Test
    fun `M do nothing W onContextUpdate { no RUM feature }`(@StringForgery fakeFeatureName: String) {
        // Given
        val fakeContext = mock<Map<String, Any?>>()

        // When
        testedFeature.onContextUpdate(fakeFeatureName, fakeContext)

        // Then
        verifyNoInteractions(mockNativeRumViewsCache)
    }
}
