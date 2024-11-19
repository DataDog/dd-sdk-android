/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.replay

import android.content.Context
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.webview.internal.storage.WebViewDataWriter
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewReplayFeatureTest {

    private lateinit var testedFeature: WebViewReplayFeature

    @Mock
    lateinit var mockRequestFactory: RequestFactory

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockAppContext: Context

    @BeforeEach
    fun `set up`() {
        testedFeature = WebViewReplayFeature(mockSdkCore, mockRequestFactory)
        whenever(mockSdkCore.internalLogger) doReturn mock()
    }

    @Test
    fun `M provide feature name W name()`() {
        // Then
        assertThat(testedFeature.name)
            .isEqualTo(WebViewReplayFeature.WEB_REPLAY_FEATURE_NAME)
    }

    @Test
    fun `M provide correct storage configuration W storageConfiguration()`() {
        // Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(WebViewReplayFeature.STORAGE_CONFIGURATION)
    }

    @Test
    fun `M unregister writer W onStop()`() {
        // Given
        testedFeature.onInitialize(mockAppContext)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.initialized).isFalse
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(NoOpDataWriter::class.java)
    }

    @Test
    fun `M initialize writer W initialize()`() {
        // When
        testedFeature.onInitialize(mockAppContext)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(WebViewDataWriter::class.java)
        assertThat(testedFeature.initialized).isTrue
    }
}
