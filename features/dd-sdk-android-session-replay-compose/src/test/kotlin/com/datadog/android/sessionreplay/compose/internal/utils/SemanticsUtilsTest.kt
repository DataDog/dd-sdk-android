/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.AnimationState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.datadog.android.sessionreplay.compose.internal.data.BitmapInfo
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
class SemanticsUtilsTest {

    private lateinit var testedSemanticsUtils: SemanticsUtils

    @Mock
    private lateinit var mockReflectionUtils: ReflectionUtils

    @Mock
    private lateinit var mockView: View

    @Mock
    private lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    private lateinit var mockLayoutInfo: LayoutInfo

    @Mock
    private lateinit var mockModifierInfo: ModifierInfo

    @Mock
    private lateinit var mockOnDraw: Any

    @Mock
    private lateinit var mockCheckCache: Any

    @Mock
    private lateinit var mockModifier: Modifier

    private var fakeOffset: Offset = Offset(0f, 0f)

    @FloatForgery(min = 1f, max = 10f)
    private var fakeDensity: Float = 0f

    @Mock
    private lateinit var mockConfig: SemanticsConfiguration

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedSemanticsUtils = SemanticsUtils(
            reflectionUtils = mockReflectionUtils
        )
        whenever(mockSemanticsNode.layoutInfo) doReturn mockLayoutInfo
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockModifierInfo)
        whenever(mockModifierInfo.modifier) doReturn mockModifier
        whenever(mockLayoutInfo.density) doReturn Density(fakeDensity)
        whenever(mockSemanticsNode.config) doReturn mockConfig
        whenever(mockReflectionUtils.isDrawBehindElementClass(mockModifier)) doReturn true
        whenever(mockReflectionUtils.getOnDraw(mockModifier)) doReturn mockOnDraw
        whenever(mockReflectionUtils.getCheckCache(mockOnDraw)) doReturn mockCheckCache
        fakeOffset = Offset(x = forge.aFloat(), y = forge.aFloat())
    }

    private data class TextLayoutTestData(
        val fakeText: AnnotatedString,
        val fakeColorValue: ULong,
        val fakeFontSize: Float,
        val fakeFontFamily: FontFamily,
        val fakeTextAlign: TextAlign,
        val textLayoutResult: TextLayoutResult
    )

    private fun setupTextLayoutMocks(forge: Forge): TextLayoutTestData {
        val fakeText = AnnotatedString(forge.aString())
        val fakeColorValue = forge.aLong().toULong()
        val fakeFontSize = forge.aFloat()
        val fakeFontFamily = forge.anElementFrom(
            FontFamily.Serif,
            FontFamily.SansSerif,
            FontFamily.Cursive,
            FontFamily.Monospace,
            FontFamily.Default
        )
        val fakeTextAlign = forge.anElementFrom(TextAlign.values())
        val mockResult = mock<AccessibilityAction<(MutableList<TextLayoutResult>) -> Boolean>>()
        val mockAction = mock<(MutableList<TextLayoutResult>) -> Boolean>()
        val textLayoutResult = mock<TextLayoutResult>()
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        val mockTextLayoutInput = mock<TextLayoutInput>()
        val mockTextStyle = mock<TextStyle>()

        whenever(mockConfig.getOrNull(SemanticsActions.GetTextLayoutResult)) doReturn mockResult
        whenever(mockResult.action) doReturn mockAction
        whenever(textLayoutResult.layoutInput) doReturn mockTextLayoutInput
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as MutableList<TextLayoutResult>).add(textLayoutResult)
            true
        }.whenever(mockAction).invoke(textLayoutResults)
        whenever(mockTextLayoutInput.style) doReturn mockTextStyle
        whenever(mockTextLayoutInput.text) doReturn fakeText
        whenever(mockTextStyle.color) doReturn Color(fakeColorValue)
        whenever(mockTextStyle.textAlign) doReturn fakeTextAlign
        whenever(mockTextStyle.fontSize) doReturn TextUnit(fakeFontSize, TextUnitType.Sp)
        whenever(mockTextStyle.fontFamily) doReturn fakeFontFamily

        return TextLayoutTestData(
            fakeText = fakeText,
            fakeColorValue = fakeColorValue,
            fakeFontSize = fakeFontSize,
            fakeFontFamily = fakeFontFamily,
            fakeTextAlign = fakeTextAlign,
            textLayoutResult = textLayoutResult
        )
    }

    @Test
    fun `M return root semantics W findRootSemanticsNode`() {
        // Given
        val mockComposition = mock<Composition>()
        val mockOwner = mock<Any>()
        val mockSemanticsOwner = mock<SemanticsOwner>()
        whenever(mockReflectionUtils.getComposition(mockView)) doReturn mockComposition
        whenever(mockReflectionUtils.isWrappedCompositionClass(mockComposition)) doReturn true
        whenever(mockReflectionUtils.getOwner(mockComposition)) doReturn mockOwner
        whenever(mockReflectionUtils.isAndroidComposeView(mockOwner)) doReturn true
        whenever(mockReflectionUtils.getSemanticsOwner(mockOwner)) doReturn mockSemanticsOwner
        whenever(mockSemanticsOwner.unmergedRootSemanticsNode) doReturn mockSemanticsNode

        // When
        val result = testedSemanticsUtils.findRootSemanticsNode(mockView)

        // Then
        assertThat(result).isEqualTo(mockSemanticsNode)
    }

    @Test
    fun `M return shape W resolveBackgroundShape`() {
        // Given
        val mockShape = mock<Shape>()
        whenever(mockReflectionUtils.isBackgroundElement(mockModifier)) doReturn true
        whenever(mockReflectionUtils.getShape(mockModifier)) doReturn mockShape

        // When
        val result = testedSemanticsUtils.resolveBackgroundShape(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(mockShape)
    }

    @Test
    fun `M return check path W resolveCheckPath`(
        @Mock mockPath: Path
    ) {
        // Given
        whenever(mockReflectionUtils.getCheckPath(mockCheckCache)) doReturn mockPath

        // When
        val result = testedSemanticsUtils.resolveCheckPath(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(mockPath)
    }

    @Test
    fun `M return checkbox fill color W resolveCheckboxFillColor`(
        @IntForgery fakeColorValue: Int
    ) {
        // Given
        val fakeColor = Color(fakeColorValue)
        val mockAnimationState = mock<AnimationState<*, *>>()
        whenever(mockReflectionUtils.getBoxColor(mockOnDraw)) doReturn mockAnimationState
        whenever(mockAnimationState.value).thenReturn(fakeColor)

        // When
        val result = testedSemanticsUtils.resolveCheckboxFillColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(fakeColor.value.toLong())
    }

    @Test
    fun `M return radio button fill color W resolveRadioButtonColor`(
        @IntForgery fakeColorValue: Int
    ) {
        // Given
        val fakeColor = Color(fakeColorValue)
        val mockAnimationState = mock<AnimationState<*, *>>()
        whenever(mockReflectionUtils.getRadioColor(mockOnDraw)) doReturn mockAnimationState
        whenever(mockAnimationState.value).thenReturn(fakeColor)

        // When
        val result = testedSemanticsUtils.resolveRadioButtonColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(fakeColor.value.toLong())
    }

    @Test
    fun `M return checkmark color W resolveCheckmarkColor`(
        @IntForgery fakeColorValue: Int
    ) {
        // Given
        val fakeColor = Color(fakeColorValue)
        val mockAnimationState = mock<AnimationState<*, *>>()
        whenever(mockReflectionUtils.getCheckColor(mockOnDraw)) doReturn mockAnimationState
        whenever(mockAnimationState.value).thenReturn(fakeColor)

        // When
        val result = testedSemanticsUtils.resolveCheckmarkColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(fakeColor.value.toLong())
    }

    @Test
    fun `M return border color W resolveBorderColor`(
        @IntForgery fakeColorValue: Int
    ) {
        // Given
        val fakeColor = Color(fakeColorValue)
        val mockAnimationState = mock<AnimationState<*, *>>()
        whenever(mockReflectionUtils.getBorderColor(mockOnDraw)) doReturn mockAnimationState
        whenever(mockAnimationState.value).thenReturn(fakeColor)

        // When
        val result = testedSemanticsUtils.resolveBorderColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(fakeColor.value.toLong())
    }

    @Test
    fun `M return inner bounds W resolveInnerBounds`() {
        // Given
        val placeable = mock<Placeable>()
        whenever(mockReflectionUtils.getPlaceable(mockSemanticsNode)) doReturn placeable
        whenever(mockSemanticsNode.positionInRoot) doReturn fakeOffset

        // When
        val result = testedSemanticsUtils.resolveInnerBounds(mockSemanticsNode)
        val expected = GlobalBounds(
            x = (fakeOffset.x / fakeDensity).toLong(),
            y = (fakeOffset.y / fakeDensity).toLong(),
            width = (placeable.width / fakeDensity).toLong(),
            height = (placeable.width / fakeDensity).toLong()
        )

        // Then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M return corner radius W resolveCornerRadius`(
        @Forgery fakeBounds: GlobalBounds,
        @IntForgery fakeCornerSizeValue: Int
    ) {
        // Given
        val mockShape = mock<RoundedCornerShape>()
        val fakeDensity = Density(fakeDensity)
        val fakeCornerSize = CornerSize(fakeCornerSizeValue.dp)
        whenever(mockShape.topStart) doReturn fakeCornerSize

        // When
        val size = Size(
            fakeBounds.width.toFloat() * fakeDensity.density,
            fakeBounds.height.toFloat() * fakeDensity.density
        )
        val expected = fakeCornerSize.toPx(size, fakeDensity) / fakeDensity.density
        val result = testedSemanticsUtils.resolveCornerRadius(mockShape, fakeBounds, fakeDensity)

        // Then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M return color W resolveBackgroundColor`(forge: Forge) {
        // Given
        val fakeColorValue = forge.aLong()
        whenever(mockReflectionUtils.isBackgroundElement(mockModifier)) doReturn true
        whenever(mockReflectionUtils.getColor(mockModifier)) doReturn fakeColorValue

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(fakeColorValue)
    }

    @Test
    fun `M return TextLayoutInfo W resolveTextLayoutInfo modifier color is null`(forge: Forge) {
        // Given
        val testData = setupTextLayoutMocks(forge)

        // When
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode))

        // Then
        val expected = TextLayoutInfo(
            text = resolveAnnotatedString(testData.fakeText),
            color = testData.fakeColorValue,
            textAlign = testData.fakeTextAlign,
            fontSize = testData.fakeFontSize.toLong(),
            fontFamily = testData.fakeFontFamily
        )
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M return TextLayoutInfo W resolveTextLayoutInfo modifier color is not null`(forge: Forge) {
        // Given
        val testData = setupTextLayoutMocks(forge)
        val fakeModifierColorValue = forge.aLong().toULong()
        whenever(mockReflectionUtils.isTextStringSimpleElement(mockModifier)) doReturn true
        whenever(mockReflectionUtils.getColorProducerColor(mockModifier)) doReturn Color(
            fakeModifierColorValue
        )

        // When
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode))

        // Then
        val expected = TextLayoutInfo(
            text = resolveAnnotatedString(testData.fakeText),
            color = fakeModifierColorValue,
            textAlign = testData.fakeTextAlign,
            fontSize = testData.fakeFontSize.toLong(),
            fontFamily = testData.fakeFontFamily
        )
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M return TextLayoutInfo W resolveTextLayoutInfo with text overflow`(forge: Forge) {
        // Given
        val testData = setupTextLayoutMocks(forge)
        val fakeCapturedText = forge.aString()
        val fakeModifierColorValue = forge.aLong().toULong()
        val mockMultiParagraph = mock<MultiParagraph>()
        whenever(testData.textLayoutResult.didOverflowHeight) doReturn true
        whenever(testData.textLayoutResult.multiParagraph) doReturn mockMultiParagraph
        whenever(mockReflectionUtils.isTextStringSimpleElement(mockModifier)) doReturn true
        whenever(mockReflectionUtils.getColorProducerColor(mockModifier)) doReturn Color(
            fakeModifierColorValue
        )
        whenever(mockReflectionUtils.getMultiParagraphCapturedText(mockMultiParagraph)) doReturn fakeCapturedText

        // When
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode))

        // Then
        val expected = TextLayoutInfo(
            text = fakeCapturedText,
            color = fakeModifierColorValue,
            textAlign = testData.fakeTextAlign,
            fontSize = testData.fakeFontSize.toLong(),
            fontFamily = testData.fakeFontFamily
        )
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M return backgroundInfo W resolveBackgroundInfo`(
        forge: Forge,
        @LongForgery fakeColorValue: Long,
        @IntForgery fakeCornerSizeValue: Int
    ) {
        // Given
        val leftPos = forge.aSmallInt()
        val rightPos = forge.anInt(leftPos, 0x2000)
        val topPos = forge.aSmallInt()
        val bottomPos = forge.anInt(topPos, 0x2000)
        val fakeRect = Rect(
            left = leftPos.toFloat(),
            top = topPos.toFloat(),
            right = rightPos.toFloat(),
            bottom = bottomPos.toFloat()
        )
        val mockShape = mock<RoundedCornerShape>()
        val mockPaddingModifier = mock<Modifier>()
        val mockBackgroundModifier = mock<Modifier>()
        val mockShapeModifier = mock<Modifier>()
        val mockPaddingModifierInfo = mock<ModifierInfo>()
        val mockBackgroundModifierInfo = mock<ModifierInfo>()
        val mockShapeModifierInfo = mock<ModifierInfo>()
        val fakeDensity = Density(fakeDensity)
        val fakeBounds = rectToBounds(fakeRect, fakeDensity.density)
        val fakeCornerSize = CornerSize(fakeCornerSizeValue.dp)
        val topPadding: Float = forge.aSmallInt().toFloat()
        val startPadding: Float = forge.aSmallInt().toFloat()
        val endPadding: Float = forge.aSmallInt().toFloat()
        val bottomPadding: Float = forge.aSmallInt().toFloat()
        whenever(mockShape.topStart) doReturn fakeCornerSize
        whenever(mockShapeModifierInfo.modifier) doReturn mockShapeModifier
        whenever(mockBackgroundModifierInfo.modifier) doReturn mockBackgroundModifier
        whenever(mockPaddingModifierInfo.modifier) doReturn mockPaddingModifier
        whenever(mockReflectionUtils.getColor(mockBackgroundModifier)) doReturn fakeColorValue
        whenever(mockReflectionUtils.getClipShape(mockShapeModifier)) doReturn mockShape
        whenever(mockReflectionUtils.getTopPadding(mockPaddingModifier)) doReturn topPadding
        whenever(mockReflectionUtils.getStartPadding(mockPaddingModifier)) doReturn startPadding
        whenever(mockReflectionUtils.getBottomPadding(mockPaddingModifier)) doReturn bottomPadding
        whenever(mockReflectionUtils.getEndPadding(mockPaddingModifier)) doReturn endPadding
        whenever(mockReflectionUtils.isPaddingElement(mockPaddingModifier)) doReturn true
        whenever(mockReflectionUtils.isBackgroundElement(mockBackgroundModifier)) doReturn true
        whenever(mockReflectionUtils.isGraphicsLayerElement(mockShapeModifier)) doReturn true
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(
            mockShapeModifierInfo,
            mockPaddingModifierInfo,
            mockBackgroundModifierInfo
        )
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)
        val size = Size(
            fakeBounds.width.toFloat() * fakeDensity.density,
            fakeBounds.height.toFloat() * fakeDensity.density
        )
        val cornerRadius = fakeCornerSize.toPx(size, fakeDensity) / fakeDensity.density

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)
        val expected = BackgroundInfo(
            color = fakeColorValue,
            globalBounds = GlobalBounds(
                x = fakeBounds.x,
                y = fakeBounds.y,
                width = fakeBounds.width,
                height = fakeBounds.height

            ),
            cornerRadius = cornerRadius
        )

        // Then
        assertThat(result).containsExactly(expected)
    }

    @Test
    fun `M return local bitmap W resolveSemanticsPainter { local image }`() {
        // Given
        val mockVectorPainter = mock<VectorPainter>()
        val mockBitmap = mock<Bitmap>()
        val mockCopiedBitmap = mock<Bitmap>()
        val fakeContentScale = ContentScale.Crop
        val fakeAlignment = Alignment.TopStart
        whenever(mockReflectionUtils.getLocalImagePainter(mockSemanticsNode)) doReturn mockVectorPainter
        whenever(mockReflectionUtils.getBitmapInVectorPainter(mockVectorPainter)) doReturn mockBitmap
        whenever(mockReflectionUtils.getContentScale(mockSemanticsNode)) doReturn fakeContentScale
        whenever(mockReflectionUtils.getAlignment(mockSemanticsNode)) doReturn fakeAlignment
        whenever(mockBitmap.copy(any(), any())) doReturn mockCopiedBitmap

        // When
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(
            BitmapInfo(
                bitmap = mockCopiedBitmap,
                isContextualImage = false,
                contentScale = fakeContentScale,
                alignment = fakeAlignment
            )
        )
    }

    @Test
    fun `M return async bitmap W resolveSemanticsPainter { async image }`() {
        // Given
        val mockBitmapPainter = mock<BitmapPainter>()
        val mockBitmap = mock<Bitmap>()
        val mockCopiedBitmap = mock<Bitmap>()
        val fakeContentScale = ContentScale.FillWidth
        val fakeAlignment = Alignment.BottomEnd
        whenever(mockReflectionUtils.getAsyncImagePainter(mockSemanticsNode)) doReturn mockBitmapPainter
        whenever(mockReflectionUtils.getBitmapInBitmapPainter(mockBitmapPainter)) doReturn mockBitmap
        whenever(mockReflectionUtils.isAsyncImagePainter(mockBitmapPainter)) doReturn false
        whenever(mockReflectionUtils.getContentScale(mockSemanticsNode)) doReturn fakeContentScale
        whenever(mockReflectionUtils.getAlignment(mockSemanticsNode)) doReturn fakeAlignment
        whenever(mockBitmap.copy(any(), any())) doReturn mockCopiedBitmap

        // When
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(
            BitmapInfo(
                bitmap = mockCopiedBitmap,
                isContextualImage = true,
                contentScale = fakeContentScale,
                alignment = fakeAlignment
            )
        )
    }

    @Test
    fun `M return raw ALPHA_8 bitmap with contentScale W resolveSemanticsPainter { ALPHA_8 bitmap }`() {
        // Given
        val mockVectorPainter = mock<VectorPainter>()
        val mockBitmap = mock<Bitmap>()
        val fakeContentScale = ContentScale.Inside
        val fakeAlignment = Alignment.CenterStart
        whenever(mockReflectionUtils.getLocalImagePainter(mockSemanticsNode)) doReturn mockVectorPainter
        whenever(mockReflectionUtils.getBitmapInVectorPainter(mockVectorPainter)) doReturn mockBitmap
        whenever(mockReflectionUtils.getContentScale(mockSemanticsNode)) doReturn fakeContentScale
        whenever(mockReflectionUtils.getAlignment(mockSemanticsNode)) doReturn fakeAlignment
        whenever(mockBitmap.config) doReturn Bitmap.Config.ALPHA_8

        // When
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(
            BitmapInfo(
                bitmap = mockBitmap,
                isContextualImage = false,
                contentScale = fakeContentScale,
                alignment = fakeAlignment
            )
        )
    }

    @Test
    fun `M return copied bitmap W resolveSemanticsPainter { HARDWARE bitmap }`() {
        // Given
        val mockVectorPainter = mock<VectorPainter>()
        val mockBitmap = mock<Bitmap>()
        val mockCopiedBitmap = mock<Bitmap>()
        val fakeContentScale = ContentScale.Fit
        val fakeAlignment = Alignment.Center
        whenever(mockReflectionUtils.getLocalImagePainter(mockSemanticsNode)) doReturn mockVectorPainter
        whenever(mockReflectionUtils.getBitmapInVectorPainter(mockVectorPainter)) doReturn mockBitmap
        whenever(mockReflectionUtils.getContentScale(mockSemanticsNode)) doReturn fakeContentScale
        whenever(mockReflectionUtils.getAlignment(mockSemanticsNode)) doReturn fakeAlignment
        whenever(mockBitmap.config) doReturn Bitmap.Config.HARDWARE
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)) doReturn mockCopiedBitmap

        // When
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(
            BitmapInfo(
                bitmap = mockCopiedBitmap,
                isContextualImage = false,
                contentScale = fakeContentScale,
                alignment = fakeAlignment
            )
        )
    }

    @Test
    fun `M return null W resolveSemanticsPainter { bitmap copy fails }`() {
        // Given
        val mockVectorPainter = mock<VectorPainter>()
        val mockBitmap = mock<Bitmap>()
        whenever(mockReflectionUtils.getLocalImagePainter(mockSemanticsNode)) doReturn mockVectorPainter
        whenever(mockReflectionUtils.getBitmapInVectorPainter(mockVectorPainter)) doReturn mockBitmap
        whenever(mockBitmap.config) doReturn Bitmap.Config.ARGB_8888
        whenever(mockBitmap.copy(any(), any())) doReturn null

        // When
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest(name = "{index} (overflowValue: {0}, expectedMode: {1})")
    @MethodSource("truncationModeMappings")
    fun `M return correct truncation mode W resolveTextLayoutInfo`(
        overflowValue: Any?,
        expectedMode: MobileSegment.TruncationMode?,
        forge: Forge
    ) {
        // Given
        setupTextLayoutMocks(forge)
        if (overflowValue != null) {
            whenever(mockReflectionUtils.isTextStringSimpleElement(mockModifier)) doReturn true
            whenever(mockReflectionUtils.getTextStringSimpleElementOverflow(mockModifier)) doReturn overflowValue
        }

        // When
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode))

        // Then
        assertThat(result.textOverflow).isEqualTo(expectedMode)
    }

    companion object {
        /**
         * Constant representing an unknown/unsupported TextOverflow Int value.
         * Used in tests to verify behavior when encountering unknown overflow modes.
         */
        private const val UNKNOWN_TEXT_OVERFLOW_ORDINAL = 99

        /**
         * Mock class that simulates TextOverflow value class structure (has "value" field).
         * Used to test reflection-based extraction of Int value from value class instances.
         */
        private class MockTextOverflowValueClass(val value: Int)

        /**
         * Mock object without a "value" field to simulate reflection extraction failure.
         * Used to test error handling when reflection fails to extract the Int value.
         */
        private class MockOverflowWithoutValueField {
            override fun toString() = "MockOverflowWithoutValueField"
        }

        @JvmStatic
        fun truncationModeMappings(): Stream<Arguments> {
            return Stream.of(
                // Int values (unboxed value class)
                Arguments.of(SemanticsUtils.TEXT_OVERFLOW_CLIP, MobileSegment.TruncationMode.CLIP),
                Arguments.of(SemanticsUtils.TEXT_OVERFLOW_ELLIPSE, MobileSegment.TruncationMode.TAIL),
                Arguments.of(SemanticsUtils.TEXT_OVERFLOW_VISIBLE, null),
                Arguments.of(
                    SemanticsUtils.TEXT_OVERFLOW_ELLIPSIS_START,
                    MobileSegment.TruncationMode.HEAD
                ),
                Arguments.of(
                    SemanticsUtils.TEXT_OVERFLOW_ELLIPSIS_MIDDLE,
                    MobileSegment.TruncationMode.MIDDLE
                ),
                // Value class instances (boxed) - simulates TextOverflow value class
                Arguments.of(
                    MockTextOverflowValueClass(SemanticsUtils.TEXT_OVERFLOW_CLIP),
                    MobileSegment.TruncationMode.CLIP
                ),
                Arguments.of(
                    MockTextOverflowValueClass(SemanticsUtils.TEXT_OVERFLOW_ELLIPSE),
                    MobileSegment.TruncationMode.TAIL
                ),
                Arguments.of(
                    MockTextOverflowValueClass(SemanticsUtils.TEXT_OVERFLOW_VISIBLE),
                    null
                ),
                Arguments.of(
                    MockTextOverflowValueClass(SemanticsUtils.TEXT_OVERFLOW_ELLIPSIS_START),
                    MobileSegment.TruncationMode.HEAD
                ),
                Arguments.of(
                    MockTextOverflowValueClass(SemanticsUtils.TEXT_OVERFLOW_ELLIPSIS_MIDDLE),
                    MobileSegment.TruncationMode.MIDDLE
                ),
                // Edge cases
                Arguments.of(UNKNOWN_TEXT_OVERFLOW_ORDINAL, null), // Unknown/unsupported overflow mode
                Arguments.of("unexpected_type", null), // Unexpected overflow type (triggers logUnknownOverflowType)
                Arguments.of(
                    MockOverflowWithoutValueField(),
                    null
                ), // Reflection extraction failure (triggers logReflectionExtractionFailure)
                Arguments.of(null, null) // No overflow modifier
            )
        }
    }

    private fun rectToBounds(rect: Rect, density: Float): GlobalBounds {
        val width = ((rect.right - rect.left) / density).toLong()
        val height = ((rect.bottom - rect.top) / density).toLong()
        val x = (rect.left / density).toLong()
        val y = (rect.top / density).toLong()
        return GlobalBounds(x, y, width, height)
    }
}
