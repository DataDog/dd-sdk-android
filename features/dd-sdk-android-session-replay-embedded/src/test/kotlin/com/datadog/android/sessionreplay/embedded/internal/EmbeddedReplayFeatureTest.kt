/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal

import android.content.Context
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.sessionreplay.embedded.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.embedded.internal.storage.EmbeddedReplayDataWriter
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedReplayFeatureTest {

    private lateinit var testedFeature: EmbeddedReplayFeature

    @Mock
    lateinit var mockRequestFactory: RequestFactory

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockAppContext: Context

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
        testedFeature = EmbeddedReplayFeature(mockSdkCore, mockRequestFactory)
    }

    @Test
    fun `M provide feature name W name()`() {
        // Then
        assertThat(testedFeature.name).isEqualTo(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
    }

    @Test
    fun `M provide correct storage configuration W storageConfiguration()`() {
        // Then
        assertThat(testedFeature.storageConfiguration).isEqualTo(EmbeddedReplayFeature.STORAGE_CONFIGURATION)
    }

    @Test
    fun `M provide the given request factory W requestFactory`() {
        // Then
        assertThat(testedFeature.requestFactory).isSameAs(mockRequestFactory)
    }

    @Test
    fun `M initialize writer W onInitialize()`() {
        // When
        testedFeature.onInitialize(mockAppContext)

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(EmbeddedReplayDataWriter::class.java)
    }

    @Test
    fun `M reset writer to no-op W onStop()`() {
        // Given
        testedFeature.onInitialize(mockAppContext)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpDataWriter::class.java)
    }
}
