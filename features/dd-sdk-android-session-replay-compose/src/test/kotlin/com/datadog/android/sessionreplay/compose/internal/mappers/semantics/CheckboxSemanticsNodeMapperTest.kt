/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.BOX_BORDER_WIDTH_DP
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.CHECKBOX_CORNER_RADIUS
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.CHECKBOX_SIZE_DP
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.DEFAULT_COLOR_BLACK
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.DEFAULT_COLOR_WHITE
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.STROKE_WIDTH_DP
import com.datadog.android.sessionreplay.compose.internal.utils.ColorUtils
import com.datadog.android.sessionreplay.compose.internal.utils.PathUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class CheckboxSemanticsNodeMapperTest : AbstractSemanticsNodeMapperTest() {
    private lateinit var testedMapper: CheckboxSemanticsNodeMapper

    @Mock
    private lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    lateinit var mockConfig: SemanticsConfiguration

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @LongForgery(min = 0xffffffff)
    var fakeBorderColor: Long = 0L

    @LongForgery(min = 0xffffffff)
    var fakeFillColor: Long = 0L

    @LongForgery(min = 0xffffffff)
    var fakeCheckmarkColor: Long = 0L

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeBorderColorHexString: String

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeFillColorHexString: String

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeCheckmarkColorHexString: String

    @Mock
    private lateinit var mockUiContext: UiContext

    @Mock
    private lateinit var mockPath: Path

    @Mock
    private lateinit var mockPathUtils: PathUtils

    @Mock
    private lateinit var mockColorUtils: ColorUtils

    @Mock
    private lateinit var mockImageWireframeHelper: ImageWireframeHelper

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)

        whenever(mockUiContext.imageWireframeHelper)
            .thenReturn(mockImageWireframeHelper)

        whenever(mockUiContext.density)
            .thenReturn(fakeDensity)

        whenever(mockUiContext.textAndInputPrivacy)
            .thenReturn(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)

        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn rectToBounds(
            fakeBounds,
            fakeDensity
        )

        mockSemanticsNodeWithBound {
            whenever(mockSemanticsNode.layoutInfo).doReturn(mockLayoutInfo)
            whenever(mockSemanticsNode.config).doReturn(mockConfig)
        }

        whenever(mockPathUtils.asAndroidPathSafe(any()))
            .thenReturn(mock())

        whenever(mockSemanticsUtils.resolveCheckmarkColor(mockSemanticsNode))
            .thenReturn(fakeCheckmarkColor)

        whenever(mockSemanticsUtils.resolveBorderColor(mockSemanticsNode))
            .thenReturn(fakeBorderColor)

        whenever(mockSemanticsUtils.resolveCheckPath(mockSemanticsNode))
            .thenReturn(mockPath)

        mockColorStringFormatter(fakeBorderColor, fakeBorderColorHexString)
        mockColorStringFormatter(fakeFillColor, fakeFillColorHexString)
        mockColorStringFormatter(fakeCheckmarkColor, fakeCheckmarkColorHexString)

        testedMapper = CheckboxSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils,
            pathUtils = mockPathUtils,
            colorUtils = mockColorUtils
        )
    }

    @Test
    fun `M return the unchecked wireframe W map { unchecked, reflection resolution successful }`() {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.Off)

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val actualWireframe = semanticsWireframe.wireframes[0] as MobileSegment.Wireframe.ShapeWireframe

        val expectedShapeBorder = MobileSegment.ShapeBorder(
            color = fakeBorderColorHexString,
            width = BOX_BORDER_WIDTH_DP
        )

        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = DEFAULT_COLOR_WHITE,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        assertThat(actualWireframe.border).isEqualTo(expectedShapeBorder)
        assertThat(actualWireframe.shapeStyle).isEqualTo(expectedShapeStyle)
    }

    @Test
    fun `M return the unchecked wireframe W map { unchecked, reflection resolution failure }`() {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.Off)

        whenever(mockSemanticsUtils.resolveBorderColor(mockSemanticsNode))
            .thenReturn(null)

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val actualWireframe = semanticsWireframe.wireframes[0] as MobileSegment.Wireframe.ShapeWireframe

        val expectedShapeBorder = MobileSegment.ShapeBorder(
            color = DEFAULT_COLOR_BLACK,
            width = BOX_BORDER_WIDTH_DP
        )

        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = DEFAULT_COLOR_WHITE,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        assertThat(actualWireframe.border).isEqualTo(expectedShapeBorder)
        assertThat(actualWireframe.shapeStyle).isEqualTo(expectedShapeStyle)
    }

    @Test
    fun `M return fallback W map { checked, reflection resolution failure, got fill color }`() {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        whenever(mockSemanticsUtils.resolveCheckPath(mockSemanticsNode))
            .thenReturn(null)

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val backgroundWireframe = semanticsWireframe.wireframes[0] as MobileSegment.Wireframe.ShapeWireframe

        val expectedBgShapeBorder = MobileSegment.ShapeBorder(
            color = fakeBorderColorHexString,
            width = BOX_BORDER_WIDTH_DP
        )

        val expectedBgShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = DEFAULT_COLOR_WHITE,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        assertThat(backgroundWireframe.border).isEqualTo(expectedBgShapeBorder)
        assertThat(backgroundWireframe.shapeStyle).isEqualTo(expectedBgShapeStyle)
    }

    @Test
    fun `M return the correct fallback fg wireframe W map { checked, reflection resolution failure }`() {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        whenever(mockSemanticsUtils.resolveCheckPath(mockSemanticsNode))
            .thenReturn(null)

        whenever(mockSemanticsUtils.resolveCheckboxFillColor(mockSemanticsNode))
            .thenReturn(fakeFillColor)

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val foregroundWireframe = semanticsWireframe.wireframes[1] as MobileSegment.Wireframe.ShapeWireframe
        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = DEFAULT_COLOR_WHITE,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        assertThat(foregroundWireframe.shapeStyle).isEqualTo(expectedShapeStyle)
    }

    @Test
    fun `M return fallback fg W map { checked, resolution fail, no fill }`() {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        whenever(mockSemanticsUtils.resolveCheckboxFillColor(mockSemanticsNode))
            .thenReturn(null)

        whenever(mockSemanticsUtils.resolveCheckPath(mockSemanticsNode))
            .thenReturn(null)

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(semanticsWireframe.wireframes).hasSize(2)

        val foregroundWireframe = semanticsWireframe.wireframes[1] as MobileSegment.Wireframe.ShapeWireframe
        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = DEFAULT_COLOR_BLACK,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        assertThat(foregroundWireframe.shapeStyle).isEqualTo(expectedShapeStyle)
    }

    @Test
    fun `M return fallback W map { checked, resolution fail, image wireframe creation fail }`() {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val foregroundWireframe = semanticsWireframe.wireframes[1] as MobileSegment.Wireframe.ShapeWireframe
        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = DEFAULT_COLOR_BLACK,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        assertThat(foregroundWireframe.shapeStyle).isEqualTo(expectedShapeStyle)
    }

    @Test
    fun `M return fallback W map { checked, resolution fail, bmp creation from path fail }`() {
        // Given
        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        // When
        val semanticsWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val foregroundWireframe = semanticsWireframe.wireframes[1] as MobileSegment.Wireframe.ShapeWireframe
        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = DEFAULT_COLOR_BLACK,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        assertThat(foregroundWireframe.shapeStyle).isEqualTo(expectedShapeStyle)
    }

    @Test
    fun `M call image wireframe creation W map { checked, reflection resolution success }`() {
        // Given
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn fakeGlobalBounds

        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        whenever(mockSemanticsUtils.resolveCheckboxFillColor(mockSemanticsNode))
            .thenReturn(fakeFillColor)

        whenever(mockColorUtils.parseColorSafe(fakeFillColorHexString))
            .thenReturn(fakeFillColor.toInt())

        whenever(mockColorUtils.parseColorSafe(fakeCheckmarkColorHexString))
            .thenReturn(fakeCheckmarkColor.toInt())

        whenever(
            mockUiContext.imageWireframeHelper.createImageWireframeByPath(
                id = any(),
                globalBounds = eq(fakeGlobalBounds),
                path = any(),
                strokeColor = eq(fakeCheckmarkColor.toInt()),
                strokeWidth = eq(STROKE_WIDTH_DP.toInt()),
                targetWidth = eq(CHECKBOX_SIZE_DP),
                targetHeight = eq(CHECKBOX_SIZE_DP),
                density = eq(fakeDensity),
                imagePrivacy = eq(ImagePrivacy.MASK_NONE),
                isContextualImage = eq(false),
                asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
                clipping = eq(null),
                shapeStyle = eq(null),
                border = eq(null),
                customResourceIdCacheKey = eq(null)
            )
        ).thenReturn(mock<MobileSegment.Wireframe.ImageWireframe>())

        // When
        testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = fakeFillColorHexString,
            opacity = 1f,
            cornerRadius = CHECKBOX_CORNER_RADIUS
        )

        val expectedBorder = MobileSegment.ShapeBorder(
            color = fakeBorderColorHexString,
            width = BOX_BORDER_WIDTH_DP
        )

        verify(mockUiContext.imageWireframeHelper).createImageWireframeByPath(
            id = any(),
            globalBounds = eq(fakeGlobalBounds),
            path = any(),
            strokeColor = eq(fakeCheckmarkColor.toInt()),
            strokeWidth = eq(STROKE_WIDTH_DP.toInt()),
            targetWidth = eq(CHECKBOX_SIZE_DP),
            targetHeight = eq(CHECKBOX_SIZE_DP),
            density = eq(fakeDensity),
            isContextualImage = eq(false),
            imagePrivacy = eq(ImagePrivacy.MASK_NONE),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = eq(null),
            shapeStyle = eq(expectedShapeStyle),
            border = eq(expectedBorder),
            customResourceIdCacheKey = eq(null)
        )
    }

    @Test
    fun `M return image wireframe W map { checked, reflection resolution success }`() {
        // Given
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn fakeGlobalBounds

        whenever(mockConfig.getOrNull(SemanticsProperties.ToggleableState))
            .thenReturn(ToggleableState.On)

        whenever(mockSemanticsUtils.resolveCheckboxFillColor(mockSemanticsNode))
            .thenReturn(fakeFillColor)

        whenever(mockColorUtils.parseColorSafe(fakeFillColorHexString))
            .thenReturn(fakeFillColor.toInt())

        whenever(mockColorUtils.parseColorSafe(fakeCheckmarkColorHexString))
            .thenReturn(fakeCheckmarkColor.toInt())

        whenever(
            mockUiContext.imageWireframeHelper.createImageWireframeByPath(
                id = any(),
                globalBounds = eq(fakeGlobalBounds),
                path = any(),
                strokeColor = eq(fakeCheckmarkColor.toInt()),
                strokeWidth = eq(STROKE_WIDTH_DP.toInt()),
                targetWidth = eq(CHECKBOX_SIZE_DP),
                targetHeight = eq(CHECKBOX_SIZE_DP),
                density = eq(fakeDensity),
                isContextualImage = eq(false),
                imagePrivacy = eq(ImagePrivacy.MASK_NONE),
                asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
                clipping = eq(null),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                customResourceIdCacheKey = eq(null)
            )
        ).thenReturn(mock<MobileSegment.Wireframe.ImageWireframe>())

        // When
        val wireframes = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframes.wireframes).hasSize(1)
        assertThat(wireframes.wireframes[0]).isInstanceOf(MobileSegment.Wireframe.ImageWireframe::class.java)
    }

    @Test
    fun `M show unchecked wireframe W map() { masked }`() {
        // Given
        whenever(mockUiContext.textAndInputPrivacy)
            .thenReturn(TextAndInputPrivacy.MASK_ALL_INPUTS)

        // When
        val checkboxWireframe = testedMapper.map(
            semanticsNode = mockSemanticsNode,
            parentContext = mockUiContext,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(checkboxWireframe.wireframes).hasSize(1)
        val actualWireframe = checkboxWireframe.wireframes[0] as MobileSegment.Wireframe.ShapeWireframe

        verify(mockUiContext.imageWireframeHelper, never()).createImageWireframeByBitmap(
            id = any(),
            globalBounds = any(),
            bitmap = any(),
            density = any(),
            isContextualImage = any(),
            imagePrivacy = any(),
            asyncJobStatusCallback = any(),
            clipping = anyOrNull(),
            shapeStyle = anyOrNull(),
            border = anyOrNull()
        )
        assertThat(actualWireframe.shapeStyle?.backgroundColor).isEqualTo(DEFAULT_COLOR_WHITE)
        assertThat(actualWireframe.border?.color).isEqualTo(DEFAULT_COLOR_BLACK)
    }
}
