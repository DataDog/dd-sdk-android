/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.FlagsConfiguration
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsFeatureTest {

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedFeature: FlagsFeature

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.createSingleThreadExecutorService(any())) doReturn mockExecutorService
        whenever(mockSdkCore.getDatadogContext()) doReturn mockDatadogContext

        testedFeature = FlagsFeature(
            sdkCore = mockSdkCore,
            flagsConfiguration = FlagsConfiguration.Builder().build()
        )
    }

    // region Constructor

    @Test
    fun `M have correct feature name W constructor`() {
        // Then
        assertThat(testedFeature.name).isEqualTo(FLAGS_FEATURE_NAME)
    }

    // endregion
}
