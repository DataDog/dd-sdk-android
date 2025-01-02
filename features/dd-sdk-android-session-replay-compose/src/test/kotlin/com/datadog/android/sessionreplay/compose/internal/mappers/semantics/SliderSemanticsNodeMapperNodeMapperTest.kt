/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.dp
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.BackgroundInfo
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
internal class SliderSemanticsNodeMapperNodeMapperTest : AbstractSemanticsNodeMapperTest() {

    private lateinit var testedSliderSemanticsNodeMapper: SliderSemanticsNodeMapper

    @Mock
    private lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    private lateinit var mockProgressBarRangeInfo: ProgressBarRangeInfo

    @Mock
    private lateinit var mockRange: ClosedFloatingPointRange<Float>

    @LongForgery(min = 0xffffffff)
    var fakeBackgroundColor: Long = 0L

    @FloatForgery
    var fakeCornerRadius: Float = 0f

    @FloatForgery
    var fakeStart: Float = 0f

    @FloatForgery
    var fakeCurrent: Float = 0f

    @FloatForgery
    var fakeEndInclusive: Float = 0f

    @Forgery
    lateinit var fakeUiContext: UiContext

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        testedSliderSemanticsNodeMapper = SliderSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    private fun mockSemanticsNode(): SemanticsNode {
        return mockSemanticsNodeWithBound {
            whenever(mockSemanticsNode.layoutInfo).doReturn(mockLayoutInfo)
        }
    }

    fun `M return the correct wireframe W map`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode()
        val fakeGlobalBounds = rectToBounds(fakeBounds, fakeUiContext.density)
        val fakeBackgroundInfo = BackgroundInfo(
            globalBounds = fakeGlobalBounds,
            color = fakeBackgroundColor,
            cornerRadius = fakeCornerRadius
        )
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn fakeGlobalBounds
        whenever(mockSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)) doReturn listOf(
            fakeBackgroundInfo
        )
        whenever(mockSemanticsUtils.getProgressBarRangeInfo(mockSemanticsNode)) doReturn
            mockProgressBarRangeInfo
        whenever(mockProgressBarRangeInfo.range) doReturn mockRange
        whenever(mockProgressBarRangeInfo.current) doReturn fakeCurrent
        whenever(mockRange.start) doReturn fakeStart
        whenever(mockRange.endInclusive) doReturn fakeEndInclusive

        // When
        val actual = testedSliderSemanticsNodeMapper.map(
            mockSemanticsNode,
            fakeUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        val trackHeight = DEFAULT_TRACK_HEIGHT.value * fakeUiContext.density
        val yTrackOffset = (fakeGlobalBounds.height - trackHeight).toLong() / 2
        val fakeProgress = fakeCurrent / (fakeEndInclusive - fakeStart)
        val xOffset = fakeProgress * fakeGlobalBounds.width + fakeGlobalBounds.x
        val thumbHeight = DEFAULT_THUMB_RADIUS.value * 2 * fakeUiContext.density
        val expectedTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32) + 0,
            x = fakeGlobalBounds.x,
            y = fakeGlobalBounds.y + yTrackOffset,
            width = fakeGlobalBounds.width,
            height = trackHeight.toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DEFAULT_COLOR,
                cornerRadius = trackHeight / 2
            )
        )
        val yThumbOffset = (fakeGlobalBounds.height - thumbHeight).toLong() / 2
        val expectedThumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32) + 1,
            x = xOffset.toLong(),
            y = fakeGlobalBounds.y + yThumbOffset,
            width = thumbHeight.toLong(),
            height = thumbHeight.toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DEFAULT_COLOR,
                cornerRadius = thumbHeight / 2
            )
        )
        assertThat(actual.wireframes).contains(expectedThumbWireframe, expectedTrackWireframe)
    }

    companion object {
        private const val DEFAULT_COLOR = "#000000FF"
        private val DEFAULT_THUMB_RADIUS = 4.dp
        private val DEFAULT_TRACK_HEIGHT = 4.dp
    }
}
