/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.ValueElement
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentityKind
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedTruncationMode
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframeKind
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.BitmapInfo
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCaptureSink
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionNativeSubtree
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class GranularComposeDecomposerTest {

    private val mockSemanticsUtils: SemanticsUtils = mock()
    private val mockReflectionUtils: ReflectionUtils = mock()
    private lateinit var testedGate: GranularComposeCompatibilityGate
    private lateinit var testedDecomposer: GranularComposeDecomposer
    private val mockView: View = mock()

    @Test
    fun `M preserve custom font pixels W decompose() { visible static text and masked images }`() {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        val info = TextLayoutInfo("250.00 €", Color.Black.value, 20, FontFamily(Font(42)))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any())).thenReturn(info)
        val fakeBitmap: Bitmap = mock()
        val registered = mutableListOf<PendingPixelCapture>()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            textRasterizer = ComposeTextRasterizer { _, _, _, _ ->
                RasterizedComposeText(fakeBitmap, GlobalBounds(12, 24, 80, 30))
            }
        )

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(
                imagePrivacy = ImagePrivacy.MASK_ALL,
                pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it }
            )
        )!!

        // Then
        val pixel = result.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixel.bounds).isEqualTo(CapturedBounds(12, 24, 80, 30))
        assertThat(registered.single().bitmap).isSameAs(fakeBitmap)
        assertThat(registered.single().wireframeIdentity).isEqualTo(pixel.identity)
        assertThat(registered.single().isTextOnly).isTrue()
        assertThat(registered.single().isTextFree).isFalse()
        assertThat(registered.single().isSourceImage).isFalse()
    }

    @ParameterizedTest
    @CsvSource(
        "MASK_ALL,false,false",
        "MASK_ALL_INPUTS,true,false",
        "MASK_SENSITIVE_INPUTS,true,false",
        "MASK_SENSITIVE_INPUTS,false,true"
    )
    fun `M never rasterize protected text W decompose() { custom font }`(
        privacy: TextAndInputPrivacy,
        editable: Boolean,
        password: Boolean
    ) {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any()))
            .thenReturn(TextLayoutInfo("sensitive", Color.Black.value, 20, FontFamily(Font(42))))
        whenever(mockSemanticsUtils.isEditable(node)).thenReturn(editable)
        whenever(mockSemanticsUtils.isPassword(node)).thenReturn(password)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            textRasterizer = ComposeTextRasterizer { _, _, _, _ -> error("Protected text must remain semantic") }
        )
        val registered = mutableListOf<PendingPixelCapture>()

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(textPrivacy = privacy, pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it })
        )!!

        // Then
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.Text::class.java)
        assertThat(registered).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["sans-serif", "serif", "monospace", "cursive"])
    fun `M retain supported font aliases W decompose() { standard single-line text }`(family: String) {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        val font = when (family) {
            "serif" -> FontFamily.Serif
            "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.SansSerif
        }
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any()))
            .thenReturn(TextLayoutInfo("Standard text", Color.Black.value, 14, font))

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())!!

        // Then
        val text = result.wireframes.single() as CapturedWireframe.Text
        assertThat(text.textStyle.family).isEqualTo(if (family == "sans-serif") "roboto, sans-serif" else family)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M rasterize only unrepresentable standard text W decompose()`(requiresPixels: Boolean) {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any())).thenReturn(
            TextLayoutInfo(
                "Standard text",
                Color.Black.value,
                14,
                FontFamily.Serif,
                requiresPixelCapture = requiresPixels
            )
        )
        var rasterizations = 0
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            textRasterizer = ComposeTextRasterizer { _, _, _, _ ->
                rasterizations++
                RasterizedComposeText(mock(), GlobalBounds(0, 0, 80, 20))
            }
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())!!

        // Then
        assertThat(rasterizations).isEqualTo(if (requiresPixels) 1 else 0)
        assertThat(result.wireframes.single()).isInstanceOf(
            if (requiresPixels) CapturedWireframe.Pixel::class.java else CapturedWireframe.Text::class.java
        )
    }

    @Test
    fun `M apply inherited and local privacy W decompose() { cached layout text is unchanged }`() {
        // Given
        val stubText = mockLeafNode()
        val stubInput = mockLeafNode()
        val stubPassword = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubText, stubInput, stubPassword))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        listOf(stubText, stubInput, stubPassword).forEach { node ->
            whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any()))
                .thenReturn(TextLayoutInfo("secret", Color.Black.value, 12L, null))
        }
        whenever(
            mockSemanticsUtils.getTextAndInputPrivacyOverride(stubRoot)
        ).thenReturn(TextAndInputPrivacy.MASK_ALL_INPUTS)
        whenever(mockSemanticsUtils.isEditable(stubInput)).thenReturn(true)
        whenever(mockSemanticsUtils.isPassword(stubPassword)).thenReturn(true)

        // When
        val first = testedDecomposer.decompose(mockView, realRequest())!!
        whenever(mockSemanticsUtils.getTextAndInputPrivacyOverride(stubText)).thenReturn(TextAndInputPrivacy.MASK_ALL)
        val second = testedDecomposer.decompose(mockView, realRequest())!!

        // Then
        assertThat(first.wireframes.filterIsInstance<CapturedWireframe.Text>().map { it.text })
            .containsExactly("secret", "***", "***")
        val masked = second.wireframes.filterIsInstance<CapturedWireframe.Text>().map { it.text }
        assertThat(masked.first()).doesNotContain("secret")
        assertThat(masked.drop(1)).containsExactly("***", "***")
    }

    @ParameterizedTest
    @CsvSource("false,true", "false,false", "true,true")
    fun `M position text inside padding W decompose { retain layer bounds and editable geometry }`(
        editable: Boolean,
        attached: Boolean
    ) {
        // Given: recorded Shopist tag geometry, with 8dp padding around the actual label.
        val node = mockLeafNode()
        val outer = GlobalBounds(16, 588, 93, 32)
        val content = GlobalBounds(24, 596, 77, 16)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(outer)
        whenever(mockSemanticsUtils.resolveTextBounds(eq(node), any())).thenReturn(if (attached) content else null)
        whenever(mockSemanticsUtils.isEditable(node)).thenReturn(editable)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any()))
            .thenReturn(TextLayoutInfo("Coco Chanel", Color.Black.value, 14, FontFamily.SansSerif))

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())!!
        val text = result.wireframes.single() as CapturedWireframe.Text

        // Then
        val expected = if (editable || !attached) outer else content
        assertThat(text.bounds).isEqualTo(CapturedBounds(expected.x, expected.y, expected.width, expected.height))
        assertThat(result.nodes.single().bounds).isEqualTo(CapturedBounds(16, 588, 93, 32))
        assertThat(text.textPosition?.alignment?.horizontal).isEqualTo(CapturedHorizontalAlignment.LEFT)
        if (editable) verify(mockSemanticsUtils, never()).resolveTextBounds(any(), any())
    }

    @Test
    fun `M preserve font alignment and truncation W decompose()`() {
        // Given
        val node = mockLeafNode()
        val stubRoot = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any())).thenReturn(
            TextLayoutInfo(
                "text",
                Color.Black.value,
                18,
                FontFamily.Monospace,
                TextAlign.Right,
                MobileSegment.TruncationMode.TAIL
            )
        )

        // When
        val text = testedDecomposer.decompose(mockView, realRequest())!!.wireframes.single() as CapturedWireframe.Text

        // Then
        assertThat(text.textStyle.family).isEqualTo("monospace")
        assertThat(text.textStyle.truncationMode).isEqualTo(CapturedTruncationMode.TAIL)
        assertThat(text.textPosition?.alignment?.horizontal).isEqualTo(CapturedHorizontalAlignment.RIGHT)
    }

    @Test
    fun `M preserve rectangular clips W decompose()`() {
        // Given
        val node = mockClippableNode(true, androidx.compose.ui.graphics.RectangleShape)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())!!

        // Then
        assertThat(result.nodes.flatMap { it.modifiers }.filterIsInstance<CapturedModifier.Clip>()).hasSize(1)
    }

    @Test
    fun `M clip glyph images to visible text bounds W decompose()`() {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        val info = TextLayoutInfo("Long title", Color.Black.value, 20, FontFamily(Font(42)))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any())).thenReturn(info)
        whenever(mockSemanticsUtils.resolveVisibleBounds(eq(node), any())).thenReturn(GlobalBounds(16, 45, 272, 36))
        val bitmap: Bitmap = mock()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            textRasterizer = ComposeTextRasterizer { _, _, _, _ ->
                RasterizedComposeText(bitmap, GlobalBounds(16, 45, 390, 36))
            }
        )

        // When
        val pixel = testedDecomposer.decompose(mockView, realRequest())!!.wireframes.single() as CapturedWireframe.Pixel

        // Then
        assertThat(pixel.clip).isEqualTo(CapturedClip(right = 118))
    }

    @Test
    fun `M capture a permitted source image W decompose() { no OCR extension }`() {
        // Given
        val node = mockLeafNode()
        val stubBitmap: Bitmap = mock()
        whenever(stubBitmap.width).thenReturn(20)
        whenever(stubBitmap.height).thenReturn(20)
        val stubRoot = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.isImage(node)).thenReturn(true)
        whenever(
            mockSemanticsUtils.resolveCompositionPainter(eq(node), any())
        ).thenReturn(BitmapInfo(stubBitmap, false))
        val captures = mutableListOf<PendingPixelCapture>()

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(pendingPixelCaptureSink = PendingPixelCaptureSink(captures::add))
        )!!

        // Then
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.Pixel::class.java)
        assertThat(captures.single().isSourceImage).isTrue()
        assertThat(captures.single().isTextFree).isFalse()
    }

    @ParameterizedTest
    @CsvSource("false,true,false", "true,true,false", "false,false,false", "false,true,true")
    fun `M bake equivalent rounded image clips W decompose() { permitted source only }`(
        masked: Boolean,
        sameBounds: Boolean,
        fails: Boolean
    ) {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        val bitmap: Bitmap = mock()
        val clippedBitmap: Bitmap = mock()
        val shape: Shape = mock()
        val clipInfo: ModifierInfo = mock()
        val painterInfo: ModifierInfo = mock()
        val clip = mockGraphicsLayerModifier(mapOf("clip" to true, "shape" to shape))
        val painter: Modifier = mock()
        whenever(clipInfo.modifier).thenReturn(clip)
        whenever(painterInfo.modifier).thenReturn(painter)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(clipInfo, painterInfo))
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(mockReflectionUtils.isImagePainterModifier(painter)).thenReturn(true)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.isImage(node)).thenReturn(true)
        whenever(bitmap.width).thenReturn(200)
        whenever(bitmap.height).thenReturn(100)
        whenever(mockSemanticsUtils.resolveCompositionPainter(eq(node), any()))
            .thenReturn(BitmapInfo(bitmap, false, ContentScale.Crop))
        val viewport = GlobalBounds(32, 32, 160, 160)
        whenever(mockSemanticsUtils.resolveUnclippedBounds(eq(node), any())).thenReturn(viewport)
        whenever(mockSemanticsUtils.resolveVisibleBounds(eq(node), any())).thenReturn(viewport)
        whenever(mockSemanticsUtils.resolveModifierBounds(eq(node), eq(clipInfo), any()))
            .thenReturn(if (sameBounds) viewport else GlobalBounds(0, 0, 224, 224))
        whenever(mockSemanticsUtils.resolveCornerRadius(eq(shape), any(), any())).thenReturn(32f)
        var clips = 0
        val failure = IllegalStateException("simulated clipping failure")
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            sourceImageClipper = ComposeSourceImageClipper { source, scaled, crop, radius, _ ->
                clips++
                if (fails) throw failure
                assertThat(source).isSameAs(bitmap)
                assertThat(scaled).isEqualTo(GlobalBounds(-48, 32, 320, 160))
                assertThat(crop).isEqualTo(viewport)
                assertThat(radius).isEqualTo(32f)
                clippedBitmap
            }
        )
        val captures = mutableListOf<PendingPixelCapture>()

        // When
        val capture = {
            testedDecomposer.decompose(
                mockView,
                realRequest(
                    imagePrivacy = if (masked) ImagePrivacy.MASK_ALL else ImagePrivacy.MASK_NONE,
                    pendingPixelCaptureSink = PendingPixelCaptureSink(captures::add)
                )
            )!!
        }
        if (fails) {
            assertThatThrownBy { capture() }.isSameAs(failure)
            verify(bitmap).recycle()
            assertThat(captures).isEmpty()
            return
        }
        val result = capture()

        // Then
        assertThat(clips).isEqualTo(if (!masked && sameBounds) 1 else 0)
        if (masked) {
            assertThat(captures).isEmpty()
            verify(mockSemanticsUtils, never()).resolveCompositionPainter(any(), any())
        } else {
            assertThat(captures.single().isSourceImage).isTrue()
            assertThat(captures.single().bitmap).isSameAs(if (sameBounds) clippedBitmap else bitmap)
            if (sameBounds) {
                assertThat(result.wireframes.single().bounds).isEqualTo(CapturedBounds(32, 32, 160, 160))
                verify(bitmap).recycle()
            }
        }
    }

    @Test
    fun `M crop source images without resizing W decompose() { image scrolls outside viewport }`() {
        // Given
        val stubImage = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubImage))
        val stubBitmap: Bitmap = mock()
        whenever(stubBitmap.width).thenReturn(100)
        whenever(stubBitmap.height).thenReturn(100)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.isImage(stubImage)).thenReturn(true)
        whenever(mockSemanticsUtils.resolveCompositionPainter(eq(stubImage), any()))
            .thenReturn(BitmapInfo(stubBitmap, false))
        val fakeLayoutBounds = GlobalBounds(0, 0, 160, 160)
        whenever(mockSemanticsUtils.resolveUnclippedBounds(eq(stubImage), any())).thenReturn(fakeLayoutBounds)

        // When
        val fakeImages = listOf(fakeLayoutBounds, GlobalBounds(0, 60, 160, 100), fakeLayoutBounds).map { visible ->
            whenever(mockSemanticsUtils.resolveVisibleBounds(eq(stubImage), any())).thenReturn(visible)
            testedDecomposer.decompose(mockView, realRequest())!!.wireframes.single() as CapturedWireframe.Pixel
        }

        // Then
        assertThat(fakeImages.map { it.bounds }).containsOnly(CapturedBounds(0, 0, 160, 160))
        assertThat(fakeImages.map { it.clip?.top }).containsExactly(null, 60L, null)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve image padding W decompose() { outer semantics and partial clipping }`(masked: Boolean) {
        // Given
        val stubImage = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubImage))
        val stubBitmap: Bitmap = mock()
        whenever(stubBitmap.width).thenReturn(100)
        whenever(stubBitmap.height).thenReturn(100)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.isImage(stubImage)).thenReturn(true)
        whenever(mockSemanticsUtils.resolveCompositionPainter(eq(stubImage), any()))
            .thenReturn(BitmapInfo(stubBitmap, false))
        whenever(mockSemanticsUtils.resolveUnclippedBounds(eq(stubImage), any()))
            .thenReturn(GlobalBounds(0, 0, 224, 224))
        whenever(mockSemanticsUtils.resolvePainterBounds(eq(stubImage), any(), any()))
            .thenReturn(GlobalBounds(32, 32, 160, 160))
        whenever(mockSemanticsUtils.resolveVisibleBounds(eq(stubImage), any()))
            .thenReturn(GlobalBounds(0, 64, 224, 160))

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(imagePrivacy = if (masked) ImagePrivacy.MASK_ALL else ImagePrivacy.MASK_NONE)
        )!!

        // Then
        val image = result.wireframes.single()
        assertThat(image.bounds).isEqualTo(CapturedBounds(32, 32, 160, 160))
        assertThat(image.clip?.top).isEqualTo(32L)
        assertThat(image.clip?.left).isNull()
        assertThat(result.nodes.single().bounds).isEqualTo(CapturedBounds(0, 0, 224, 224))
        if (masked) verify(mockSemanticsUtils, never()).resolveCompositionPainter(any(), any())
    }

    @Test
    fun `M crop image to painter viewport W decompose() { crop inside padding }`() {
        // Given
        val stubImage = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubImage))
        val stubBitmap: Bitmap = mock()
        whenever(stubBitmap.width).thenReturn(200)
        whenever(stubBitmap.height).thenReturn(100)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.isImage(stubImage)).thenReturn(true)
        whenever(mockSemanticsUtils.resolveCompositionPainter(eq(stubImage), any()))
            .thenReturn(BitmapInfo(stubBitmap, false, ContentScale.Crop))
        whenever(mockSemanticsUtils.resolveUnclippedBounds(eq(stubImage), any()))
            .thenReturn(GlobalBounds(0, 0, 224, 224))
        whenever(mockSemanticsUtils.resolvePainterBounds(eq(stubImage), any(), any()))
            .thenReturn(GlobalBounds(32, 32, 160, 160))
        whenever(mockSemanticsUtils.resolveVisibleBounds(eq(stubImage), any()))
            .thenReturn(GlobalBounds(0, 0, 224, 224))

        // When
        val image = testedDecomposer.decompose(mockView, realRequest())!!.wireframes.single()

        // Then
        assertThat(image.bounds).isEqualTo(CapturedBounds(-48, 32, 320, 160))
        assertThat(image.clip?.left).isEqualTo(80L)
        assertThat(image.clip?.right).isEqualTo(80L)
    }

    @Test
    fun `M retain editable field background W decompose() { no text layout and masked input }`() {
        // Given
        val stubField = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubField))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.isEditable(stubField)).thenReturn(true)
        whenever(mockSemanticsUtils.editableText(stubField)).thenReturn("secret")
        whenever(
            mockSemanticsUtils.resolveBackgroundColor(any<List<ModifierInfo>>())
        ).thenReturn(Color.White.value.toLong())

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(textPrivacy = TextAndInputPrivacy.MASK_ALL_INPUTS)
        )!!

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>()).hasSize(1)
        val text = result.wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(text.text).isEqualTo("***")
        assertThat(text.textPosition?.padding?.left).isEqualTo(4L)
        assertThat(result.nodes.single().children.map { it.identity })
            .containsExactlyElementsOf(result.wireframes.map { it.identity })
    }

    @Test
    fun `M retain ownership of rendered painter pixels W decompose()`() {
        // Given
        val node = mockLeafNode()
        val bitmap: Bitmap = mock()
        whenever(bitmap.width).thenReturn(20)
        whenever(bitmap.height).thenReturn(20)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.isImage(node)).thenReturn(true)
        whenever(mockSemanticsUtils.resolveCompositionPainter(eq(node), any())).thenReturn(BitmapInfo(bitmap, false))
        val captures = mutableListOf<PendingPixelCapture>()

        // When
        testedDecomposer.decompose(
            mockView,
            realRequest(pendingPixelCaptureSink = PendingPixelCaptureSink(captures::add))
        )

        // Then
        assertThat(captures.single().bitmap).isSameAs(bitmap)
        verify(bitmap, never()).copy(any(), any())
        verify(bitmap, never()).recycle()
    }

    @Test
    fun `M retain padded background bounds and paint border after content W decompose()`() {
        // Given
        val child = mockLeafNode()
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(child), any(), any()))
            .thenReturn(TextLayoutInfo("No Recents", Color.Black.value, 14, null))
        val node = mockNode(GlobalBounds(0, 20, 360, 105), listOf(child))
        val background: Modifier = mock()
        val backgroundInfo: ModifierInfo = mock()
        whenever(backgroundInfo.modifier).thenReturn(background)
        whenever(mockReflectionUtils.isBackgroundElement(background)).thenReturn(true)
        val border = mockGraphicsLayerModifier(mapOf("width" to androidx.compose.ui.unit.Dp(1f), "color" to Color.Red))
        whenever((border as InspectableValue).nameFallback).thenReturn("border")
        val borderInfo: ModifierInfo = mock()
        whenever(borderInfo.modifier).thenReturn(border)
        val modifiers = listOf(borderInfo, backgroundInfo)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(modifiers)
        val drawingBounds = GlobalBounds(16, 36, 328, 150)
        modifiers.forEach {
            whenever(mockSemanticsUtils.resolveModifierBounds(eq(node), eq(it), any())).thenReturn(drawingBounds)
        }
        whenever(
            mockSemanticsUtils.resolveBackgroundColor(listOf(backgroundInfo))
        ).thenReturn(Color.Gray.value.toLong())
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())!!

        // Then
        val backgroundShape = result.wireframes.first() as CapturedWireframe.Shape
        val text = result.wireframes[1] as CapturedWireframe.Text
        val borderShape = result.wireframes.last() as CapturedWireframe.Shape
        assertThat(backgroundShape.bounds).isEqualTo(CapturedBounds(16, 36, 328, 150))
        assertThat(text.text).isEqualTo("No Recents")
        assertThat(borderShape.bounds).isEqualTo(backgroundShape.bounds)
        assertThat(borderShape.border?.width).isEqualTo(1L)
        assertThat(borderShape.border?.color).isEqualTo("#ff0000ff")
        val parent = result.nodes.last()
        assertThat(parent.children.first().identity).isEqualTo(backgroundShape.identity)
        assertThat(parent.children.last().identity).isEqualTo(borderShape.identity)
    }

    @ParameterizedTest
    @ValueSource(strings = ["hidden", "password", "input", "image", "interop"])
    fun `M exclude restricted descendants from parent raster W decompose()`(fakeRestriction: String) {
        // Given
        val stubParent = mockDrawingEffectNode()
        val stubChild = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubParent))
        whenever(stubParent.children).thenReturn(listOf(stubChild))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        when (fakeRestriction) {
            "hidden" -> whenever(mockSemanticsUtils.isNodeHidden(stubChild)).thenReturn(true)
            "password" -> whenever(mockSemanticsUtils.isPassword(stubChild)).thenReturn(true)
            "input" -> {
                whenever(mockSemanticsUtils.hasInputState(stubChild)).thenReturn(true)
                whenever(mockSemanticsUtils.isEditable(stubChild)).thenReturn(true)
            }
            "image" -> {
                whenever(mockSemanticsUtils.isImage(stubChild)).thenReturn(true)
                whenever(mockSemanticsUtils.getImagePrivacyOverride(stubChild)).thenReturn(ImagePrivacy.MASK_ALL)
            }
            "interop" -> whenever(mockReflectionUtils.getInteropView(stubChild)).thenReturn(mockView)
        }
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(stubChild), any(), any()))
            .thenReturn(TextLayoutInfo("secret", Color.Black.value, 12L, null))
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )
        val fakeCaptures = mutableListOf<PendingPixelCapture>()

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(
                textPrivacy = TextAndInputPrivacy.MASK_ALL_INPUTS,
                pendingPixelCaptureSink = PendingPixelCaptureSink(fakeCaptures::add)
            )
        )

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isZero()
        assertThat(fakeCaptures).isEmpty()
        assertThat(result!!.wireframes.filterIsInstance<CapturedWireframe.Text>()).allMatch { it.text != "secret" }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M exclude protected siblings from raster crops W decompose()`(fakeOverlap: Boolean) {
        // Given
        val stubDrawing = mockDrawingEffectNode()
        val stubHidden = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubDrawing, stubHidden))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.isNodeHidden(stubHidden)).thenReturn(true)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(stubHidden), any()))
            .thenReturn(GlobalBounds(if (fakeOverlap) 0 else 20, 0, 10, 10))
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        val fakeCaptures = mutableListOf<PendingPixelCapture>()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(pendingPixelCaptureSink = PendingPixelCaptureSink(fakeCaptures::add))
        )!!

        // Then
        assertThat(fakeRasterizer.callCount).isEqualTo(if (fakeOverlap) 0 else 1)
        assertThat(fakeCaptures).hasSize(if (fakeOverlap) 0 else 1)
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>())
            .hasSize(if (fakeOverlap) 2 else 1)
    }

    @Test
    fun `M apply current image privacy W decompose() { source was captured previously }`() {
        // Given
        val stubImage = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubImage))
        val stubBitmap: Bitmap = mock()
        whenever(stubBitmap.width).thenReturn(20)
        whenever(stubBitmap.height).thenReturn(20)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.isImage(stubImage)).thenReturn(true)
        whenever(
            mockSemanticsUtils.resolveCompositionPainter(eq(stubImage), any())
        ).thenReturn(BitmapInfo(stubBitmap, false))
        val fakeCaptures = mutableListOf<PendingPixelCapture>()
        val fakeRequest = realRequest(pendingPixelCaptureSink = PendingPixelCaptureSink(fakeCaptures::add))
        testedDecomposer.decompose(mockView, fakeRequest)
        assertThat(fakeCaptures).hasSize(1)
        fakeCaptures.clear()

        // When
        whenever(mockSemanticsUtils.getImagePrivacyOverride(stubImage)).thenReturn(ImagePrivacy.MASK_ALL)
        val result = testedDecomposer.decompose(mockView, fakeRequest)!!

        // Then
        assertThat(fakeCaptures).isEmpty()
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M propagate inherited and local privacy W decompose() { native interop }`() {
        // Given
        val stubInterop = mockLeafNode()
        val stubRoot = mockRoot(listOf(stubInterop))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        whenever(mockReflectionUtils.getInteropView(stubInterop)).thenReturn(mockView)
        whenever(mockSemanticsUtils.getTextAndInputPrivacyOverride(stubRoot))
            .thenReturn(TextAndInputPrivacy.MASK_ALL_INPUTS)
        whenever(mockSemanticsUtils.getImagePrivacyOverride(stubInterop)).thenReturn(ImagePrivacy.MASK_ALL)
        val fakeFactory = FakeCompositionIdentityFactory()
        val fakePrivacy = mutableListOf<Pair<ImagePrivacy, TextAndInputPrivacy>>()
        val fakeRequest = CompositionHostDecomposeRequest(
            fakeFactory,
            fakeFactory.anyIdentity(),
            1f,
            hostBounds = null,
            nativeViewHandoff = { _, _ -> error("Expected privacy-aware handoff") },
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            nativeViewHandoffWithPrivacy = { _, identity, image, text ->
                fakePrivacy += image to text
                val layer =
                    CapturedLayer(identity, CapturedLayerKind.NATIVE_VIEW, CapturedBounds(0, 0, 10, 10), emptyList())
                CompositionNativeSubtree(layer, listOf(layer), emptyList())
            }
        )

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result).isNotNull()
        assertThat(fakePrivacy).containsExactly(ImagePrivacy.MASK_ALL to TextAndInputPrivacy.MASK_ALL_INPUTS)
    }

    @BeforeEach
    fun `set up`() {
        testedGate = GranularComposeCompatibilityGate()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate
        )
    }

    @Test
    fun `M return false W canDecompose() { no root semantics node }`() {
        // Given
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(null)

        // When
        val result = testedDecomposer.canDecompose(mockView)

        // Then
        assertThat(result).isFalse()
    }

    @ParameterizedTest
    @CsvSource(
        "probe,exception", "probe,assertion", "probe,oom",
        "capture,exception", "capture,assertion", "capture,oom",
        "privacy,exception", "privacy,assertion", "privacy,oom"
    )
    fun `M propagate non-linkage failures W capture entry points`(entryPoint: String, kind: String) {
        // Given
        val failure = when (kind) {
            "oom" -> OutOfMemoryError("simulated allocation failure")
            "assertion" -> AssertionError("simulated assertion failure")
            else -> IllegalStateException("simulated bug")
        }
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenAnswer { throw failure }

        // When + Then
        assertThatThrownBy {
            when (entryPoint) {
                "probe" -> testedDecomposer.canDecompose(mockView)
                "privacy" -> testedDecomposer.readTouchPrivacy(mockView) { true }
                else -> testedDecomposer.decompose(mockView, realRequest())
            }
        }.isSameAs(failure)
        assertThat(testedGate.isAvailable()).isTrue()
    }

    @Test
    fun `M return false W canDecompose() { gate already tripped }`() {
        // Given
        testedGate.markIncompatible(LinkageError(), mock())

        // When
        val result = testedDecomposer.canDecompose(mockView)

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(mockSemanticsUtils)
    }

    @Test
    fun `M return null W decompose() { gate already tripped }`() {
        // Given
        testedGate.markIncompatible(LinkageError(), mock())
        val request: CompositionHostDecomposeRequest = mock()

        // When
        val result = testedDecomposer.decompose(mockView, request)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M trip the gate and return null W decompose() { runtime throws }`(
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenThrow(NoSuchMethodError(fakeErrorMessage))
        val request: CompositionHostDecomposeRequest = mock()

        // When
        val result = testedDecomposer.decompose(mockView, request)

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isFalse()
        assertThat(testedDecomposer.canDecompose(mockView)).isFalse()
    }

    @Test
    fun `M return null W decompose() { no root semantics node }`() {
        // Given
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(null)
        val request: CompositionHostDecomposeRequest = mock()

        // When
        val result = testedDecomposer.decompose(mockView, request)

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isTrue()
    }

    @Test
    fun `M capture a leaf entering the viewport W decompose() { clipped leaf is skipped initially }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        val fakeRequest = requestWithLargeGroup(stubLeaf)

        // When
        val outsideResult = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(outsideResult).isNotNull()
        assertThat(outsideResult?.nodes).isEmpty()
        assertThat(outsideResult?.wireframes).isEmpty()
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(stubLeaf), any(), any())

        // When
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(stubLeaf), any()))
            .thenReturn(GlobalBounds(10, 90, 20, 20))
        whenever(stubLeaf.boundsInRoot).thenReturn(androidx.compose.ui.geometry.Rect(10f, 90f, 30f, 100f))
        val enteringResult = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(enteringResult?.nodes).hasSize(1)
        assertThat(enteringResult?.nodes?.single()?.bounds?.y).isEqualTo(90L)
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubLeaf), any(), any())
    }

    @Test
    fun `M retain overflowing children W decompose() { parent bounds are outside viewport }`() {
        // Given
        val stubChild = mockNode(GlobalBounds(10, 10, 20, 20))
        val stubParent = mockNode(GlobalBounds(-50, 10, 20, 20), listOf(stubChild))
        val fakeRequest = requestWithLargeGroup(stubParent)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(2)
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubChild), any(), any())
    }

    @Test
    fun `M retain nodes W decompose() { copied host clip is unknown }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        val fakeRequest = requestWithLargeGroup(stubLeaf, hostBounds = null)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(32)
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubLeaf), any(), any())
    }

    @Test
    fun `M return an empty composition W decompose() { all leaves are clipped }`() {
        // Given
        val stubLeaves = List(32) { mockNode(GlobalBounds(10, 110, 20, 20)) }
        val fakeRequest = requestWithRoots(stubLeaves)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.rootChildren).isEmpty()
        assertThat(result?.nodes).isEmpty()
        assertThat(result?.wireframes).isEmpty()
        assertThat(testedGate.isAvailable()).isTrue()
    }

    @Test
    fun `M retain small groups W decompose() { selective culling below threshold }`() {
        // Given
        val stubLeaves = List(31) { mockNode(GlobalBounds(10, 110, 20, 20)) }
        val stubParent = mockNode(GlobalBounds(0, 0, 100, 100), stubLeaves)
        val fakeRequest = requestWithRoot(stubParent)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(32)
        stubLeaves.forEach { verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(it), any(), any()) }
    }

    @Test
    fun `M cull large groups W decompose() { selective culling reaches threshold }`() {
        // Given
        val stubOffscreenLeaves = List(31) { mockNode(GlobalBounds(10, 110, 20, 20)) }
        val stubVisibleLeaf = mockNode(GlobalBounds(10, 10, 20, 20))
        val stubParent = mockNode(GlobalBounds(0, 0, 100, 100), stubOffscreenLeaves + stubVisibleLeaf)
        val fakeRequest = requestWithRoot(stubParent)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(2)
        stubOffscreenLeaves.forEach { verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(it), any(), any()) }
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubVisibleLeaf), any(), any())
    }

    @Test
    fun `M retain intersecting leaves W decompose() { partial visibility and zero extent }`() {
        // Given
        val fakeBounds = listOf(
            GlobalBounds(-19, 10, 20, 20),
            GlobalBounds(99, 10, 20, 20),
            GlobalBounds(10, -19, 20, 20),
            GlobalBounds(10, 99, 20, 20),
            GlobalBounds(10, 110, 0, 20)
        )
        val stubLeaves = fakeBounds.map { mockNode(it) }
        val fakeRequest = requestWithRoots(stubLeaves + List(27) { mockNode(GlobalBounds(10, 110, 20, 20)) })

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(5)
        stubLeaves.forEach { verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(it), any(), any()) }
    }

    @Test
    fun `M retain hidden placeholders W decompose() { leaf is outside host clip }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        whenever(mockSemanticsUtils.isNodeHidden(stubLeaf)).thenReturn(true)
        val fakeRequest = requestWithLargeGroup(stubLeaf)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(1)
        assertThat(result?.wireframes).hasSize(1)
        verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(stubLeaf), any(), any())
        verify(mockReflectionUtils, never()).getInteropView(stubLeaf)
    }

    @Test
    fun `M retain native handoff W decompose() { interop leaf is outside host clip }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        val stubInteropView: View = mock()
        whenever(mockReflectionUtils.getInteropView(stubLeaf)).thenReturn(stubInteropView)
        val fakeRoot = CapturedLayer(
            mock(),
            CapturedLayerKind.NATIVE_VIEW,
            CapturedBounds(10, 110, 20, 20),
            emptyList()
        )
        val fakeSubtree = CompositionNativeSubtree(fakeRoot, listOf(fakeRoot), emptyList())
        val fakeRequest = requestWithLargeGroup(stubLeaf, nativeHandoff = { view, _ ->
            assertThat(view).isSameAs(stubInteropView)
            fakeSubtree
        })

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).containsExactly(fakeRoot)
        verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(stubLeaf), any(), any())
    }

    private fun mockNode(bounds: GlobalBounds, children: List<SemanticsNode> = emptyList()): SemanticsNode {
        val stubNode: SemanticsNode = mock()
        val stubLayout: LayoutInfo = mock()
        whenever(stubNode.layoutInfo).thenReturn(stubLayout)
        whenever(stubLayout.getModifierInfo()).thenReturn(emptyList())
        whenever(stubNode.children).thenReturn(children)
        whenever(stubNode.boundsInRoot).thenReturn(
            androidx.compose.ui.geometry.Rect(
                bounds.x.toFloat(),
                bounds.y.toFloat(),
                (bounds.x + bounds.width).toFloat(),
                (bounds.y + bounds.height).toFloat()
            )
        )
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(stubNode), any())).thenReturn(bounds)
        whenever(mockSemanticsUtils.resolveVisibleBounds(eq(stubNode), any())).thenReturn(bounds)
        whenever(stubLayout.layoutDirection).thenReturn(LayoutDirection.Ltr)
        whenever(mockSemanticsUtils.resolveBackgroundColor(any<List<ModifierInfo>>())).thenReturn(null)
        return stubNode
    }

    private fun requestWithRoot(
        node: SemanticsNode,
        hostBounds: CapturedBounds? = CapturedBounds(0, 0, 100, 100),
        nativeHandoff: (View, CapturedIdentity) -> CompositionNativeSubtree? = { _, _ -> null }
    ): CompositionHostDecomposeRequest = requestWithRoots(listOf(node), hostBounds, nativeHandoff)

    private fun requestWithLargeGroup(
        node: SemanticsNode,
        hostBounds: CapturedBounds? = CapturedBounds(0, 0, 100, 100),
        nativeHandoff: (View, CapturedIdentity) -> CompositionNativeSubtree? = { _, _ -> null }
    ): CompositionHostDecomposeRequest = requestWithRoots(
        listOf(node) + List(31) { mockNode(GlobalBounds(10, 110, 20, 20)) },
        hostBounds,
        nativeHandoff
    )

    private fun requestWithRoots(
        nodes: List<SemanticsNode>,
        hostBounds: CapturedBounds? = CapturedBounds(0, 0, 100, 100),
        nativeHandoff: (View, CapturedIdentity) -> CompositionNativeSubtree? = { _, _ -> null }
    ): CompositionHostDecomposeRequest {
        val stubRoot: SemanticsNode = mock()
        whenever(stubRoot.children).thenReturn(nodes)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        val stubIdentityFactory: CompositionIdentityFactory = mock()
        whenever(stubIdentityFactory.composeNode(any(), any())).thenAnswer { mock<CapturedIdentity>() }
        whenever(stubIdentityFactory.placeholderWireframe(any())).thenAnswer { mock<CapturedIdentity>() }
        whenever(stubIdentityFactory.imageWireframe(any())).thenAnswer { mock<CapturedIdentity>() }
        return CompositionHostDecomposeRequest(stubIdentityFactory, mock(), 1f, hostBounds, nativeHandoff)
    }

    // region shouldContinue checkpoint

    private var nextNodeId = 0

    /** A childless node that resolves to nothing (no text, no shape, no interop) - the cheapest leaf shape. */
    private fun mockLeafNode(): SemanticsNode {
        val node: SemanticsNode = mock()
        // getId()'s real implementation reads layoutInfo internally, so this must be stubbed first -
        // otherwise evaluating node.id below throws mid-stub and corrupts Mockito's recording state.
        val layoutInfo: LayoutInfo = mock()
        whenever(node.layoutInfo).thenReturn(layoutInfo)
        whenever(layoutInfo.layoutDirection).thenReturn(LayoutDirection.Ltr)
        whenever(layoutInfo.getModifierInfo()).thenReturn(emptyList())
        whenever(node.id).thenReturn(nextNodeId++)
        whenever(node.children).thenReturn(emptyList())
        whenever(mockSemanticsUtils.isNodeHidden(node)).thenReturn(false)
        whenever(mockReflectionUtils.getInteropView(node)).thenReturn(null)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any())).thenReturn(null)
        whenever(mockSemanticsUtils.resolveBackgroundColor(any<List<ModifierInfo>>())).thenReturn(null)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 10, 10))
        whenever(mockSemanticsUtils.resolveUnclippedBounds(eq(node), any())).thenAnswer {
            mockSemanticsUtils.resolveInnerBounds(node, it.getArgument(1))
        }
        whenever(mockSemanticsUtils.resolvePainterBounds(eq(node), any(), any())).thenAnswer {
            mockSemanticsUtils.resolveUnclippedBounds(node, it.getArgument(2))
        }
        whenever(mockSemanticsUtils.resolveVisibleBounds(eq(node), any())).thenAnswer {
            mockSemanticsUtils.resolveInnerBounds(node, it.getArgument(1))
        }
        whenever(node.boundsInRoot).thenReturn(androidx.compose.ui.geometry.Rect(0f, 0f, 10f, 10f))
        return node
    }

    private fun mockRoot(children: List<SemanticsNode>): SemanticsNode {
        val root: SemanticsNode = mock()
        whenever(root.children).thenReturn(children)
        whenever(mockSemanticsUtils.resolveBackgroundColor(root)).thenReturn(null)
        return root
    }

    private fun realRequest(
        shouldContinue: () -> Boolean = { true },
        pixelCapturePlaceholderLabelFor: (
            bounds: com.datadog.android.internal.sessionreplay.composition.CapturedBounds
        ) -> String? = { null },
        pendingPixelCaptureSink: PendingPixelCaptureSink = PendingPixelCaptureSink.NoOp,
        textPrivacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
        imagePrivacy: ImagePrivacy = ImagePrivacy.MASK_NONE,
        screenDensity: Float = 1f
    ): CompositionHostDecomposeRequest {
        val identityFactory = FakeCompositionIdentityFactory()
        return CompositionHostDecomposeRequest(
            identityFactory = identityFactory,
            hostIdentity = identityFactory.anyIdentity(),
            screenDensity = screenDensity,
            hostBounds = null,
            nativeViewHandoff = { _, _ -> null },
            shouldContinue = shouldContinue,
            pixelCapturePlaceholderLabelFor = pixelCapturePlaceholderLabelFor,
            pendingPixelCaptureSink = pendingPixelCaptureSink,
            textAndInputPrivacy = textPrivacy,
            imagePrivacy = imagePrivacy
        )
    }

    @Test
    fun `M abort and return null W decompose() { shouldContinue denies at the first checkpoint }`() {
        // Given
        val root = mockRoot(List(3) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 1
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { false }))

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M complete the walk W decompose() { shouldContinue keeps allowing }`() {
        // Given
        val root = mockRoot(List(5) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 1
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { true }))

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.rootChildren).hasSize(5)
    }

    @Test
    fun `M check shouldContinue only every nodesPerCheckpoint nodes W decompose()`() {
        // Given
        val root = mockRoot(List(6) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 2,
            hostRasterizer = ComposeHostCaptureRasterizer { _, regions -> regions.map { null } }
        )
        var checkpointCalls = 0

        // When
        testedDecomposer.decompose(mockView, realRequest(shouldContinue = { checkpointCalls++; true }))

        // Then - 3 walk checkpoints, 6 raster privacy checks, and 3 eligibility/draw checks.
        // that fires because every childless mockLeafNode() here is itself a pixel-capture
        // candidate (see the "pixel capture" test region below).
        assertThat(checkpointCalls).isEqualTo(12)
    }

    // endregion

    @Test
    fun `M inspect modifiers and children once W decompose() { nonterminal container }`() {
        // Given
        val node = mockLeafNode()
        val child = mockLeafNode()
        whenever(node.children).thenReturn(listOf(child))
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(result).isNotNull()
        verify(node.layoutInfo).getModifierInfo()
        verify(node).children
    }

    @Test
    fun `M refresh children W decompose() { subsequent capture }`() {
        // Given
        val node = mockLeafNode()
        val firstChild = mockLeafNode()
        val secondChild = mockLeafNode()
        whenever(node.children).thenReturn(listOf(firstChild))
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val first = testedDecomposer.decompose(mockView, realRequest())
        whenever(node.children).thenReturn(listOf(firstChild, secondChild))
        val second = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(first!!.nodes).hasSize(2)
        assertThat(second!!.nodes).hasSize(3)
        verify(node.layoutInfo, times(2)).getModifierInfo()
    }

    @Test
    fun `M discard expired raster output W decompose() { draw consumes deadline }`() {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        var active = true
        val rasterizer = ComposeHostCaptureRasterizer { _, regions ->
            active = false
            regions.map { mock<Bitmap>() }
        }
        val registered = mutableListOf<PendingPixelCapture>()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = rasterizer
        )

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(
                shouldContinue = { active },
                pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it }
            )
        )

        // Then
        assertThat(result).isNull()
        assertThat(registered).isEmpty()
        assertThat(testedGate.isAvailable()).isTrue()
    }

    // region pixel capture

    @Test
    fun `M inspect overscroll state on every capture W decompose() { legacy drawing container }`() {
        // Given
        val stubContainer = mockLeafNode()
        val children = listOf(mockLeafNode(), mockLeafNode())
        val stubModifier: DrawModifier = mock()
        val stubModifierInfo: ModifierInfo = mock()
        val stubRoot = mockRoot(listOf(stubContainer))
        whenever(stubModifierInfo.modifier).thenReturn(stubModifier)
        whenever(stubContainer.layoutInfo.getModifierInfo()).thenReturn(listOf(stubModifierInfo))
        whenever(stubContainer.children).thenReturn(children)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When / Then - custom, active and unrecognized drawing modifiers retain whole-container capture.
        assertThat(testedDecomposer.decompose(mockView, realRequest())?.wireframes).hasSize(1)
        whenever(mockReflectionUtils.isInactiveOverscrollModifier(stubModifier)).thenReturn(true)
        assertThat(testedDecomposer.decompose(mockView, realRequest())?.wireframes).hasSize(2)
        whenever(mockReflectionUtils.isInactiveOverscrollModifier(stubModifier)).thenReturn(false)
        assertThat(testedDecomposer.decompose(mockView, realRequest())?.wireframes).hasSize(1)
    }

    @Test
    fun `M rasterize only visible leaves W decompose() { culling precedes pixel capture }`() {
        // Given
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )
        val stubVisibleLeaf = mockNode(GlobalBounds(10, 10, 20, 20))
        val fakeRequest = requestWithLargeGroup(stubVisibleLeaf)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(testedGate.isAvailable()).isTrue()
        assertThat(fakeRasterizer.callCount).isEqualTo(1)
        assertThat(fakeRasterizer.lastRegions).hasSize(1)
        assertThat(result?.nodes).hasSize(1)
        assertThat(result?.wireframes?.single()).isInstanceOf(CapturedWireframe.Pixel::class.java)
    }

    private class FakeComposeHostCaptureRasterizer(
        private val bitmapsToReturn: (regions: List<Rect>) -> List<Bitmap?> = { regions -> regions.map { mock() } }
    ) : ComposeHostCaptureRasterizer {
        var callCount = 0
        var lastRegions: List<Rect> = emptyList()

        override fun captureRegions(hostView: View, regions: List<Rect>): List<Bitmap?> {
            callCount++
            lastRegions = regions
            return bitmapsToReturn(regions)
        }
    }

    /** A real drawBehind element; the node created by it is not the ModifierInfo element. */
    private fun mockDrawingEffectNode(): SemanticsNode {
        val node = mockLeafNode()
        // A node with its own drawing effect commonly has children too (e.g. a Card wrapping
        // content) - non-empty here specifically to prove capture doesn't depend on childlessness.
        // Evaluated before any whenever(...).thenReturn(...) below - mockLeafNode() itself performs
        // several stubbing calls, and nesting a fresh one inside another's still-open .thenReturn(...)
        // argument corrupts Mockito's stubbing recorder (see the block-7 fix for this same mistake
        // earlier in this file).
        val childNode = mockLeafNode()
        val drawModifier = Modifier.drawBehind {}
        whenever(mockReflectionUtils.isDrawModifierElement(drawModifier)).thenReturn(true)
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(drawModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.children).thenReturn(listOf(childNode))
        return node
    }

    @Test
    fun `M register a pending capture and emit a Pixel wireframe W decompose() { drawing-effect node }`() {
        // Given
        val node = mockDrawingEffectNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )
        val registered = mutableListOf<PendingPixelCapture>()

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it })
        )

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(1)
        assertThat(registered).hasSize(1)
        val pixelWireframe = result!!.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixelWireframe.resource).isEqualTo(PixelResource.Unresolved)
        assertThat(pixelWireframe.identity).isEqualTo(registered.single().wireframeIdentity)
        // Never recurses into a capture candidate's own children (would double-render).
        assertThat(result.wireframes).hasSize(1)
    }

    @ParameterizedTest
    @CsvSource("508,5,194,2", "736,27,280,10", "508,1,194,1")
    fun `M preserve visible crop geometry W decompose() { fractional density and thin captures }`(
        top: Int,
        height: Int,
        expectedY: Long,
        expectedHeight: Long
    ) {
        // Given - the emulator's divider geometry; semantic bounds deliberately describe a different box.
        val node = mockDrawingEffectNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(node.boundsInRoot).thenReturn(
            androidx.compose.ui.geometry.Rect(42f, top.toFloat(), 1038f, (top + height).toFloat())
        )
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(screenDensity = 2.625f))!!

        // Then
        val pixel = result.wireframes.single() as CapturedWireframe.Pixel
        assertThat(fakeRasterizer.lastRegions).containsExactly(Rect(42, top, 1038, top + height))
        assertThat(pixel.bounds).isEqualTo(CapturedBounds(16, expectedY, 379, expectedHeight))
        assertThat(result.nodes.single().bounds).isEqualTo(pixel.bounds)
    }

    @Test
    fun `M add the raw host offset before rounding W decompose() { pixel crop }`() {
        // Given - truncating the host offset before adding it would lose a dp.
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(node.boundsInRoot).thenReturn(androidx.compose.ui.geometry.Rect(2f, 2f, 12f, 12f))
        doAnswer { invocation ->
            val offset = invocation.getArgument<IntArray>(0)
            offset[0] = 2
            offset[1] = 2
            null
        }.whenever(mockView).getLocationOnScreen(any())
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = FakeComposeHostCaptureRasterizer()
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(screenDensity = 2.625f))!!

        // Then
        assertThat(result.wireframes.single().bounds).isEqualTo(CapturedBounds(2, 2, 4, 4))
    }

    @ParameterizedTest
    @CsvSource("5,2,16,40", "27,10,20,54")
    fun `M register padded resource and retain original layer W decompose() { thin drawing }`(
        heightPx: Int,
        heightDp: Long,
        paddedHeightDp: Long,
        paddedHeightPx: Int
    ) {
        // Given
        val node = mockDrawingEffectNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(node.boundsInRoot).thenReturn(
            androidx.compose.ui.geometry.Rect(42f, 508f, 1038f, (508 + heightPx).toFloat())
        )
        val source: Bitmap = mock()
        val padded: Bitmap = mock()
        whenever(source.width).thenReturn(996)
        whenever(source.height).thenReturn(heightPx)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = FakeComposeHostCaptureRasterizer { listOf(source) }
        )
        val registered = mutableListOf<PendingPixelCapture>()
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> {
                Bitmap.createBitmap(996, paddedHeightPx, Bitmap.Config.ARGB_8888)
            }.thenReturn(padded)
            mockConstruction(Canvas::class.java).use { _ ->
                // When
                val result = testedDecomposer.decompose(
                    mockView,
                    realRequest(
                        screenDensity = 2.625f,
                        pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it }
                    )
                )!!

                // Then
                val pixel = result.wireframes.single() as CapturedWireframe.Pixel
                assertThat(pixel.bounds).isEqualTo(CapturedBounds(16, 194, 379, paddedHeightDp))
                assertThat(pixel.clip).isEqualTo(CapturedClip(bottom = paddedHeightDp - heightDp))
                assertThat(result.nodes.single().bounds).isEqualTo(CapturedBounds(16, 194, 379, heightDp))
                assertThat(registered.single().bitmap).isSameAs(padded)
                assertThat(registered.single().isTextFree).isFalse()
                assertThat(registered.single().isSourceImage).isFalse()
                verify(source).recycle()
                verify(padded, never()).recycle()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M register a pending capture W decompose() { leaf without a resolvable shape }`(
        unresolvedBackground: Boolean
    ) {
        // Given
        val node = mockLeafNode()
        if (unresolvedBackground) {
            val background: Modifier = mock()
            val info: ModifierInfo = mock()
            whenever(info.modifier).thenReturn(background)
            whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(info))
            whenever(mockReflectionUtils.isBackgroundElement(background)).thenReturn(true)
        }
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(1)
        assertThat(result!!.wireframes.single()).isInstanceOf(CapturedWireframe.Pixel::class.java)
    }

    @Test
    fun `M call the rasterizer exactly once W decompose() { two capture candidates under one host }`() {
        // Given - the shared-draw batching guarantee: repeated View#draw on the same Compose host
        // within one cycle can corrupt a stateful Painter's internal state, so this must never be N calls.
        val nodes = listOf(mockLeafNode(), mockLeafNode())
        val root = mockRoot(nodes)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(1)
        assertThat(fakeRasterizer.lastRegions).hasSize(2)
        assertThat(result!!.wireframes).hasSize(2)
    }

    @Test
    fun `M emit a placeholder without registering a capture W decompose() { privacy denies }`(
        @StringForgery fakeLabel: String
    ) {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )
        val registered = mutableListOf<PendingPixelCapture>()

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(
                pixelCapturePlaceholderLabelFor = { fakeLabel },
                pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it }
            )
        )

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(0)
        assertThat(registered).isEmpty()
        val placeholder = result!!.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.label).isEqualTo(fakeLabel)
    }

    @Test
    fun `M emit a placeholder W decompose() { rasterizer returns null for the region }`() {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer(bitmapsToReturn = { regions -> regions.map { null } })
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M abort before the batched draw W decompose() { shouldContinue denies right before capture }`() {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer,
            nodesPerCheckpoint = 1000 // never trips mid-walk; only the pre-capture check should fire
        )

        // When - denies every call, so the walk's own single node still passes (checkpoint never
        // reached with only 1 node) but the dedicated pre-capture re-check catches it.
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { false }))

        // Then
        assertThat(result).isNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(0)
    }

    // endregion

    @Test
    fun `M discard partial tree W decompose() { checkpoint denies after a completed node }`() {
        // Given
        val root = mockRoot(List(3) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 2
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { false }))

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils, never()).resolveInnerBounds(eq(root.children[2]), any())
    }

    @Test
    fun `M preserve text size and color W decompose() { adjacent text shares and changes styles }`() {
        // Given
        val fakeText = TextLayoutInfo("First", Color.Blue.value, 12L, null)
        val fakeTexts = listOf(
            fakeText,
            fakeText.copy(text = "Second"),
            fakeText.copy(text = "Third", color = Color.Red.value),
            fakeText.copy(text = "Fourth", fontSize = 18L),
            fakeText.copy(text = "Fifth")
        )
        val stubNodes = fakeTexts.map { info ->
            mockLeafNode().also { node ->
                whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any())).thenReturn(info)
            }
        }
        val stubRoot = mockRoot(stubNodes)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        val texts = result?.wireframes?.filterIsInstance<CapturedWireframe.Text>()
        assertThat(texts?.map { it.text }).isEqualTo(fakeTexts.map { it.text })
        assertThat(texts?.map { it.textStyle.size }).containsExactly(12L, 12L, 12L, 18L, 12L)
        val blue = DefaultColorStringFormatter.formatColorAsHexString(Color.Blue.toArgb())
        val red = DefaultColorStringFormatter.formatColorAsHexString(Color.Red.toArgb())
        assertThat(texts?.map { it.textStyle.color }).containsExactly(blue, blue, red, blue, blue)
    }

    @Test
    fun `M resolve colors afresh W decompose() { capture changes with repeated styles }`() {
        // Given
        val fakeText = TextLayoutInfo("Current", Color.Blue.value, 12L, null)
        val stubNodes = List(2) { mockLeafNode() }
        stubNodes.forEach { node ->
            whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any(), any())).thenReturn(fakeText)
        }
        val stubRoot = mockRoot(stubNodes)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        val mockFormatter: ColorStringFormatter = mock()
        whenever(mockFormatter.formatColorAndAlphaAsHexString(any(), any())).thenReturn("first", "second")
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            colorStringFormatter = mockFormatter,
            compatibilityGate = testedGate
        )

        // When
        val first = testedDecomposer.decompose(mockView, realRequest())
        val second = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(first?.wireframes?.filterIsInstance<CapturedWireframe.Text>()?.map { it.textStyle.color })
            .containsExactly("first", "first")
        assertThat(second?.wireframes?.filterIsInstance<CapturedWireframe.Text>()?.map { it.textStyle.color })
            .containsExactly("second", "second")
    }

    @ParameterizedTest
    @CsvSource("true,true,16.0", "true,false,0.0", "false,true,0.0")
    fun `M preserve equivalent rounded background W decompose() { flat wireframes }`(
        clipEnabled: Boolean,
        sameBounds: Boolean,
        expectedRadius: Double
    ) {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        val shape: Shape = mock()
        val clip = mockGraphicsLayerModifier(mapOf("clip" to clipEnabled, "shape" to shape))
        val background: Modifier = mock()
        val clipInfo: ModifierInfo = mock()
        val backgroundInfo: ModifierInfo = mock()
        whenever(clipInfo.modifier).thenReturn(clip)
        whenever(backgroundInfo.modifier).thenReturn(background)
        val modifiers = listOf(clipInfo, backgroundInfo)
        whenever(mockReflectionUtils.isBackgroundElement(background)).thenReturn(true)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(modifiers)
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 100, 40))
        whenever(mockSemanticsUtils.resolveModifierBounds(eq(node), eq(clipInfo), any()))
            .thenReturn(GlobalBounds(if (sameBounds) 0 else 8, 0, 100, 40))
        whenever(mockSemanticsUtils.resolveBackgroundColor(modifiers)).thenReturn(Color.Black.value.toLong())
        whenever(
            mockSemanticsUtils.resolveBackgroundColor(listOf(backgroundInfo))
        ).thenReturn(Color.Black.value.toLong())
        whenever(mockSemanticsUtils.resolveCornerRadius(eq(shape), any(), any())).thenReturn(16f)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())!!

        // Then
        val backgroundShape = result.wireframes.filterIsInstance<CapturedWireframe.Shape>().single()
        assertThat(backgroundShape.style?.cornerRadius ?: 0.0).isEqualTo(expectedRadius)
    }

    // region clip modifier

    /**
     * A `graphicsLayer` modifier exposing [properties] through [InspectableValue.inspectableElements] -
     * the same public mechanism the real `GraphicsLayerElement` uses (see
     * `GranularComposeDecomposer.resolveGraphicsLayerModifiers`'s doc), not reflection.
     */
    private fun mockGraphicsLayerModifier(properties: Map<String, Any?>): Modifier {
        val modifier = mock<Modifier>(extraInterfaces = arrayOf(InspectableValue::class))
        val inspectable = modifier as InspectableValue
        whenever(inspectable.nameFallback).thenReturn("graphicsLayer")
        whenever(inspectable.inspectableElements).thenReturn(
            properties.map { (name, value) -> ValueElement(name, value) }.asSequence()
        )
        return modifier
    }

    /** A node with one child (so it isn't treated as a pixel-capture candidate) and a `graphicsLayer` modifier exposing [clipEnabled]/[shape]. */
    private fun mockClippableNode(clipEnabled: Boolean, shape: Shape?): SemanticsNode {
        val node = mockLeafNode()
        val childNode = mockLeafNode()
        val graphicsLayerModifier = mockGraphicsLayerModifier(mapOf("clip" to clipEnabled, "shape" to shape))
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(graphicsLayerModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(node.children).thenReturn(listOf(childNode))
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 100, 50))
        return node
    }

    @Test
    fun `M emit a Clip modifier W decompose() { graphicsLayer clip enabled with a rounded shape }`() {
        // Given
        val fakeShape: Shape = mock()
        val node = mockClippableNode(clipEnabled = true, shape = fakeShape)
        whenever(mockSemanticsUtils.resolveCornerRadius(eq(fakeShape), any(), any())).thenReturn(10f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val clippedLayer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val clip = clippedLayer.modifiers.filterIsInstance<CapturedModifier.Clip>().single()
        assertThat(clip.path).isEqualTo(
            "M 10.0,0 L 90.0,0 A 10.0,10.0 0 0 1 100.0,10.0 L 100.0,40.0 A 10.0,10.0 0 0 1 90.0,50.0 " +
                "L 10.0,50.0 A 10.0,10.0 0 0 1 0,40.0 L 0,10.0 A 10.0,10.0 0 0 1 10.0,0 Z"
        )
    }

    @Test
    fun `M not emit a Clip modifier W decompose() { graphicsLayer shape set but clip disabled }`() {
        // Given: a shape without clip=true is only used for the shadow outline, not to actually
        // clip content - emitting a Clip modifier here would be wrong, not just unnecessary.
        val fakeShape: Shape = mock()
        val node = mockClippableNode(clipEnabled = false, shape = fakeShape)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val clippedLayer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(clippedLayer.modifiers.filterIsInstance<CapturedModifier.Clip>()).isEmpty()
    }

    @Test
    fun `M not emit a Clip modifier W decompose() { clip enabled but shape resolves to no rounding }`() {
        // Given: a plain rectangular clip has no visual effect the existing per-wireframe
        // ancestor-bounds crop doesn't already provide - not worth a layer-level modifier for it.
        val fakeShape: Shape = mock()
        val node = mockClippableNode(clipEnabled = true, shape = fakeShape)
        whenever(mockSemanticsUtils.resolveCornerRadius(eq(fakeShape), any(), any())).thenReturn(0f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val clippedLayer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(clippedLayer.modifiers.filterIsInstance<CapturedModifier.Clip>()).isEmpty()
    }

    // endregion

    // region shadow modifier

    /** A node with one child (so it isn't treated as a pixel-capture candidate) and a `graphicsLayer` modifier exposing [elevationPx]/[spotColor]. */
    private fun mockShadowNode(elevationPx: Float?, spotColor: Color?): SemanticsNode {
        val node = mockLeafNode()
        val childNode = mockLeafNode()
        val graphicsLayerModifier = mockGraphicsLayerModifier(
            mapOf("shadowElevation" to elevationPx, "spotShadowColor" to spotColor)
        )
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(graphicsLayerModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(node.children).thenReturn(listOf(childNode))
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 100, 50))
        return node
    }

    @Test
    fun `M emit a Shadow modifier W decompose() { graphicsLayer has positive elevation }`() {
        // Given: elevation 4px at density 1 rounds to the Material elevation table's 4dp row -
        // (offsetY=2, blur=4) for the key/umbra layer.
        val node = mockShadowNode(elevationPx = 4f, spotColor = Color.Black)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val shadow = layer.modifiers.filterIsInstance<CapturedModifier.Shadow>().single()
        assertThat(shadow.offsetX).isEqualTo(0.0)
        assertThat(shadow.offsetY).isEqualTo(2.0)
        assertThat(shadow.radius).isEqualTo(4.0)
        val expectedAlpha = (Color.Black.alpha * 0.2f * 255).roundToInt()
        assertThat(shadow.color).isEqualTo(
            DefaultColorStringFormatter.formatColorAndAlphaAsHexString(Color.Black.toArgb(), expectedAlpha)
        )
    }

    @Test
    fun `M default the shadow color to black W decompose() { spotShadowColor not resolvable }`() {
        // Given
        val node = mockShadowNode(elevationPx = 4f, spotColor = null)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val shadow = layer.modifiers.filterIsInstance<CapturedModifier.Shadow>().single()
        val expectedAlpha = (Color.Black.alpha * 0.2f * 255).roundToInt()
        assertThat(shadow.color).isEqualTo(
            DefaultColorStringFormatter.formatColorAndAlphaAsHexString(Color.Black.toArgb(), expectedAlpha)
        )
    }

    @Test
    fun `M not emit a Shadow modifier W decompose() { elevation is zero }`() {
        // Given: a flat node (no Z) casts no shadow.
        val node = mockShadowNode(elevationPx = 0f, spotColor = Color.Black)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.Shadow>()).isEmpty()
    }

    @Test
    fun `M not emit a Shadow modifier W decompose() { elevation not resolvable }`() {
        // Given
        val node = mockShadowNode(elevationPx = null, spotColor = Color.Black)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.Shadow>()).isEmpty()
    }

    // endregion

    // region blur modifier

    /** A node with one child (so it isn't treated as a pixel-capture candidate) and a `graphicsLayer` modifier exposing [renderEffect]. */
    private fun mockBlurNode(renderEffect: RenderEffect?): SemanticsNode {
        val node = mockLeafNode()
        val childNode = mockLeafNode()
        val graphicsLayerModifier = mockGraphicsLayerModifier(mapOf("renderEffect" to renderEffect))
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(graphicsLayerModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(node.children).thenReturn(listOf(childNode))
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 100, 50))
        return node
    }

    @Test
    fun `M emit a GaussianBlur modifier W decompose() { graphicsLayer has a uniform BlurEffect }`() {
        // Given
        val fakeBlurEffect: BlurEffect = mock()
        val node = mockBlurNode(fakeBlurEffect)
        whenever(mockReflectionUtils.getBlurRadii(fakeBlurEffect)).thenReturn(8f to 8f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val blur = layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>().single()
        assertThat(blur.radius).isEqualTo(8.0)
    }

    @Test
    fun `M not emit a GaussianBlur modifier W decompose() { radiusX and radiusY differ }`() {
        // Given: CapturedModifier.GaussianBlur has a single radius - an elliptical blur has no
        // faithful representation, so it's left unrecognized rather than guessed at.
        val fakeBlurEffect: BlurEffect = mock()
        val node = mockBlurNode(fakeBlurEffect)
        whenever(mockReflectionUtils.getBlurRadii(fakeBlurEffect)).thenReturn(8f to 4f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>()).isEmpty()
    }

    @Test
    fun `M not emit a GaussianBlur modifier W decompose() { renderEffect is not a BlurEffect }`() {
        // Given: e.g. an OffsetEffect - no CapturedModifier equivalent.
        val fakeRenderEffect: RenderEffect = mock()
        val node = mockBlurNode(fakeRenderEffect)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>()).isEmpty()
    }

    @Test
    fun `M not emit a GaussianBlur modifier W decompose() { reflection fails to resolve radii }`() {
        // Given
        val fakeBlurEffect: BlurEffect = mock()
        val node = mockBlurNode(fakeBlurEffect)
        whenever(mockReflectionUtils.getBlurRadii(fakeBlurEffect)).thenReturn(null)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>()).isEmpty()
    }

    // endregion

    // region color matrix modifier
    //
    // resolveColorMatrixModifier/resolveSaturateValue/resolveBrightnessValue are plain top-level
    // functions taking a raw FloatArray - no mocking, no fakes, no decompose()-level integration
    // needed. The only part of this feature that isn't unit-tested is platformColorMatrixValues' few
    // lines of real Compose→Android ColorFilter interop, which unitTests.isReturnDefaultValues=true
    // makes impossible to observe meaningfully in this environment regardless of how it's structured
    // - see that function's own doc.

    @Test
    fun `M return null W resolveColorMatrixModifier { values is null }`() {
        assertThat(resolveColorMatrixModifier(null)).isNull()
    }

    @Test
    fun `M return a ColorMatrix modifier W resolveColorMatrixModifier { values is not a saturation matrix }`() {
        // Given: an arbitrary, deterministic 20-value matrix that doesn't match the saturation shape.
        val fakeValues = FloatArray(20) { it.toFloat() }

        // When
        val modifier = resolveColorMatrixModifier(fakeValues)

        // Then
        assertThat(modifier).isEqualTo(CapturedModifier.ColorMatrix(fakeValues.map { it.toDouble() }))
    }

    @Test
    fun `M return a Saturate modifier W resolveColorMatrixModifier { values is a saturation matrix }`() {
        // Given: ColorMatrix.setToSaturation is real, non-Android-stubbed Compose Kotlin code, so
        // it behaves correctly (and testably) even under this module's unit test setup.
        val fakeSaturation = 0.4f
        val fakeValues = ColorMatrix().apply { setToSaturation(fakeSaturation) }.values

        // When
        val modifier = resolveColorMatrixModifier(fakeValues)

        // Then
        val saturate = modifier as CapturedModifier.Saturate
        assertThat(saturate.value).isCloseTo(fakeSaturation.toDouble(), within(0.001))
    }

    @Test
    fun `M return null W resolveSaturateValue { values has the wrong size }`() {
        assertThat(resolveSaturateValue(FloatArray(4))).isNull()
    }

    @Test
    fun `M return null W resolveSaturateValue { values does not match the saturation shape }`() {
        assertThat(resolveSaturateValue(FloatArray(20) { it.toFloat() })).isNull()
    }

    @Test
    fun `M return a BrightnessBias modifier W resolveColorMatrixModifier { values is a brightness matrix }`(
        @FloatForgery(min = -1f, max = 1f) fakeBrightness: Float
    ) {
        // Given
        val fakeValues = brightnessMatrix(fakeBrightness, alphaOffset = 0f)

        // When
        val modifier = resolveColorMatrixModifier(fakeValues)

        // Then
        val brightnessBias = modifier as CapturedModifier.BrightnessBias
        assertThat(brightnessBias.value).isCloseTo(fakeBrightness.toDouble(), within(0.001))
    }

    @Test
    fun `M return null W resolveBrightnessValue { values has the wrong size }`() {
        assertThat(resolveBrightnessValue(FloatArray(4))).isNull()
    }

    @Test
    fun `M return null W resolveBrightnessValue { values does not match the brightness shape }`() {
        assertThat(resolveBrightnessValue(FloatArray(20) { it.toFloat() })).isNull()
    }

    @Test
    fun `M return null W resolveBrightnessValue { alpha translation is non-zero }`(
        @FloatForgery(min = -1f, max = 1f) fakeBrightness: Float,
        @FloatForgery(min = 1f, max = 10f) fakeAlphaOffset: Float
    ) {
        // Given
        val fakeValues = brightnessMatrix(fakeBrightness, alphaOffset = fakeAlphaOffset)

        // When + Then
        assertThat(resolveBrightnessValue(fakeValues)).isNull()
    }

    /** Matches the production identity-plus-translation brightness matrix shape. */
    private fun brightnessMatrix(brightness: Float, alphaOffset: Float): FloatArray {
        val offset = brightness * 255f
        return floatArrayOf(
            1f, 0f, 0f, 0f, offset,
            0f, 1f, 0f, 0f, offset,
            0f, 0f, 1f, 0f, offset,
            0f, 0f, 0f, 1f, alphaOffset
        )
    }

    // endregion

    private class FakeCompositionIdentityFactory : CompositionIdentityFactory {
        private var nextWireId = 0L
        private val scope = RumViewIdentityScope("fake-scope")

        private fun next(kind: CapturedIdentityKind, wireframeKind: CapturedWireframeKind? = null): CapturedIdentity =
            CapturedIdentity(
                scope = scope,
                kind = kind,
                wireframeKind = wireframeKind,
                namespace = emptyList(),
                localId = nextWireId.toString(),
                wireId = nextWireId++
            )

        fun anyIdentity(): CapturedIdentity = next(CapturedIdentityKind.VIEW)

        override fun composeHost(window: CapturedIdentity, hostId: String) = next(CapturedIdentityKind.COMPOSE_HOST)
        override fun composeNode(host: CapturedIdentity, nodeId: String) = next(CapturedIdentityKind.COMPOSE_NODE)
        override fun shapeWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.SHAPE)
        override fun textWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.TEXT)
        override fun placeholderWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.PLACEHOLDER)
        override fun imageWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.IMAGE)
    }
}
