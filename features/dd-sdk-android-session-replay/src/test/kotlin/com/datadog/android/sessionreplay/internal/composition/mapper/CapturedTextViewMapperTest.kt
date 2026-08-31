/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.Layout
import android.util.DisplayMetrics
import android.view.Gravity
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedPadding
import com.datadog.android.internal.sessionreplay.composition.CapturedTextPosition
import com.datadog.android.internal.sessionreplay.composition.CapturedVerticalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCaptureSink
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedTextViewMapperTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockColorStringFormatter: ColorStringFormatter = mock()
    private val mockDrawableToColorMapper: DrawableToColorMapper = mock()
    private val mockInternalLogger: InternalLogger = mock()
    private val mockBitmap: Bitmap = mock()
    private val mockIconBitmap: Bitmap = mock()
    private val testedMapper = CapturedTextViewMapper<TextView>(
        viewBoundsResolver = mockViewBoundsResolver,
        colorStringFormatter = mockColorStringFormatter,
        backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(
            mockColorStringFormatter,
            mockDrawableToColorMapper
        ),
        backgroundRasterizer = ViewBackgroundRasterizer { mockBitmap },
        compoundDrawableRasterizer = CompoundDrawableRasterizer { _, _, _ -> mockIconBitmap },
        internalLogger = mockInternalLogger
    )

    @ParameterizedTest
    @CsvSource(
        "true, false, 0, #11223300",
        "true, false, 128, #11223380",
        "true, false, 255, #112233ff",
        "false, true, 0, #11223300",
        "false, true, 128, #11223380",
        "false, true, 255, #112233ff",
        "false, false, 128, #11223380"
    )
    fun `M preserve active text or hint color alpha W map`(
        fakeHasText: Boolean,
        fakeHasHintColors: Boolean,
        fakeAlpha: Int,
        fakeExpectedColor: String
    ) {
        // Given: current state-list colors differ from the default, and View.alpha belongs to the layer.
        val mockTextView = textView()
        val fakeColor = (fakeAlpha shl 24) or 0x112233
        whenever(mockTextView.text).thenReturn(if (fakeHasText) "text" else "")
        whenever(mockTextView.alpha).thenReturn(0.5f)
        whenever(mockTextView.currentTextColor).thenReturn(fakeColor)
        whenever(mockTextView.currentHintTextColor).thenReturn(fakeColor)
        if (fakeHasHintColors) {
            val stubColors: ColorStateList = mock()
            whenever(stubColors.defaultColor).thenReturn(0xff445566.toInt())
            whenever(mockTextView.hintTextColors).thenReturn(stubColors)
        }
        val testedColorMapper = CapturedTextViewMapper<TextView>(
            viewBoundsResolver = mockViewBoundsResolver,
            internalLogger = mockInternalLogger
        )
        val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
        val fakeOwner = fakeFactory.view(fakeFactory.window("window"), "text")

        // When: use the real color formatter.
        val result = testedColorMapper.map(
            mockTextView,
            CapturedMappingContext(fakeFactory, fakeOwner, 1f)
        ) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Text>().single().textStyle.color)
            .isEqualTo(fakeExpectedColor)
    }

    private fun mappingContext(
        fakeScope: String,
        fakeDensity: Float,
        imagePrivacy: ImagePrivacy = ImagePrivacy.MASK_NONE,
        sink: PendingPixelCaptureSink = PendingPixelCaptureSink.NoOp
    ): CapturedMappingContext {
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "text-owner")
        return CapturedMappingContext(
            factory,
            owner,
            screenDensity = fakeDensity,
            imagePrivacy = imagePrivacy,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            pendingPixelCaptureSink = sink
        )
    }

    private fun stubTextView(
        fakeText: String,
        fakeTextColor: Int,
        fakeColorHexString: String,
        fakeDensity: Float,
        fakeBounds: GlobalBounds,
        width: Int = 100,
        height: Int = 100
    ): TextView {
        val mockTextView: TextView = mock()
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockTextView.width).thenReturn(width)
        whenever(mockTextView.height).thenReturn(height)
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeTextColor))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        return mockTextView
    }

    @Test
    fun `M build exclusively from copied text and style W live view changes after capture`(
        @Forgery fakeContext: CapturedMappingContext
    ) {
        // Given
        val mockTextView: TextView = mock()
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        val fakeText = StringBuilder("original")
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.currentTextColor).thenReturn(0x112233)
        whenever(mockTextView.textSize).thenReturn(20f)
        whenever(mockTextView.paddingLeft).thenReturn(4)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        whenever(mockColorStringFormatter.formatColorAsHexString(0x112233))
            .thenReturn("#112233FF")
        val fakeMappingContext = fakeContext.copy(
            screenDensity = 1f,
            bounds = null,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )
        val copied = testedMapper.capture(mockTextView, fakeMappingContext)
        fakeText.replace(0, fakeText.length, "changed")
        whenever(mockTextView.currentTextColor).thenReturn(0xFFFFFF)
        whenever(mockTextView.paddingLeft).thenReturn(40)
        clearInvocations(mockTextView, mockViewBoundsResolver)

        // When
        val result = copied.map(fakeMappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        verifyNoInteractions(mockTextView, mockViewBoundsResolver)
        val text = result.wireframes
            .filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(text.text).isEqualTo("original")
        assertThat(text.textStyle.color).isEqualTo("#112233FF")
        assertThat(text.textStyle.size).isEqualTo(20L)
        assertThat(checkNotNull(checkNotNull(text.textPosition).padding).left).isEqualTo(4L)
    }

    @Test
    fun `M capture raw unmasked text W map()`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView: TextView = mock()
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.background).thenReturn(null)
        whenever(mockTextView.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeTextColor))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "text-owner")
        val mappingContext = CapturedMappingContext(
            factory,
            owner,
            screenDensity = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val textWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(textWireframe.text).isEqualTo(fakeText)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeColorHexString)
        assertThat(textWireframe.bounds.x).isEqualTo(fakeBounds.x)
    }

    @Test
    fun `M not emit a Shape wireframe W map { no background }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView: TextView = mock()
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.background).thenReturn(null)
        whenever(mockTextView.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeTextColor))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "text-owner")
        val mappingContext = CapturedMappingContext(
            factory,
            owner,
            screenDensity = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>()).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
        "TEXT_START, 0, -1, RIGHT",
        "TEXT_END, 0, -1, LEFT",
        "TEXT_START, 1, 1, LEFT",
        "TEXT_END, 1, 1, RIGHT",
        "VIEW_START, 0, -1, LEFT",
        "VIEW_END, 0, -1, RIGHT",
        "VIEW_START, 1, 1, RIGHT",
        "VIEW_END, 1, 1, LEFT"
    )
    fun `M distinguish paragraph and view directions W map relative alignment`(
        fakeAlignment: RelativeAlignment,
        fakeLayoutDirection: Int,
        fakeParagraphDirection: Int,
        fakeExpected: CapturedHorizontalAlignment
    ) {
        // Given: paragraph and layout directions deliberately disagree.
        val mockTextView = textView()
        val mockLayout: Layout = mock()
        whenever(mockLayout.lineCount).thenReturn(1)
        whenever(mockLayout.getParagraphDirection(0)).thenReturn(fakeParagraphDirection)
        whenever(mockTextView.layout).thenReturn(mockLayout)
        whenever(mockTextView.layoutDirection).thenReturn(fakeLayoutDirection)
        whenever(mockTextView.textAlignment).thenReturn(fakeAlignment.value)

        // When / Then
        assertThat(captureAlignment(mockTextView).horizontal).isEqualTo(fakeExpected)
    }

    @ParameterizedTest
    @CsvSource("START, RIGHT", "END, LEFT", "LEFT, LEFT", "RIGHT, RIGHT", "CENTER_HORIZONTAL, CENTER")
    fun `M resolve relative gravity and retain absolute gravity W RTL paragraph`(
        fakeGravity: HorizontalGravity,
        fakeExpected: CapturedHorizontalAlignment
    ) {
        // Given: gravity START/END follows the paragraph, not the LTR view direction.
        val mockTextView = textView()
        val mockLayout: Layout = mock()
        whenever(mockLayout.lineCount).thenReturn(1)
        whenever(mockLayout.getParagraphDirection(0)).thenReturn(-1)
        whenever(mockTextView.layout).thenReturn(mockLayout)
        whenever(mockTextView.textAlignment).thenReturn(TextView.TEXT_ALIGNMENT_GRAVITY)
        whenever(mockTextView.gravity).thenReturn(fakeGravity.value or Gravity.BOTTOM)

        // When / Then
        assertThat(
            captureAlignment(mockTextView)
        ).isEqualTo(CapturedAlignment(fakeExpected, CapturedVerticalAlignment.BOTTOM))
    }

    @ParameterizedTest
    @CsvSource(
        "שלום, 0, 1, RIGHT",
        "مرحبا, 0, 1, RIGHT",
        "hello, 1, 1, LEFT",
        "hello, 0, 4, RIGHT",
        "שלום, 1, 3, LEFT",
        "123, 1, 1, RIGHT",
        "123, 0, 1, LEFT"
    )
    fun `M resolve text direction W no text layout exists`(
        fakeText: String,
        fakeLayoutDirection: Int,
        fakeTextDirection: Int,
        fakeExpected: CapturedHorizontalAlignment
    ) {
        // Given: first-strong direction, forced RTL/LTR, and neutral text with layout fallback.
        val mockTextView = textView()
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.layoutDirection).thenReturn(fakeLayoutDirection)
        whenever(mockTextView.textDirection).thenReturn(fakeTextDirection)
        whenever(mockTextView.textAlignment).thenReturn(TextView.TEXT_ALIGNMENT_TEXT_START)

        // When / Then
        assertThat(captureAlignment(mockTextView).horizontal).isEqualTo(fakeExpected)
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `M preserve physical horizontal insets W asymmetric padding in LTR or RTL`(
        fakeHasLayout: Boolean,
        fakeIsRtl: Boolean
    ) {
        // Given: compound drawables add to total padding; RTL reverses start/end, not left/right.
        val mockTextView = textView()
        whenever(
            mockTextView.layoutDirection
        ).thenReturn(if (fakeIsRtl) TextView.LAYOUT_DIRECTION_RTL else TextView.LAYOUT_DIRECTION_LTR)
        whenever(mockTextView.paddingLeft).thenReturn(10)
        whenever(mockTextView.paddingRight).thenReturn(20)
        whenever(mockTextView.paddingStart).thenReturn(if (fakeIsRtl) 20 else 10)
        whenever(mockTextView.paddingEnd).thenReturn(if (fakeIsRtl) 10 else 20)
        whenever(mockTextView.totalPaddingLeft).thenReturn(14)
        whenever(mockTextView.totalPaddingRight).thenReturn(26)
        whenever(mockTextView.totalPaddingStart).thenReturn(if (fakeIsRtl) 26 else 14)
        whenever(mockTextView.totalPaddingEnd).thenReturn(if (fakeIsRtl) 14 else 26)
        if (fakeHasLayout) whenever(mockTextView.layout).thenReturn(mock<Layout>())
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
        val owner = factory.view(factory.window("window"), "text")

        // When
        val result = testedMapper.map(
            mockTextView,
            CapturedMappingContext(factory, owner, 1f)
        ) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Text>().single().textPosition?.padding)
            .isEqualTo(CapturedPadding(0, 0, if (fakeHasLayout) 14 else 10, if (fakeHasLayout) 26 else 20))
    }

    @ParameterizedTest
    @CsvSource(
        "48, TOP, false",
        "80, BOTTOM, false",
        "16, CENTER, false",
        "48, TOP, true",
        "80, BOTTOM, true",
        "16, CENTER, true"
    )
    fun `M preserve vertical placement W map non-gravity text alignment`(
        fakeGravity: Int,
        fakeExpectedGravity: CapturedVerticalAlignment,
        fakeHasLayout: Boolean
    ) {
        RelativeAlignment.entries.forEach { fakeAlignment ->
            // Given: ordinary padding excludes the gravity offsets supplied by a completed layout.
            val mockTextView = textView()
            whenever(mockTextView.textAlignment).thenReturn(fakeAlignment.value)
            whenever(mockTextView.gravity).thenReturn(fakeGravity)
            whenever(mockTextView.paddingTop).thenReturn(3)
            whenever(mockTextView.paddingBottom).thenReturn(5)
            whenever(mockTextView.totalPaddingTop).thenReturn(11)
            whenever(mockTextView.totalPaddingBottom).thenReturn(17)
            if (fakeHasLayout) whenever(mockTextView.layout).thenReturn(mock<Layout>())

            // When
            val fakePosition = captureTextPosition(mockTextView)

            // Then: a layout retains its total padding; the fallback needs explicit vertical gravity.
            assertThat(fakePosition.padding).isEqualTo(
                if (fakeHasLayout) CapturedPadding(11, 17, 0, 0) else CapturedPadding(3, 5, 0, 0)
            )
            assertThat(fakePosition.alignment?.vertical).isEqualTo(
                if (fakeHasLayout) CapturedVerticalAlignment.CENTER else fakeExpectedGravity
            )
        }
    }

    private fun textView(): TextView {
        val mockTextView: TextView = mock()
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockTextView.text).thenReturn("text")
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, 1f)
        ).thenReturn(GlobalBounds(0, 0, 100, 40))
        whenever(mockColorStringFormatter.formatColorAsHexString(any())).thenReturn("#000000FF")
        return mockTextView
    }

    private fun captureAlignment(view: TextView): CapturedAlignment =
        requireNotNull(captureTextPosition(view).alignment)

    private fun captureTextPosition(view: TextView): CapturedTextPosition {
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
        val owner = factory.view(factory.window("window"), "text")
        val result = testedMapper.map(
            view,
            CapturedMappingContext(factory, owner, 1f)
        ) as CapturedViewMapperResult.Wireframes
        val text = result.wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        return requireNotNull(text.textPosition)
    }

    enum class RelativeAlignment(val value: Int) {
        TEXT_START(TextView.TEXT_ALIGNMENT_TEXT_START),
        TEXT_END(TextView.TEXT_ALIGNMENT_TEXT_END),
        VIEW_START(TextView.TEXT_ALIGNMENT_VIEW_START),
        VIEW_END(TextView.TEXT_ALIGNMENT_VIEW_END),
        CENTER(TextView.TEXT_ALIGNMENT_CENTER)
    }

    enum class HorizontalGravity(val value: Int) {
        START(
            Gravity.START
        ), END(Gravity.END), LEFT(Gravity.LEFT), RIGHT(Gravity.RIGHT), CENTER_HORIZONTAL(Gravity.CENTER_HORIZONTAL)
    }

    @Test
    fun `M emit a Shape wireframe and skip pixel capture W map { background resolves to a solid color }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeRgb: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeBackgroundColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given: an opaque, solid resolvable background - a plain non-Material button background,
        // for instance - always wins over a pixel capture, since it's cheaper and never
        // privacy-sensitive.
        val fakeSolidColor = fakeRgb or (0xFF shl 24)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity, fakeBounds)
        val mockDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger))
            .thenReturn(fakeSolidColor)
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeSolidColor))
            .thenReturn(fakeBackgroundColorHexString)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val shapeWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Shape>().single()
        assertThat(shapeWireframe.style?.backgroundColor).isEqualTo(fakeBackgroundColorHexString)
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(registered).isEmpty()
    }

    @Test
    fun `M register a pending capture and emit a Pixel wireframe W map { non-solid background }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given: a ripple/selector/stateful Material button background can't reduce to one color -
        // this is exactly the fidelity gap the pixel-capture fallback exists to close.
        val mockTextView = stubTextView(
            fakeText,
            fakeTextColor,
            fakeTextColorHexString,
            fakeDensity,
            fakeBounds,
            width = 100,
            height = 100
        )
        val mockDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger)).thenReturn(null)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
        assertThat(pixelWireframe.resource).isEqualTo(PixelResource.Unresolved)
        assertThat(registered).hasSize(1)
        assertThat(registered.single().wireframeIdentity).isEqualTo(pixelWireframe.identity)
        assertThat(registered.single().bitmap).isSameAs(mockBitmap)
        // Only the background drawable is rasterized here, never the view's text (captured
        // separately as its own CapturedWireframe.Text) - so this capture is structurally
        // guaranteed not to contain text, and must skip the text-detection safety net entirely.
        assertThat(registered.single().isTextFree).isTrue()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>()).isEmpty()
    }

    @Test
    fun `M register a pending capture W map { non-solid background, MASK_LARGE_ONLY privacy }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float
    ) {
        // Given: MASK_LARGE_ONLY's size-based "large enough to be suspected PII" heuristic exists
        // for genuine image content - a button's own decorative background is never PII, so it
        // must still be captured regardless of how large the button is. Bounds are deliberately
        // far larger than IMAGE_DIMEN_CONSIDERED_PII_IN_DP to prove size alone doesn't matter here.
        val fakeLargeBounds = GlobalBounds(0, 0, 2000, 2000)
        val mockTextView = stubTextView(
            fakeText,
            fakeTextColor,
            fakeTextColorHexString,
            fakeDensity,
            fakeLargeBounds,
            width = 100,
            height = 100
        )
        val mockDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger)).thenReturn(null)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
        assertThat(pixelWireframe.resource).isEqualTo(PixelResource.Unresolved)
        assertThat(registered).hasSize(1)
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).isEmpty()
    }

    @Test
    fun `M drop the background without rasterizing W map { non-solid background, MASK_ALL privacy }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given: a PrivacyPlaceholder would occupy the exact same bounds as the Text wireframe
        // below, which is always emitted regardless (masked separately, by TextAndInputPrivacy) -
        // stacking a masked-content label under deliberately-unmasked text would be nonsensical, so
        // this drops the background entirely instead, same as legacy does for a background it
        // can't rasterize either.
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity, fakeBounds)
        val mockDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger)).thenReturn(null)
        val mappingContext = mappingContext(fakeScope, fakeDensity, imagePrivacy = ImagePrivacy.MASK_ALL)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Text>()).hasSize(1)
        verifyNoInteractions(mockBitmap)
    }

    @Test
    fun `M emit nothing extra W map { non-solid background too large to capture }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given: 1000x1000 screen, 8x cap = 8,000,000px - this view is 12,000,000px.
        val mockTextView = stubTextView(
            fakeText,
            fakeTextColor,
            fakeTextColorHexString,
            fakeDensity,
            fakeBounds,
            width = 4000,
            height = 3000
        )
        val mockDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger)).thenReturn(null)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val mappingContext = mappingContext(fakeScope, fakeDensity, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>()).isEmpty()
    }

    @Test
    fun `M emit nothing extra W map { non-solid background rasterization fails }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity, fakeBounds)
        val mockDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger)).thenReturn(null)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val testedMapperWithFailingRasterizer = CapturedTextViewMapper<TextView>(
            viewBoundsResolver = mockViewBoundsResolver,
            colorStringFormatter = mockColorStringFormatter,
            backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(
                mockColorStringFormatter,
                mockDrawableToColorMapper
            ),
            backgroundRasterizer = ViewBackgroundRasterizer { null },
            internalLogger = mockInternalLogger
        )
        val mappingContext = mappingContext(fakeScope, fakeDensity, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapperWithFailingRasterizer.map(mockTextView, mappingContext)
            as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
    }

    @Test
    fun `M register a pending capture and emit a Pixel wireframe W map { compound drawable icon }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given: a leading icon (drawableStart/drawableLeft), the view's own bounds/padding chosen
        // so the expected icon position is simple to hand-verify.
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(mockTextView, index = 0, intrinsicWidth = 20, intrinsicHeight = 20)
        whenever(mockTextView.paddingStart).thenReturn(10)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
        assertThat(pixelWireframe.resource).isEqualTo(PixelResource.Unresolved)
        // x = view.x + paddingStart; y = view.y + centered within the view's own height.
        assertThat(pixelWireframe.bounds.x).isEqualTo(110)
        assertThat(pixelWireframe.bounds.y).isEqualTo(215)
        assertThat(pixelWireframe.bounds.width).isEqualTo(20)
        assertThat(pixelWireframe.bounds.height).isEqualTo(20)
        assertThat(registered).hasSize(1)
        assertThat(registered.single().wireframeIdentity).isEqualTo(pixelWireframe.identity)
        assertThat(registered.single().bitmap).isSameAs(mockIconBitmap)
        assertThat(registered.single().isTextFree).isTrue()
    }

    @Test
    fun `M keep background and icon Pixel wireframes distinct W map { non-solid background, icon }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given: a button with both a non-solid (ripple/selector) background AND a compound
        // drawable icon - each is captured via its own imageWireframe() call, which must not
        // collide onto the same wire id (identityFactory.imageWireframe/placeholderWireframe mint
        // exactly one identity per owner, so a naive implementation reusing the view's own owner
        // identity for both would have the icon's Pixel wireframe silently clobber the background's).
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        val mockBackgroundDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockBackgroundDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockBackgroundDrawable, mockInternalLogger))
            .thenReturn(null)
        stubCompoundDrawable(mockTextView, index = 0, intrinsicWidth = 20, intrinsicHeight = 20)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframes = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()
        assertThat(pixelWireframes).hasSize(2)
        assertThat(pixelWireframes.map { it.identity }.toSet()).hasSize(2)
        assertThat(pixelWireframes.map { it.identity.wireId }.toSet()).hasSize(2)
        assertThat(registered).hasSize(2)
        assertThat(registered.map { it.wireframeIdentity }.toSet()).hasSize(2)
        // The full-size background must still be present, at the view's own bounds - not the
        // icon's small bounds.
        assertThat(pixelWireframes).anyMatch { it.bounds == fakeBounds.toCaptured() }
    }

    @Test
    fun `M emit a PrivacyPlaceholder W map { compound drawable icon, MASK_ALL privacy }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given: a compound drawable is real icon/image content - unlike a non-solid background,
        // MASK_ALL must still surface a placeholder for it, not silently drop it.
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(mockTextView, index = 0, intrinsicWidth = 20, intrinsicHeight = 20)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).hasSize(1)
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(registered).isEmpty()
    }

    @Test
    fun `M emit nothing extra W map { no compound drawables }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given: stubTextView's default compoundDrawables are all null.
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity, fakeBounds)
        val mappingContext = mappingContext(fakeScope, fakeDensity, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).isEmpty()
    }

    private fun stubCompoundDrawable(
        view: TextView,
        index: Int,
        intrinsicWidth: Int,
        intrinsicHeight: Int
    ): Drawable {
        val mockDrawable: Drawable = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(intrinsicWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(intrinsicHeight)
        val drawables = arrayOfNulls<Drawable>(4)
        drawables[index] = mockDrawable
        whenever(view.compoundDrawables).thenReturn(drawables)
        return mockDrawable
    }

    private fun stubDisplayMetrics(view: TextView, screenWidth: Int, screenHeight: Int) {
        val resources: Resources = mock()
        val displayMetrics = DisplayMetrics().apply {
            widthPixels = screenWidth
            heightPixels = screenHeight
        }
        whenever(view.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
    }
}
