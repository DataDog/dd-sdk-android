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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.ValueElement
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
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.data.BitmapInfo
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils.Companion.COLOR_UNSPECIFIED
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class SemanticsUtilsTest {

    private lateinit var testedSemanticsUtils: SemanticsUtils

    @Mock
    private lateinit var mockReflectionUtils: ReflectionUtils

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

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
    fun `M return null W resolveBackgroundShape { no background modifier }`() {
        // Given
        whenever(mockLayoutInfo.getModifierInfo()) doReturn emptyList()

        // When
        val result = testedSemanticsUtils.resolveBackgroundShape(mockSemanticsNode)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return topmost shape W resolveBackgroundShape { stacked backgrounds }`() {
        // Given — mirrors the resolveBackgroundColor stacked-background test: the last modifier
        // (topmost visible layer) governs both colour and shape.
        val bottomShape = mock<Shape>()
        val topShape = mock<Shape>()
        val bottomBackground = backgroundModifierStub(color = 0xFF0000FFL, brush = null)
        val topBackground = backgroundModifierStub(color = 0x0000FFFFL, brush = null)
        whenever(mockReflectionUtils.getShape(bottomBackground.modifier)) doReturn bottomShape
        whenever(mockReflectionUtils.getShape(topBackground.modifier)) doReturn topShape
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(bottomBackground, topBackground)

        // When
        val result = testedSemanticsUtils.resolveBackgroundShape(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(topShape)
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
    fun `M add window offset to inner bounds W resolveInnerBounds { non-zero windowOffset }`(
        @Forgery fakeWindowOffset: ComposeWindowOffset
    ) {
        // Given — RUM-16362: the host view's screen offset must be baked into bounds at the point
        // they're resolved, so it stays attached to the same wireframe instance that async
        // resource resolution later mutates (e.g. ImageWireframe.resourceId).
        val placeable = mock<Placeable>()
        whenever(mockReflectionUtils.getPlaceable(mockSemanticsNode)) doReturn placeable
        whenever(mockSemanticsNode.positionInRoot) doReturn fakeOffset

        // When
        val result = testedSemanticsUtils.resolveInnerBounds(mockSemanticsNode, fakeWindowOffset)
        val expected = GlobalBounds(
            x = (fakeOffset.x / fakeDensity).toLong() + fakeWindowOffset.xDp,
            y = (fakeOffset.y / fakeDensity).toLong() + fakeWindowOffset.yDp,
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
    fun `M return color W resolveBackgroundColor`(
        @LongForgery(min = 17L) fakeColorValue: Long
    ) {
        // Given — min = 17L avoids Color.Unspecified (16L) which would trigger the brush fallback.
        // alpha = null → applyAlphaToColor is a no-op; raw color value must come back unchanged.
        whenever(mockReflectionUtils.isBackgroundElement(mockModifier)) doReturn true
        whenever(mockReflectionUtils.getColor(mockModifier)) doReturn fakeColorValue
        whenever(mockReflectionUtils.getAlpha(mockModifier)) doReturn null

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(fakeColorValue)
    }

    @Test
    fun `M return topmost background color W resolveBackgroundColor { stacked backgrounds }`(
        @LongForgery(min = 17L) bottomColorValue: Long,
        @LongForgery(min = 17L) topColorValue: Long
    ) {
        // Given — two stacked backgrounds; the last modifier is the topmost visible layer and its
        // color must drive parentContentColor, not the first (bottom) layer.
        val bottomBackground = backgroundModifierStub(color = bottomColorValue, brush = null)
        val topBackground = backgroundModifierStub(color = topColorValue, brush = null)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(bottomBackground, topBackground)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(topColorValue)
    }

    @Test
    fun `M return null W resolveBackgroundColor { no background modifier }`() {
        // Given
        whenever(mockLayoutInfo.getModifierInfo()) doReturn emptyList()

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M fall back to brush first stop W resolveBackgroundColor { gradient brush }`() {
        // Given
        val firstStop = Color.Red
        val secondStop = Color.Blue
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop, secondStop)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(firstStop.value.toLong())
    }

    @Test
    fun `M return null W resolveBackgroundColor { unspecified color and no brush }`() {
        // Given
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = null
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W resolveBackgroundColor { unknown brush type }`() {
        // Given
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn null
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M prefer color over brush W resolveBackgroundColor { both color and brush set }`(
        @LongForgery(min = 17L) explicitColorValue: Long
    ) {
        // Given — when BackgroundElement.color is explicitly set (not Unspecified) we must use
        // it, even if some future Compose version starts setting brush in parallel.
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = explicitColorValue,
            brush = mockBrush
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(explicitColorValue)
        verify(mockReflectionUtils, never()).getBrushColors(any())
    }

    @Test
    fun `M fall back to brush W resolveBackgroundColor { null color and brush set }`() {
        // Given — getColor returns null (field inaccessible via reflection). This is distinct
        // from Color.Unspecified (16L): both trigger the brush fallback, but the null path
        // exercises a different branch in resolveBackgroundElementColor.
        val firstStop = Color.Cyan
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = null,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(firstStop.value.toLong())
    }

    @Test
    fun `M return null W resolveBackgroundColor { null color and no brush }`() {
        // Given
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = null,
            brush = null
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return TextLayoutInfo W resolveTextLayoutInfo modifier color is null`(forge: Forge) {
        // Given
        val testData = setupTextLayoutMocks(forge)

        // When
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode, mockInternalLogger))

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
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode, mockInternalLogger))

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
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode, mockInternalLogger))

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
    fun `M use default font size W resolveTextLayoutInfo {fontSize is Unspecified}`(forge: Forge) {
        // Given
        val testData = setupTextLayoutMocks(forge)
        whenever(testData.textLayoutResult.layoutInput.style.fontSize) doReturn TextUnit.Unspecified

        // When
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode, mockInternalLogger))

        // Then
        assertThat(result.fontSize).isEqualTo(SemanticsUtils.DEFAULT_FONT_SIZE_SP)
    }

    @Test
    fun `M use specified font size W resolveTextLayoutInfo {fontSize is valid Sp}`(forge: Forge) {
        // Given
        val fakeFontSizeSp = forge.aFloat(min = 1f, max = 500f)
        val testData = setupTextLayoutMocks(forge)
        whenever(testData.textLayoutResult.layoutInput.style.fontSize) doReturn
            TextUnit(fakeFontSizeSp, TextUnitType.Sp)

        // When
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode, mockInternalLogger))

        // Then
        assertThat(result.fontSize).isEqualTo(fakeFontSizeSp.toLong())
    }

    @Test
    fun `M return backgroundInfo W resolveBackgroundInfo`(
        forge: Forge,
        @LongForgery(min = 17L) fakeColorValue: Long,
        @IntForgery fakeCornerSizeValue: Int
    ) {
        // Given
        val leftPos = forge.aSmallInt()
        val rightPos = forge.anInt(leftPos + MIN_VISIBLE_PX, 0x2000)
        val topPos = forge.aSmallInt()
        val bottomPos = forge.anInt(topPos + MIN_VISIBLE_PX, 0x2000)
        val fakeRect = Rect(
            left = leftPos.toFloat(),
            top = topPos.toFloat(),
            right = rightPos.toFloat(),
            bottom = bottomPos.toFloat()
        )
        val mockShape = mock<RoundedCornerShape>()
        val mockPaddingModifier = mock<Modifier>()
        val mockBackgroundModifier = mock<Modifier>()
        val mockShapeModifier = mockGraphicsLayerModifier(mockShape)
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
        // alpha = null → applyAlphaToColor is a no-op; color comes back unchanged
        whenever(mockReflectionUtils.getAlpha(mockBackgroundModifier)) doReturn null
        whenever(mockReflectionUtils.getTopPadding(mockPaddingModifier)) doReturn topPadding
        whenever(mockReflectionUtils.getStartPadding(mockPaddingModifier)) doReturn startPadding
        whenever(mockReflectionUtils.getBottomPadding(mockPaddingModifier)) doReturn bottomPadding
        whenever(mockReflectionUtils.getEndPadding(mockPaddingModifier)) doReturn endPadding
        whenever(mockReflectionUtils.isPaddingElement(mockPaddingModifier)) doReturn true
        whenever(mockReflectionUtils.isBackgroundElement(mockBackgroundModifier)) doReturn true
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
    fun `M add window offset to backgroundInfo bounds W resolveBackgroundInfo { non-zero windowOffset }`(
        forge: Forge,
        @LongForgery(min = 17L) fakeColorValue: Long,
        @Forgery fakeWindowOffset: ComposeWindowOffset
    ) {
        // Given — RUM-16362: BackgroundResolver's bounds (used for background/padding shape
        // wireframes) go through a separate code path from resolveInnerBounds, and must get the
        // same window offset applied.
        val (fakeRect, fakeBounds) = forgeBackgroundBounds(forge)
        val backgroundModifierInfo = backgroundModifierStub(color = fakeColorValue, brush = null)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(backgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode, fakeWindowOffset)

        // Then
        val expected = BackgroundInfo(
            color = fakeColorValue,
            globalBounds = GlobalBounds(
                x = fakeBounds.x + fakeWindowOffset.xDp,
                y = fakeBounds.y + fakeWindowOffset.yDp,
                width = fakeBounds.width,
                height = fakeBounds.height
            )
        )
        assertThat(result).containsExactly(expected)
    }

    @Test
    fun `M return two BackgroundInfo items W resolveBackgroundInfo { stacked backgrounds }`(
        forge: Forge,
        @LongForgery(min = 17L) bottomColorValue: Long,
        @LongForgery(min = 17L) topColorValue: Long
    ) {
        // Given — two stacked backgrounds; resolveBackgroundInfo must include both in the list,
        // with each carrying the global bounds at the point the modifier was encountered.
        val (fakeRect, fakeBounds) = forgeBackgroundBounds(forge)
        val bottomBackground = backgroundModifierStub(color = bottomColorValue, brush = null)
        val topBackground = backgroundModifierStub(color = topColorValue, brush = null)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(bottomBackground, topBackground)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — both backgrounds are present; colours match their respective modifiers.
        assertThat(result).hasSize(2)
        assertThat(result[0].color).isEqualTo(bottomColorValue)
        assertThat(result[1].color).isEqualTo(topColorValue)
        assertThat(result[0].globalBounds).isEqualTo(
            GlobalBounds(x = fakeBounds.x, y = fakeBounds.y, width = fakeBounds.width, height = fakeBounds.height)
        )
        assertThat(result[1].globalBounds).isEqualTo(
            GlobalBounds(x = fakeBounds.x, y = fakeBounds.y, width = fakeBounds.width, height = fakeBounds.height)
        )
    }

    @Test
    fun `M produce different bounds per item W resolveBackgroundInfo { padding between backgrounds }`(
        forge: Forge,
        @LongForgery(min = 17L) outerColorValue: Long,
        @LongForgery(min = 17L) innerColorValue: Long
    ) {
        // Given — [background(outer), padding, background(inner)].
        // resolveOuterBounds pre-expands by the padding, so when background(outer) is encountered
        // the bounds are the expanded outer bounds. After the padding modifier shrinks them back,
        // background(inner) gets the original inner (fakeBounds) bounds.
        val (fakeRect, fakeBounds) = forgeBackgroundBounds(forge)
        val padding = forge.anInt(min = 1, max = 20).toFloat()
        val outerBackground = backgroundModifierStub(color = outerColorValue, brush = null)
        val innerBackground = backgroundModifierStub(color = innerColorValue, brush = null)
        val mockPaddingModifier = mock<Modifier>()
        val mockPaddingModifierInfo = mock<ModifierInfo>()
        whenever(mockPaddingModifierInfo.modifier) doReturn mockPaddingModifier
        whenever(mockReflectionUtils.isPaddingElement(mockPaddingModifier)) doReturn true
        whenever(mockReflectionUtils.getTopPadding(mockPaddingModifier)) doReturn padding
        whenever(mockReflectionUtils.getStartPadding(mockPaddingModifier)) doReturn padding
        whenever(mockReflectionUtils.getBottomPadding(mockPaddingModifier)) doReturn padding
        whenever(mockReflectionUtils.getEndPadding(mockPaddingModifier)) doReturn padding
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(
            outerBackground,
            mockPaddingModifierInfo,
            innerBackground
        )
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — two items, each with distinct bounds reflecting their position in the modifier chain.
        assertThat(result).hasSize(2)
        val paddingLong = padding.toLong()
        assertThat(result[0].color).isEqualTo(outerColorValue)
        assertThat(result[0].globalBounds).isEqualTo(
            GlobalBounds(
                x = fakeBounds.x - paddingLong,
                y = fakeBounds.y - paddingLong,
                width = fakeBounds.width + paddingLong * 2,
                height = fakeBounds.height + paddingLong * 2
            )
        )
        assertThat(result[1].color).isEqualTo(innerColorValue)
        assertThat(result[1].globalBounds).isEqualTo(
            GlobalBounds(x = fakeBounds.x, y = fakeBounds.y, width = fakeBounds.width, height = fakeBounds.height)
        )
    }

    @Test
    fun `M reset cornerRadius between stacked backgrounds W resolveBackgroundInfo { shape then two backgrounds }`(
        forge: Forge,
        @LongForgery(min = 17L) firstColorValue: Long,
        @LongForgery(min = 17L) secondColorValue: Long,
        @IntForgery fakeCornerSizeValue: Int
    ) {
        // Given — [shape, background1, background2].
        // The clip shape sets cornerRadius on currentBackgroundInfo; background1 consumes that and
        // resets to a fresh BackgroundInfo. background2 should therefore carry cornerRadius = 0f.
        val (fakeRect, fakeBounds) = forgeBackgroundBounds(forge)
        val density = Density(fakeDensity)
        val mockShape = mock<RoundedCornerShape>()
        val mockShapeModifier = mockGraphicsLayerModifier(mockShape)
        val mockShapeModifierInfo = mock<ModifierInfo>()
        val fakeCornerSize = CornerSize(fakeCornerSizeValue.dp)
        whenever(mockShape.topStart) doReturn fakeCornerSize
        whenever(mockShapeModifierInfo.modifier) doReturn mockShapeModifier
        val firstBackground = backgroundModifierStub(color = firstColorValue, brush = null)
        val secondBackground = backgroundModifierStub(color = secondColorValue, brush = null)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(
            mockShapeModifierInfo,
            firstBackground,
            secondBackground
        )
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)
        val size = Size(fakeBounds.width.toFloat() * density.density, fakeBounds.height.toFloat() * density.density)
        val expectedCornerRadius = fakeCornerSize.toPx(size, density) / density.density

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — first background picks up the corner radius; second gets 0f after the reset.
        assertThat(result).hasSize(2)
        assertThat(result[0].cornerRadius).isEqualTo(expectedCornerRadius)
        assertThat(result[1].cornerRadius).isEqualTo(0f)
    }

    @Test
    fun `M fall back to brush first stop W resolveBackgroundInfo { gradient brush }`(forge: Forge) {
        // Given — Modifier.background(brush = ...) leaves BackgroundElement.color as
        // Color.Unspecified (raw value 16L); the actual fill lives on `brush`. We expect the
        // resolution to fall through to the brush and return the first stop's value.
        val (fakeRect, fakeBounds) = forgeBackgroundBounds(forge)
        val firstStop = Color.Red
        val secondStop = Color.Blue
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop, secondStop)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then
        assertThat(result).containsExactly(
            BackgroundInfo(
                color = firstStop.value.toLong(),
                globalBounds = GlobalBounds(
                    x = fakeBounds.x,
                    y = fakeBounds.y,
                    width = fakeBounds.width,
                    height = fakeBounds.height
                ),
                cornerRadius = 0f
            )
        )
    }

    @Test
    fun `M return solid color W resolveBackgroundInfo { SolidColor brush }`(forge: Forge) {
        // Given — Modifier.background(brush = SolidColor(c)) is unusual but valid. SolidColor is
        // accessed via its public API in ReflectionUtils (not reflection), so it is always available.
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val solidColor = Color.Green
        val solidBrush = SolidColor(solidColor)
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = solidBrush
        )
        whenever(mockReflectionUtils.getBrushColors(solidBrush)) doReturn listOf(solidColor)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then
        assertThat(result.single().color).isEqualTo(solidColor.value.toLong())
    }

    @Test
    fun `M emit null color W resolveBackgroundInfo { unknown brush type }`(forge: Forge) {
        // Given — a custom Brush implementation that ReflectionUtils can't introspect. We
        // preserve the pre-fix behaviour (a wireframe is still emitted, but with a null
        // backgroundColor — invisible) rather than skipping the wireframe entirely, so the
        // shape's bounds still influence layout in the replay.
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn null
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.single().color).isNull()
    }

    @Test
    fun `M emit null color W resolveBackgroundInfo { brush returns empty color list }`(forge: Forge) {
        // Given — getBrushColors returns an empty list (reflection succeeded but the colors field
        // was unexpectedly empty). This is treated the same as an unknown brush type: a wireframe
        // is emitted with null color to preserve layout space.
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn emptyList()
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.single().color).isNull()
    }

    @Test
    fun `M return null W resolveBackgroundColor { brush returns empty color list }`() {
        // Given — mirrors the resolveBackgroundInfo empty-list case but through resolveBackgroundColor.
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn emptyList()
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M prefer color over brush W resolveBackgroundInfo { both color and brush set }`(
        forge: Forge,
        @LongForgery(min = 17L) explicitColorValue: Long
    ) {
        // Given — when BackgroundElement.color is explicitly set (not Unspecified) we must use
        // it, even if some future Compose version starts setting brush in parallel.
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = explicitColorValue,
            brush = mockBrush
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then
        assertThat(result.single().color).isEqualTo(explicitColorValue)
        // Brush must not be consulted when the color is already set.
        verify(mockReflectionUtils, never()).getBrushColors(any())
    }

    @Test
    fun `M fall back to brush W resolveBackgroundInfo { null color and brush set }`(forge: Forge) {
        // Given — getColor returns null (field inaccessible via reflection). Both null and
        // Color.Unspecified trigger the brush fallback; this test exercises the null path.
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val firstStop = Color.Cyan
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = null,
            brush = mockBrush
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then
        assertThat(result.single().color).isEqualTo(firstStop.value.toLong())
    }

    @Test
    fun `M emit null color W resolveBackgroundInfo { null color and no brush }`(forge: Forge) {
        // Given
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = null,
            brush = null
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result.single().color).isNull()
    }

    // region alpha tests

    @Test
    fun `M apply modifier alpha to solid color W resolveBackgroundColor { alpha less than 1 }`(
        // Float.MIN_VALUE as lower bound avoids alpha=0f (fully-transparent degenerate case,
        // which is covered by its own dedicated test below).
        @FloatForgery(min = Float.MIN_VALUE, max = 1f) fakeAlpha: Float
    ) {
        // Given — Modifier.background(brush = SolidColor(Color.Red), alpha = fakeAlpha) stores the
        // color in the brush field (not the color field) and the alpha in BackgroundElement.alpha.
        // We simulate this here by supplying an opaque red via the color path with a non-unity alpha,
        // which exercises applyAlphaToColor independently of which branch produced the color.
        val opaqueRed = Color.Red
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = opaqueRed.value.toLong(),
            brush = null,
            alpha = fakeAlpha
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then — the returned color must carry the blended alpha channel.
        // Compose stores color components as 16-bit half-floats, so we compute expectedAlpha
        // via the same Color.copy() round-trip the implementation uses, rather than using
        // 32-bit float arithmetic which would disagree after half-float rounding.
        val resultColor = result?.let { Color(it.toULong()) }
        val expectedAlpha = opaqueRed.copy(alpha = (opaqueRed.alpha * fakeAlpha).coerceIn(0f, 1f)).alpha
        assertThat(resultColor?.alpha).isCloseTo(expectedAlpha, org.assertj.core.data.Offset.offset(0.01f))
        // RGB channels must be preserved (half-float precision, tolerance 0.01f)
        assertThat(resultColor?.red).isCloseTo(opaqueRed.red, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(resultColor?.green).isCloseTo(opaqueRed.green, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(resultColor?.blue).isCloseTo(opaqueRed.blue, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `M not change color W resolveBackgroundColor { alpha is 1f }`(
        @LongForgery(min = 17L) fakeColorValue: Long
    ) {
        // Given
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = fakeColorValue,
            brush = null,
            alpha = 1f
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then — alpha == 1f is a no-op; raw value must come back unchanged
        assertThat(result).isEqualTo(fakeColorValue)
    }

    @Test
    fun `M not change color W resolveBackgroundColor { alpha is null }`(
        @LongForgery(min = 17L) fakeColorValue: Long
    ) {
        // Given — alpha field inaccessible via reflection → null
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = fakeColorValue,
            brush = null,
            alpha = null
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then — null alpha is treated as 1f (no-op)
        assertThat(result).isEqualTo(fakeColorValue)
    }

    @Test
    fun `M apply modifier alpha to brush first stop W resolveBackgroundColor { brush with alpha less than 1 }`(
        // Float.MIN_VALUE as lower bound excludes the degenerate alpha=0f case (own test below).
        @FloatForgery(min = Float.MIN_VALUE, max = 1f) fakeAlpha: Float
    ) {
        // Given — opaque green as first brush stop
        val firstStop = Color.Green
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush,
            alpha = fakeAlpha
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then — use the Color.copy() round-trip to derive the expected alpha,
        // matching the half-float encoding used by Compose internally.
        val resultColor = result?.let { Color(it.toULong()) }
        val expectedAlpha = firstStop.copy(alpha = (firstStop.alpha * fakeAlpha).coerceIn(0f, 1f)).alpha
        assertThat(resultColor?.alpha).isCloseTo(expectedAlpha, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(resultColor?.green).isCloseTo(firstStop.green, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `M apply modifier alpha to solid color W resolveBackgroundInfo { alpha less than 1 }`(
        forge: Forge,
        @FloatForgery(min = Float.MIN_VALUE, max = 1f) fakeAlpha: Float
    ) {
        // Given — opaque blue solid color with a modifier-level alpha
        val (fakeRect, fakeBounds) = forgeBackgroundBounds(forge)
        val opaqueBlue = Color.Blue
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = opaqueBlue.value.toLong(),
            brush = null,
            alpha = fakeAlpha
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — BackgroundInfo.color must carry the blended alpha; bounds must be preserved.
        // Use Color.copy() to derive expectedAlpha through the same half-float round-trip.
        assertThat(result).hasSize(1)
        val resultColor = result.single().color?.let { Color(it.toULong()) }
        val expectedAlpha = opaqueBlue.copy(alpha = (opaqueBlue.alpha * fakeAlpha).coerceIn(0f, 1f)).alpha
        assertThat(resultColor?.alpha).isCloseTo(expectedAlpha, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(resultColor?.blue).isCloseTo(opaqueBlue.blue, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(result.single().globalBounds).isEqualTo(
            GlobalBounds(x = fakeBounds.x, y = fakeBounds.y, width = fakeBounds.width, height = fakeBounds.height)
        )
    }

    @Test
    fun `M apply modifier alpha to brush first stop W resolveBackgroundInfo { brush with alpha less than 1 }`(
        forge: Forge,
        @FloatForgery(min = Float.MIN_VALUE, max = 1f) fakeAlpha: Float
    ) {
        // Given — opaque red as first stop of a gradient brush, plus a modifier-level alpha
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val firstStop = Color.Red
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush,
            alpha = fakeAlpha
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop, Color.Blue)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — use Color.copy() round-trip to derive expectedAlpha after half-float encoding.
        assertThat(result).hasSize(1)
        val resultColor = result.single().color?.let { Color(it.toULong()) }
        val expectedAlpha = firstStop.copy(alpha = (firstStop.alpha * fakeAlpha).coerceIn(0f, 1f)).alpha
        assertThat(resultColor?.alpha).isCloseTo(expectedAlpha, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(resultColor?.red).isCloseTo(firstStop.red, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `M return COLOR_UNSPECIFIED W resolveBackgroundColor { alpha is 0f }`(
        @LongForgery(min = 17L) fakeColorValue: Long
    ) {
        // Given — a solid-color background with alpha = 0f (fully invisible). This exercises
        // the color path of resolveBackgroundElementColor (brush = null, color != Unspecified).
        // applyAlphaToColor normalises alpha=0f to COLOR_UNSPECIFIED so that AbstractSemanticsNodeMapper
        // .convertColor() returns null (no fill), consistent with the existing "no color" contract.
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = fakeColorValue,
            brush = null,
            alpha = 0f
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then — COLOR_UNSPECIFIED signals "no fill" to downstream convertColor.
        assertThat(result).isEqualTo(COLOR_UNSPECIFIED)
    }

    @Test
    fun `M emit null color W resolveBackgroundInfo { alpha is 0f }`(forge: Forge) {
        // Given — alpha=0f makes the element fully invisible. applyAlphaToColor returns
        // COLOR_UNSPECIFIED, so BackgroundInfo.color should be COLOR_UNSPECIFIED (convertColor
        // will produce null backgroundColor in the wireframe).
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val opaqueRed = Color.Red
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = opaqueRed.value.toLong(),
            brush = null,
            alpha = 0f
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — wireframe is emitted (size=1) but color is COLOR_UNSPECIFIED ("no fill").
        assertThat(result).hasSize(1)
        assertThat(result.single().color).isEqualTo(COLOR_UNSPECIFIED)
    }

    @Test
    fun `M return COLOR_UNSPECIFIED W resolveBackgroundColor { brush with alpha is 0f }`() {
        // Given — the realistic Modifier.background(brush = ..., alpha = 0f) scenario.
        // The color field is Unspecified (brush overload), so we go through the brush path.
        // applyAlphaToColor still normalises alpha=0f to COLOR_UNSPECIFIED.
        val firstStop = Color.Red
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush,
            alpha = 0f
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then — fully invisible brush background yields COLOR_UNSPECIFIED (→ null fill in wireframe).
        assertThat(result).isEqualTo(COLOR_UNSPECIFIED)
    }

    @Test
    fun `M emit COLOR_UNSPECIFIED color W resolveBackgroundInfo { brush with alpha is 0f }`(forge: Forge) {
        // Given — Modifier.background(brush = linearGradient(...), alpha = 0f).
        // Even though the brush has a valid first stop, the modifier-level alpha=0f
        // makes the element invisible → BackgroundInfo.color should be COLOR_UNSPECIFIED.
        val (fakeRect, _) = forgeBackgroundBounds(forge)
        val firstStop = Color.Red
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush,
            alpha = 0f
        )
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop)
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — wireframe is still emitted (size=1) but color is COLOR_UNSPECIFIED ("no fill").
        assertThat(result).hasSize(1)
        assertThat(result.single().color).isEqualTo(COLOR_UNSPECIFIED)
    }

    @Test
    fun `M apply compounded alpha W resolveBackgroundColor { source color and modifier both semi-transparent }`(
        @FloatForgery(min = Float.MIN_VALUE, max = 1f) sourceAlpha: Float,
        @FloatForgery(min = Float.MIN_VALUE, max = 1f) modifierAlpha: Float
    ) {
        // Given — a source color that is itself semi-transparent (alpha < 1f), combined with a
        // modifier-level alpha. The result should multiply both alphas. For example, a 50%-alpha
        // source combined with a 50%-alpha modifier yields ~25% visible.
        val semiColor = Color.Red.copy(alpha = sourceAlpha)
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = semiColor.value.toLong(),
            brush = null,
            alpha = modifierAlpha
        )
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockBackgroundModifierInfo)

        // When
        val result = testedSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)

        // Then — Use the Color.copy() round-trip for expectedAlpha to match half-float precision.
        val resultColor = result?.let { Color(it.toULong()) }
        val blended = (semiColor.alpha * modifierAlpha).coerceIn(0f, 1f)
        val expectedAlpha = semiColor.copy(alpha = blended).alpha
        assertThat(resultColor?.alpha).isCloseTo(expectedAlpha, org.assertj.core.data.Offset.offset(0.01f))
        // RGB channels must survive the multiplication unchanged.
        assertThat(resultColor?.red).isCloseTo(semiColor.red, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `M return backgroundInfo W resolveBackgroundInfo { brush with padding and shape }`(
        forge: Forge,
        @IntForgery fakeCornerSizeValue: Int
    ) {
        // Given — exercises the full modifier chain (clip-shape → padding → brush background)
        // through resolveBackgroundInfo, mirroring the colour-path test but for the brush path.
        val (fakeRect, fakeBounds) = forgeBackgroundBounds(forge)
        val density = Density(fakeDensity)
        val mockShape = mock<RoundedCornerShape>()
        val mockPaddingModifier = mock<Modifier>()
        val mockShapeModifier = mockGraphicsLayerModifier(mockShape)
        val mockPaddingModifierInfo = mock<ModifierInfo>()
        val mockShapeModifierInfo = mock<ModifierInfo>()
        val fakeCornerSize = CornerSize(fakeCornerSizeValue.dp)
        val topPadding: Float = forge.aSmallInt().toFloat()
        val startPadding: Float = forge.aSmallInt().toFloat()
        val endPadding: Float = forge.aSmallInt().toFloat()
        val bottomPadding: Float = forge.aSmallInt().toFloat()
        val firstStop = Color.Magenta
        val mockBrush = mock<Brush>()
        val mockBackgroundModifierInfo = backgroundModifierStub(
            color = COLOR_UNSPECIFIED,
            brush = mockBrush
        )
        whenever(mockShape.topStart) doReturn fakeCornerSize
        whenever(mockShapeModifierInfo.modifier) doReturn mockShapeModifier
        whenever(mockPaddingModifierInfo.modifier) doReturn mockPaddingModifier
        whenever(mockReflectionUtils.getBrushColors(mockBrush)) doReturn listOf(firstStop)
        whenever(mockReflectionUtils.getTopPadding(mockPaddingModifier)) doReturn topPadding
        whenever(mockReflectionUtils.getStartPadding(mockPaddingModifier)) doReturn startPadding
        whenever(mockReflectionUtils.getBottomPadding(mockPaddingModifier)) doReturn bottomPadding
        whenever(mockReflectionUtils.getEndPadding(mockPaddingModifier)) doReturn endPadding
        whenever(mockReflectionUtils.isPaddingElement(mockPaddingModifier)) doReturn true
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(
            mockShapeModifierInfo,
            mockPaddingModifierInfo,
            mockBackgroundModifierInfo
        )
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeRect
        whenever(mockSemanticsNode.positionInRoot) doReturn Offset(fakeRect.left, fakeRect.top)
        val size = Size(
            fakeBounds.width.toFloat() * density.density,
            fakeBounds.height.toFloat() * density.density
        )
        val expectedCornerRadius = fakeCornerSize.toPx(size, density) / density.density

        // When
        val result = testedSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)

        // Then — first brush stop is used as color; padding shrinks bounds; corner radius is set.
        assertThat(result).hasSize(1)
        val info = result.single()
        assertThat(info.color).isEqualTo(firstStop.value.toLong())
        assertThat(info.cornerRadius).isEqualTo(expectedCornerRadius)
        assertThat(info.globalBounds).isEqualTo(
            GlobalBounds(
                x = fakeBounds.x,
                y = fakeBounds.y,
                width = fakeBounds.width,
                height = fakeBounds.height
            )
        )
    }

    // endregion

    private fun forgeBackgroundBounds(forge: Forge): Pair<Rect, GlobalBounds> {
        val leftPos = forge.aSmallInt()
        val rightPos = forge.anInt(leftPos + MIN_VISIBLE_PX, 0x2000)
        val topPos = forge.aSmallInt()
        val bottomPos = forge.anInt(topPos + MIN_VISIBLE_PX, 0x2000)
        val rect = Rect(
            left = leftPos.toFloat(),
            top = topPos.toFloat(),
            right = rightPos.toFloat(),
            bottom = bottomPos.toFloat()
        )
        return rect to rectToBounds(rect, fakeDensity)
    }

    private fun backgroundModifierStub(
        color: Long?,
        brush: Brush?,
        alpha: Float? = null
    ): ModifierInfo {
        val modifier = mock<Modifier>()
        val info = mock<ModifierInfo>()
        whenever(info.modifier) doReturn modifier
        whenever(mockReflectionUtils.isBackgroundElement(modifier)) doReturn true
        whenever(mockReflectionUtils.getColor(modifier)) doReturn color
        whenever(mockReflectionUtils.getBrush(modifier)) doReturn brush
        whenever(mockReflectionUtils.getAlpha(modifier)) doReturn alpha
        return info
    }

    /**
     * A `graphicsLayer` modifier exposing [shape] through [InspectableValue.inspectableElements] -
     * the public mechanism [BackgroundResolver] reads `shape` through, not reflection.
     */
    private fun mockGraphicsLayerModifier(shape: Shape): Modifier {
        val modifier = mock<Modifier>(extraInterfaces = arrayOf(InspectableValue::class))
        val inspectable = modifier as InspectableValue
        whenever(inspectable.nameFallback) doReturn "graphicsLayer"
        whenever(inspectable.inspectableElements) doReturn sequenceOf(ValueElement("shape", shape))
        return modifier
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
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode, mockInternalLogger)

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
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode, mockInternalLogger)

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
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode, mockInternalLogger)

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
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode, mockInternalLogger)

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
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode, mockInternalLogger)

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
        val result = requireNotNull(testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode, mockInternalLogger))

        // Then
        assertThat(result.textOverflow).isEqualTo(expectedMode)
    }

    companion object {
        /**
         * Minimum rect span (in pixels) that guarantees a non-zero GlobalBounds dimension after
         * dividing by the maximum possible fakeDensity (10f). Without this guard,
         * `(rightPos - leftPos) / density` can truncate to 0L, causing `isGlobalBoundsValid` to
         * return false and `resolveBackgroundInfo` to return an empty list, which causes
         * `result.single()` to throw NoSuchElementException.
         * 11px / 10f = 1.1 → toLong() = 1 > 0, so 11 is the minimum safe span.
         */
        private const val MIN_VISIBLE_PX = 11

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
