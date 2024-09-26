/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper.Companion.HIDDEN_VIEW_PLACEHOLDER_TEXT
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
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
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class HiddenViewMapperTest {
    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockMappingContext: MappingContext

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    @Mock
    lateinit var mockGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedViewMapper: HiddenViewMapper

    @BeforeEach
    fun setup() {
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(any(), any()))
            .thenReturn(mockGlobalBounds)

        whenever(mockMappingContext.systemInformation)
            .thenReturn(mockSystemInformation)

        whenever(mockSystemInformation.screenDensity)
            .thenReturn(1f)

        testedViewMapper = HiddenViewMapper(
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver
        )
    }

    @Test
    fun `M return a placeholder with correct label W map()`(
        @Mock mockView: View
    ) {
        // When
        val wireframesList = testedViewMapper.map(
            view = mockView,
            asyncJobStatusCallback = mockAsyncJobStatusCallback,
            internalLogger = mockInternalLogger,
            mappingContext = mockMappingContext
        )

        // Then
        val wireframe = wireframesList[0]
        check(wireframe is MobileSegment.Wireframe.PlaceholderWireframe)
        assertThat(wireframe.label).isEqualTo(HIDDEN_VIEW_PLACEHOLDER_TEXT)
    }

    @Test
    fun `M return empty list W map() { failed to resolve unique id }`(
        @Mock mockView: View
    ) {
        // When
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)
        val wireframesList = testedViewMapper.map(
            view = mockView,
            asyncJobStatusCallback = mockAsyncJobStatusCallback,
            internalLogger = mockInternalLogger,
            mappingContext = mockMappingContext
        )

        // Then
        assertThat(wireframesList).isEmpty()
    }
}
