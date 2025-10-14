/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.storage.ExposureEventRecordWriter
import com.datadog.android.flags.internal.storage.NoOpRecordWriter
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsFeatureTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @StringForgery
    lateinit var fakeApplicationId: String

    private lateinit var testedFeature: FlagsFeature

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockExecutorService
//        whenever(mockSdkCore.getDatadogContext()) doReturn mockDatadogContext

        testedFeature = FlagsFeature(
            sdkCore = mockSdkCore,
            flagsConfiguration = FlagsConfiguration.Builder().build()
        )
    }

    // region onContextUpdate

    @Test
    fun `M update applicationId W onContextUpdate { rum feature with non-null application_id }`(forge: Forge) {
        // Given
        val fakeContext = mapOf(
            "application_id" to fakeApplicationId,
            "other_key" to forge.anAlphabeticalString()
        )

        // When
        testedFeature.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)

        // Then
        assertThat(testedFeature.applicationId).isEqualTo(fakeApplicationId)
    }

    // endregion

    // region Lifecycle Methods

    @Test
    fun `M set context update receiver W onInitialize`() {
        // When
        testedFeature.onInitialize(mockContext)

        // Then
        verify(mockSdkCore).setContextUpdateReceiver(testedFeature)
    }

    @Test
    fun `M initialize processor and dataWriter W onInitialize`() {
        // Given
        assertThat(testedFeature.processor).isInstanceOf(NoOpEventsProcessor::class.java)
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpRecordWriter::class.java)

        // When
        testedFeature.onInitialize(mockContext)

        // Then
        assertThat(testedFeature.processor).isInstanceOf(ExposureEventsProcessor::class.java)
        assertThat(testedFeature.dataWriter).isInstanceOf(ExposureEventRecordWriter::class.java)
    }

    @Test
    fun `M initialize precomputedRequestFactory W constructor`() {
        // When

        // Then
        assertThat(
            testedFeature.precomputedRequestFactory
        ).isNotNull()
    }

    @Test
    fun `M remove context update receiver W onStop`() {
        // When
        testedFeature.onStop()

        // Then
        verify(mockSdkCore).removeContextUpdateReceiver(testedFeature)
    }

    @Test
    fun `M reset dataWriter to NoOp W onStop`() {
        // Given
        testedFeature.onInitialize(mockContext) // Initialize with real dataWriter
        assertThat(testedFeature.dataWriter).isInstanceOf(ExposureEventRecordWriter::class.java)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpRecordWriter::class.java)
    }

    // endregion

    // region General

    @Test
    fun `M have correct feature name W constructor`() {
        // Then
        assertThat(testedFeature.name).isEqualTo(FLAGS_FEATURE_NAME)
    }

    // endregion
}
