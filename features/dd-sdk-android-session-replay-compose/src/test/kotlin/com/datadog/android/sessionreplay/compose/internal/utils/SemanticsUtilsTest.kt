/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composition
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.VectorPainter
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

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
    private lateinit var mockModifier: Modifier

    private var fakeOffset: Offset = Offset(0f, 0f)

    @FloatForgery(min = 1f, max = 10f)
    private var fakeDensity: Float = 0f

    @Mock
    private lateinit var mockConfig: SemanticsConfiguration

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedSemanticsUtils = SemanticsUtils(reflectionUtils = mockReflectionUtils)
        whenever(mockSemanticsNode.layoutInfo) doReturn mockLayoutInfo
        whenever(mockLayoutInfo.getModifierInfo()) doReturn listOf(mockModifierInfo)
        whenever(mockModifierInfo.modifier) doReturn mockModifier
        whenever(mockLayoutInfo.density) doReturn Density(fakeDensity)
        whenever(mockSemanticsNode.config) doReturn mockConfig
        fakeOffset = Offset(x = forge.aFloat(), y = forge.aFloat())
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
        val fakeText = AnnotatedString(forge.aString())
        val fakeColorValue = forge.aLong().toULong()
        val fakeFontSize = forge.aFloat()
        val fakeFontFamily = forge.anElementFrom(
            listOf(
                FontFamily.Serif,
                FontFamily.SansSerif,
                FontFamily.Cursive,
                FontFamily.Monospace,
                FontFamily.Default
            )
        )
        val fakeTextAlign = forge.anElementFrom(TextAlign.values())
        val mockResult = mock<AccessibilityAction<(MutableList<TextLayoutResult>) -> Boolean>>()
        val mockAction = mock<(MutableList<TextLayoutResult>) -> Boolean>()
        val textLayoutResult: TextLayoutResult = mock()
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

        // When
        val result = testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode)

        // Then
        val expected = TextLayoutInfo(
            text = resolveAnnotatedString(fakeText),
            color = fakeColorValue,
            textAlign = fakeTextAlign,
            fontSize = fakeFontSize.toLong(),
            fontFamily = fakeFontFamily
        )
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M return TextLayoutInfo W resolveTextLayoutInfo modifier color is not null`(forge: Forge) {
        // Given
        val fakeText = AnnotatedString(forge.aString())
        val fakeColorValue = forge.aLong().toULong()
        val fakeModifierColorValue = forge.aLong().toULong()
        val fakeFontSize = forge.aFloat()
        val fakeFontFamily = forge.anElementFrom(
            listOf(
                FontFamily.Serif,
                FontFamily.SansSerif,
                FontFamily.Cursive,
                FontFamily.Monospace,
                FontFamily.Default
            )
        )
        val fakeTextAlign = forge.anElementFrom(TextAlign.values())
        val mockResult = mock<AccessibilityAction<(MutableList<TextLayoutResult>) -> Boolean>>()
        val mockAction = mock<(MutableList<TextLayoutResult>) -> Boolean>()
        val textLayoutResult: TextLayoutResult = mock()
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
        whenever(mockReflectionUtils.isTextStringSimpleElement(mockModifier)) doReturn true
        whenever(mockReflectionUtils.getColorProducerColor(mockModifier)) doReturn Color(
            fakeModifierColorValue
        )

        // When
        val result = testedSemanticsUtils.resolveTextLayoutInfo(mockSemanticsNode)

        // Then
        val expected = TextLayoutInfo(
            text = resolveAnnotatedString(fakeText),
            color = fakeModifierColorValue,
            textAlign = fakeTextAlign,
            fontSize = fakeFontSize.toLong(),
            fontFamily = fakeFontFamily
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
        whenever(mockReflectionUtils.getLocalImagePainter(mockSemanticsNode)) doReturn mockVectorPainter
        whenever(mockReflectionUtils.getBitmapInVectorPainter(mockVectorPainter)) doReturn mockBitmap
        whenever(mockBitmap.copy(any(), any())) doReturn mockBitmap

        // When
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(BitmapInfo(mockBitmap, false))
    }

    @Test
    fun `M return async bitmap W resolveSemanticsPainter { async image }`() {
        // Given
        val mockBitmapPainter = mock<BitmapPainter>()
        val mockBitmap = mock<Bitmap>()
        whenever(mockReflectionUtils.getAsyncImagePainter(mockSemanticsNode)) doReturn mockBitmapPainter
        whenever(mockReflectionUtils.getBitmapInBitmapPainter(mockBitmapPainter)) doReturn mockBitmap
        whenever(mockReflectionUtils.isAsyncImagePainter(mockBitmapPainter)) doReturn false
        whenever(mockBitmap.copy(any(), any())) doReturn mockBitmap

        // When
        val result = testedSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)

        // Then
        assertThat(result).isEqualTo(BitmapInfo(mockBitmap, true))
    }

    private fun rectToBounds(rect: Rect, density: Float): GlobalBounds {
        val width = ((rect.right - rect.left) / density).toLong()
        val height = ((rect.bottom - rect.top) / density).toLong()
        val x = (rect.left / density).toLong()
        val y = (rect.top / density).toLong()
        return GlobalBounds(x, y, width, height)
    }
}
