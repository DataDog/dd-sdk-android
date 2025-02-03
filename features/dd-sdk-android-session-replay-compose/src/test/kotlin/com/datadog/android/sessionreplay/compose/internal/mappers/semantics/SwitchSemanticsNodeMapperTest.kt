/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.SwitchSemanticsNodeMapper.Companion.BORDER_WIDTH_DP
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.SwitchSemanticsNodeMapper.Companion.CORNER_RADIUS_DP
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.SwitchSemanticsNodeMapper.Companion.THUMB_DIAMETER_DP
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.SwitchSemanticsNodeMapper.Companion.TRACK_WIDTH_DP
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils.Companion.DEFAULT_COLOR_BLACK
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils.Companion.DEFAULT_COLOR_WHITE
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class SwitchSemanticsNodeMapperTest {
    private lateinit var testedMapper: SwitchSemanticsNodeMapper

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    lateinit var mockSemanticsUtils: SemanticsUtils

    @Mock
    lateinit var mockParentContext: UiContext

    @Mock
    lateinit var mockConfig: SemanticsConfiguration

    @IntForgery
    var fakeSemanticsId: Int = 0

    @BeforeEach
    fun `set up`() {
        whenever(mockSemanticsNode.id) doReturn fakeSemanticsId
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn fakeGlobalBounds
        whenever(mockSemanticsNode.config).thenReturn(mockConfig)
        whenever(mockParentContext.textAndInputPrivacy).thenReturn(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)

        testedMapper = SwitchSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    @Test
    fun `M return correct wireframe W map { switch off }`(
        @Mock mockAsyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.Off)

        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeSemanticsId.toLong() shl 32,
            x = fakeGlobalBounds.x,
            y = fakeGlobalBounds.y + (fakeGlobalBounds.height / 4),
            width = TRACK_WIDTH_DP,
            height = THUMB_DIAMETER_DP.toLong() / 2,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DEFAULT_COLOR_WHITE,
                cornerRadius = CORNER_RADIUS_DP
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BORDER_WIDTH_DP
            )
        )

        val expectedThumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32) + 1,
            x = fakeGlobalBounds.x,
            y = fakeGlobalBounds.y + (fakeGlobalBounds.height / 4) - (THUMB_DIAMETER_DP / 4),
            width = THUMB_DIAMETER_DP.toLong(),
            height = THUMB_DIAMETER_DP.toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DEFAULT_COLOR_WHITE,
                cornerRadius = CORNER_RADIUS_DP
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BORDER_WIDTH_DP
            )
        )

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockParentContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(semanticsWireframe.wireframes).hasSize(2)
        val trackWireframe = semanticsWireframe.wireframes[0] as MobileSegment.Wireframe.ShapeWireframe
        val thumbWireframe = semanticsWireframe.wireframes[1] as MobileSegment.Wireframe.ShapeWireframe

        assertThat(trackWireframe).isEqualTo(expectedTrackWireframe)
        assertThat(thumbWireframe).isEqualTo(expectedThumbWireframe)
    }

    @Test
    fun `M return correct wireframe W map { switch on }`(
        @Mock mockAsyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeSemanticsId.toLong() shl 32,
            x = fakeGlobalBounds.x,
            y = fakeGlobalBounds.y + (fakeGlobalBounds.height / 4),
            width = TRACK_WIDTH_DP,
            height = THUMB_DIAMETER_DP.toLong() / 2,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DEFAULT_COLOR_BLACK,
                cornerRadius = CORNER_RADIUS_DP
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BORDER_WIDTH_DP
            )
        )

        val expectedThumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32) + 1,
            x = fakeGlobalBounds.x + fakeGlobalBounds.width - THUMB_DIAMETER_DP,
            y = fakeGlobalBounds.y + (fakeGlobalBounds.height / 4) - (THUMB_DIAMETER_DP / 4),
            width = THUMB_DIAMETER_DP.toLong(),
            height = THUMB_DIAMETER_DP.toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DEFAULT_COLOR_BLACK,
                cornerRadius = CORNER_RADIUS_DP
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BORDER_WIDTH_DP
            )
        )

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockParentContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(semanticsWireframe.wireframes).hasSize(2)
        val trackWireframe = semanticsWireframe.wireframes[0] as MobileSegment.Wireframe.ShapeWireframe
        val thumbWireframe = semanticsWireframe.wireframes[1] as MobileSegment.Wireframe.ShapeWireframe

        assertThat(trackWireframe).isEqualTo(expectedTrackWireframe)
        assertThat(thumbWireframe).isEqualTo(expectedThumbWireframe)
    }

    @Test
    fun `M return empty track W map { masked }`(
        @Mock mockAsyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        whenever(mockParentContext.textAndInputPrivacy).thenReturn(
            TextAndInputPrivacy.MASK_ALL_INPUTS
        )

        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeSemanticsId.toLong() shl 32,
            x = fakeGlobalBounds.x,
            y = fakeGlobalBounds.y + (fakeGlobalBounds.height / 4),
            width = TRACK_WIDTH_DP,
            height = THUMB_DIAMETER_DP.toLong() / 2,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DEFAULT_COLOR_WHITE,
                cornerRadius = CORNER_RADIUS_DP
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_BLACK,
                width = BORDER_WIDTH_DP
            )
        )

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockParentContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(semanticsWireframe.wireframes).hasSize(1)
        val trackWireframe = semanticsWireframe.wireframes[0] as MobileSegment.Wireframe.ShapeWireframe

        assertThat(trackWireframe).isEqualTo(expectedTrackWireframe)
    }
}
