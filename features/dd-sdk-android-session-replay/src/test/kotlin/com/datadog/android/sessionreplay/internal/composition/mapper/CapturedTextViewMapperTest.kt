/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Layout
import android.util.DisplayMetrics
import android.view.Gravity
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedPadding
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
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
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
    private lateinit var fakeDefaultMappingContext: CapturedMappingContext
    private val testedMapper = CapturedTextViewMapper<TextView>(
        viewBoundsResolver = mockViewBoundsResolver,
        colorStringFormatter = mockColorStringFormatter,
        backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(
            mockInternalLogger,
            mockColorStringFormatter,
            mockDrawableToColorMapper
        ),
        backgroundRasterizer = ViewBackgroundRasterizer { mockBitmap },
        compoundDrawableRasterizer = CompoundDrawableRasterizer { _, _, _ -> mockIconBitmap },
        internalLogger = mockInternalLogger
    )

    @BeforeEach
    fun setUp(
        @StringForgery fakeScopeValue: String,
        @StringForgery fakeWindowName: String,
        @StringForgery fakeViewId: String
    ) {
        val fakeIdentityFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScopeValue))
        val fakeOwner = fakeIdentityFactory.view(fakeIdentityFactory.window(fakeWindowName), fakeViewId)
        fakeDefaultMappingContext = CapturedMappingContext(
            fakeIdentityFactory,
            fakeOwner,
            screenDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )
    }

    @Test
    fun `M use default font without telemetry W map() { null typeface }`() {
        // Given
        val mockTextView = mockTextView()

        // When
        val result = testedMapper.map(mockTextView, fakeDefaultMappingContext)

        // Then
        assertThat(result.textWireframe().textStyle.family).isEqualTo("roboto, sans-serif")
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M report font fallback once W map { null then unsupported fonts across views and frames }`() {
        // Given
        val mockTextView = mockTextView()
        val mockTypeface: Typeface = mock()
        whenever(mockTextView.typeface).thenReturn(mockTypeface)
        val mockOtherTextView = mockTextView()
        whenever(mockOtherTextView.typeface).thenReturn(mock<Typeface>())

        // When
        val first = testedMapper.map(mockTextView, fakeDefaultMappingContext)
        val repeated = testedMapper.map(mockTextView, fakeDefaultMappingContext)
        val other = testedMapper.map(mockOtherTextView, fakeDefaultMappingContext)

        // Then
        assertThat(listOf(first, repeated, other).map { it.textWireframe().textStyle.family })
            .containsOnly("roboto, sans-serif")
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.TELEMETRY,
            "Unable to resolve text font family; using sans-serif fallback",
            onlyOnce = true
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @ParameterizedTest
    @MethodSource("builtInFonts")
    fun `M preserve built-in font families without telemetry W map`(
        fakeConstant: String,
        fakeOriginalTypeface: Typeface?,
        fakeFamily: String
    ) {
        // Given
        val mockTypeface: Typeface = mock()
        Typeface::class.java.setStaticValue(fakeConstant, mockTypeface)
        try {
            val mockTextView = mockTextView()
            whenever(mockTextView.typeface).thenReturn(mockTypeface)

            // When
            val captured = testedMapper.map(mockTextView, fakeDefaultMappingContext)

            // Then
            assertThat(captured.textWireframe().textStyle.family).isEqualTo(fakeFamily)
            verifyNoInteractions(mockInternalLogger)
        } finally {
            Typeface::class.java.setStaticValue(fakeConstant, fakeOriginalTypeface)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [Typeface.NORMAL, Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD_ITALIC])
    fun `M skip telemetry for default font styles W map`(fakeStyle: Int) {
        // Given
        val mockTypeface: Typeface = mock()
        whenever(mockTypeface.style).thenReturn(fakeStyle)
        val mockTextView = mockTextView()
        whenever(mockTextView.typeface).thenReturn(mockTypeface)
        mockStatic(Typeface::class.java).use { mockTypefaceStatic ->
            mockTypefaceStatic.`when`<Typeface> { Typeface.defaultFromStyle(fakeStyle) }.thenReturn(mockTypeface)

            // When
            val captured = testedMapper.map(mockTextView, fakeDefaultMappingContext)

            // Then
            assertThat(captured.textWireframe().textStyle.family).isEqualTo("roboto, sans-serif")
            verifyNoInteractions(mockInternalLogger)
        }
    }

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
        // Given
        val mockTextView = mockTextView()
        val fakeColor = (fakeAlpha shl 24) or 0x112233
        mockTextView.stub {
            on { text } doReturn if (fakeHasText) "text" else ""
            on { alpha } doReturn 0.5f
            on { currentTextColor } doReturn fakeColor
            on { currentHintTextColor } doReturn fakeColor
        }
        if (fakeHasHintColors) {
            val mockHintColors: ColorStateList = mock()
            whenever(mockHintColors.defaultColor).thenReturn(0xff445566.toInt())
            whenever(mockTextView.hintTextColors).thenReturn(mockHintColors)
        }
        val testedColorMapper = CapturedTextViewMapper<TextView>(
            viewBoundsResolver = mockViewBoundsResolver,
            backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(mockInternalLogger),
            internalLogger = mockInternalLogger
        )

        // When
        val result = testedColorMapper.map(
            mockTextView,
            fakeDefaultMappingContext
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
    fun `M build exclusively from copied text and style W map() { live view changes after capture }`(
        @Forgery fakeContext: CapturedMappingContext,
        @StringForgery(regex = "[a-zA-Z0-9]{1,64}") fakeOriginalText: String
    ) {
        // Given
        val mockTextView: TextView = mock()
        val fakeText = StringBuilder(fakeOriginalText)
        mockTextView.stub {
            on { compoundDrawables } doReturn arrayOfNulls(4)
            on { text } doReturn fakeText
            on { currentTextColor } doReturn 0x112233
            on { textSize } doReturn 20f
            on { paddingLeft } doReturn 4
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        whenever(mockColorStringFormatter.formatColorAsHexString(0x112233))
            .thenReturn("#112233FF")
        val fakeMappingContext = fakeContext.copy(
            screenDensity = 1f,
            bounds = null,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )
        val testedCopy = testedMapper.capture(mockTextView, fakeMappingContext)
        fakeText.replace(0, fakeText.length, fakeOriginalText + "-changed")
        whenever(mockTextView.currentTextColor).thenReturn(0xFFFFFF)
        whenever(mockTextView.paddingLeft).thenReturn(40)
        clearInvocations(mockTextView, mockViewBoundsResolver)

        // When
        val result = testedCopy.map(fakeMappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        verifyNoInteractions(mockTextView, mockViewBoundsResolver)
        val text = result.wireframes
            .filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(text.text).isEqualTo(fakeOriginalText)
        assertThat(text.textStyle.color).isEqualTo("#112233FF")
        assertThat(text.textStyle.size).isEqualTo(20L)
        val position = checkNotNull(text.textPosition) { "Expected copied text positioning" }
        val padding = checkNotNull(position.padding) { "Expected copied text padding" }
        assertThat(padding.left).isEqualTo(4L)
    }

    @Test
    fun `M capture raw unmasked text W map()`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeColorHexString: String,
        @FloatForgery(min = 0.1f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView: TextView = mock {
            on { compoundDrawables } doReturn arrayOfNulls(4)
            on { text } doReturn fakeText
            on { background } doReturn null
            on { currentTextColor } doReturn fakeTextColor
        }
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeTextColor))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val fakeOwner = fakeFactory.view(fakeFactory.window("window"), "text-owner")
        val fakeMappingContext = CapturedMappingContext(
            fakeFactory,
            fakeOwner,
            screenDensity = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )

        // When
        val result = testedMapper.map(mockTextView, fakeMappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val textWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(textWireframe.text).isEqualTo(fakeText)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeColorHexString)
        assertThat(textWireframe.bounds.x).isEqualTo(fakeBounds.x)
    }

    @Test
    fun `M obfuscate the text W map { MASK_ALL }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @FloatForgery(min = 0.1f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val stubTextView = stubTextViewWithText(fakeText, fakeDensity, fakeBounds)
        val fakeMappingContext = textMappingContext(fakeScope, fakeDensity, TextAndInputPrivacy.MASK_ALL)

        // When
        val result = testedMapper.map(stubTextView, fakeMappingContext)

        // Then
        assertThat(result.textWireframe().text)
            .isEqualTo(StringObfuscator.getStringObfuscator().obfuscate(fakeText))
    }

    @Test
    fun `M keep the text W map { MASK_ALL_INPUTS }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @FloatForgery(min = 0.1f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val stubTextView = stubTextViewWithText(fakeText, fakeDensity, fakeBounds)
        val fakeMappingContext = textMappingContext(fakeScope, fakeDensity, TextAndInputPrivacy.MASK_ALL_INPUTS)

        // When
        val result = testedMapper.map(stubTextView, fakeMappingContext)

        // Then
        assertThat(result.textWireframe().text).isEqualTo(fakeText)
    }

    @Test
    fun `M obfuscate the laid out text W map { MASK_ALL, text layout exists }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @StringForgery fakeLayoutText: String,
        @FloatForgery(min = 0.1f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockLayout: Layout = mock { on { text } doReturn fakeLayoutText }
        val stubTextView = stubTextViewWithText(fakeText, fakeDensity, fakeBounds)
        whenever(stubTextView.layout).thenReturn(mockLayout)
        val fakeMappingContext = textMappingContext(fakeScope, fakeDensity, TextAndInputPrivacy.MASK_ALL)

        // When
        val result = testedMapper.map(stubTextView, fakeMappingContext)

        // Then
        assertThat(result.textWireframe().text)
            .isEqualTo(StringObfuscator.getStringObfuscator().obfuscate(fakeLayoutText))
    }

    @Test
    fun `M not emit a Shape wireframe W map { no background }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeColorHexString: String,
        @FloatForgery(min = 0.1f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView: TextView = mock {
            on { compoundDrawables } doReturn arrayOfNulls(4)
            on { text } doReturn fakeText
            on { background } doReturn null
            on { currentTextColor } doReturn fakeTextColor
        }
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeTextColor))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val fakeOwner = fakeFactory.view(fakeFactory.window("window"), "text-owner")
        val fakeMappingContext = CapturedMappingContext(
            fakeFactory,
            fakeOwner,
            screenDensity = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        )

        // When
        val result = testedMapper.map(mockTextView, fakeMappingContext) as CapturedViewMapperResult.Wireframes

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
    fun `M distinguish paragraph and view directions W map() { relative alignment }`(
        fakeAlignment: RelativeAlignment,
        fakeLayoutDirection: Int,
        fakeParagraphDirection: Int,
        fakeExpected: CapturedHorizontalAlignment
    ) {
        // Given
        val mockTextView = mockTextView()
        val mockLayout: Layout = mock()
        whenever(mockLayout.lineCount).thenReturn(1)
        whenever(mockLayout.getParagraphDirection(0)).thenReturn(fakeParagraphDirection)
        mockTextView.stub {
            on { layout } doReturn mockLayout
            on { layoutDirection } doReturn fakeLayoutDirection
            on { textAlignment } doReturn fakeAlignment.value
        }

        // When
        val result = testedMapper.map(mockTextView, fakeDefaultMappingContext)

        // Then
        val position = checkNotNull(result.textWireframe().textPosition) { "Expected text positioning" }
        val alignment = checkNotNull(position.alignment) { "Expected text alignment" }
        assertThat(alignment.horizontal).isEqualTo(fakeExpected)
    }

    @ParameterizedTest
    @CsvSource("START, RIGHT", "END, LEFT", "LEFT, LEFT", "RIGHT, RIGHT", "CENTER_HORIZONTAL, CENTER")
    fun `M resolve relative gravity and retain absolute gravity W map() { RTL paragraph }`(
        fakeGravity: HorizontalGravity,
        fakeExpected: CapturedHorizontalAlignment
    ) {
        // Given
        val mockTextView = mockTextView()
        val mockLayout: Layout = mock()
        whenever(mockLayout.lineCount).thenReturn(1)
        whenever(mockLayout.getParagraphDirection(0)).thenReturn(-1)
        mockTextView.stub {
            on { layout } doReturn mockLayout
            on { textAlignment } doReturn TextView.TEXT_ALIGNMENT_GRAVITY
            on { gravity } doReturn (fakeGravity.value or Gravity.BOTTOM)
        }

        // When
        val result = testedMapper.map(mockTextView, fakeDefaultMappingContext)

        // Then
        val position = checkNotNull(result.textWireframe().textPosition) { "Expected text positioning" }
        val alignment = checkNotNull(position.alignment) { "Expected text alignment" }
        assertThat(alignment).isEqualTo(CapturedAlignment(fakeExpected, CapturedVerticalAlignment.BOTTOM))
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
    fun `M resolve text direction W map() { no text layout exists }`(
        fakeText: String,
        fakeLayoutDirection: Int,
        fakeTextDirection: Int,
        fakeExpected: CapturedHorizontalAlignment
    ) {
        // Given
        val mockTextView = mockTextView {
            on { text } doReturn fakeText
            on { layoutDirection } doReturn fakeLayoutDirection
            on { textDirection } doReturn fakeTextDirection
            on { textAlignment } doReturn TextView.TEXT_ALIGNMENT_TEXT_START
        }

        // When
        val result = testedMapper.map(mockTextView, fakeDefaultMappingContext)

        // Then
        val position = checkNotNull(result.textWireframe().textPosition) { "Expected text positioning" }
        val alignment = checkNotNull(position.alignment) { "Expected text alignment" }
        assertThat(alignment.horizontal).isEqualTo(fakeExpected)
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `M preserve physical horizontal insets W map() { asymmetric padding in LTR or RTL }`(
        fakeHasLayout: Boolean,
        fakeIsRtl: Boolean
    ) {
        // Given
        val mockTextView = mockTextView()
        whenever(
            mockTextView.layoutDirection
        ).thenReturn(if (fakeIsRtl) TextView.LAYOUT_DIRECTION_RTL else TextView.LAYOUT_DIRECTION_LTR)
        mockTextView.stub {
            on { paddingLeft } doReturn 10
            on { paddingRight } doReturn 20
            on { paddingStart } doReturn if (fakeIsRtl) 20 else 10
            on { paddingEnd } doReturn if (fakeIsRtl) 10 else 20
            on { totalPaddingLeft } doReturn 14
            on { totalPaddingRight } doReturn 26
            on { totalPaddingStart } doReturn if (fakeIsRtl) 26 else 14
            on { totalPaddingEnd } doReturn if (fakeIsRtl) 14 else 26
        }
        if (fakeHasLayout) whenever(mockTextView.layout).thenReturn(mock<Layout>())

        // When
        val result = testedMapper.map(
            mockTextView,
            fakeDefaultMappingContext
        ) as CapturedViewMapperResult.Wireframes

        // Then
        val position = checkNotNull(result.textWireframe().textPosition) { "Expected text positioning" }
        assertThat(position.padding)
            .isEqualTo(CapturedPadding(0, 0, if (fakeHasLayout) 14 else 10, if (fakeHasLayout) 26 else 20))
    }

    @ParameterizedTest
    @MethodSource("verticalPlacements")
    fun `M preserve vertical placement W map() { non-gravity text alignment }`(
        fakeGravity: Int,
        fakeExpectedGravity: CapturedVerticalAlignment,
        fakeHasLayout: Boolean,
        fakeAlignment: RelativeAlignment
    ) {
        // Given
        val mockTextView = mockTextView {
            on { textAlignment } doReturn fakeAlignment.value
            on { gravity } doReturn fakeGravity
            on { paddingTop } doReturn 3
            on { paddingBottom } doReturn 5
            on { totalPaddingTop } doReturn 11
            on { totalPaddingBottom } doReturn 17
        }
        if (fakeHasLayout) whenever(mockTextView.layout).thenReturn(mock<Layout>())

        // When
        val result = testedMapper.map(mockTextView, fakeDefaultMappingContext)

        // Then
        val position = checkNotNull(result.textWireframe().textPosition) { "Expected text positioning" }
        assertThat(position.padding).isEqualTo(
            if (fakeHasLayout) CapturedPadding(11, 17, 0, 0) else CapturedPadding(3, 5, 0, 0)
        )
        val alignment = checkNotNull(position.alignment) { "Expected vertical text alignment" }
        assertThat(alignment.vertical).isEqualTo(
            if (fakeHasLayout) CapturedVerticalAlignment.CENTER else fakeExpectedGravity
        )
    }

    @Test
    fun `M align by the layout direction W map { masked text in an RTL paragraph }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @FloatForgery(min = 0.1f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val stubTextView = stubTextViewWithText(fakeText, fakeDensity, fakeBounds)
        val stubLayout: Layout = mock {
            on { text } doReturn fakeText
            on { lineCount } doReturn 1
            on { getParagraphDirection(0) } doReturn Layout.DIR_RIGHT_TO_LEFT
        }
        stubTextView.stub {
            on { layout } doReturn stubLayout
            on { layoutDirection } doReturn TextView.LAYOUT_DIRECTION_LTR
            on { textAlignment } doReturn TextView.TEXT_ALIGNMENT_TEXT_START
        }
        val fakeMappingContext = textMappingContext(fakeScope, fakeDensity, TextAndInputPrivacy.MASK_ALL)

        // When
        val result = testedMapper.map(stubTextView, fakeMappingContext)

        // Then
        val position = checkNotNull(result.textWireframe().textPosition)
        assertThat(checkNotNull(position.alignment).horizontal).isEqualTo(CapturedHorizontalAlignment.LEFT)
    }

    private fun mockTextView(customize: KStubbing<TextView>.() -> Unit = {}): TextView {
        val mockTextView: TextView = mock {
            on { compoundDrawables } doReturn arrayOfNulls(4)
            on { text } doReturn "text"
            customize()
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 40))
        whenever(mockColorStringFormatter.formatColorAsHexString(any())).thenReturn("#000000FF")
        return mockTextView
    }

    private fun stubTextViewWithText(fakeText: String, fakeDensity: Float, fakeBounds: GlobalBounds): TextView {
        val stubTextView: TextView = mock {
            on { text } doReturn fakeText
            on { background } doReturn null
            on { compoundDrawables } doReturn arrayOfNulls(4)
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(stubTextView, fakeDensity)).thenReturn(fakeBounds)
        whenever(mockColorStringFormatter.formatColorAsHexString(any())).thenReturn("#000000FF")
        return stubTextView
    }

    private fun textMappingContext(
        fakeScope: String,
        fakeDensity: Float,
        textAndInputPrivacy: TextAndInputPrivacy
    ): CapturedMappingContext {
        val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        return CapturedMappingContext(
            fakeFactory,
            fakeFactory.view(fakeFactory.window("window"), "text-owner"),
            screenDensity = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = textAndInputPrivacy
        )
    }

    private fun CapturedViewMapperResult.textWireframe(): CapturedWireframe.Text =
        (this as CapturedViewMapperResult.Wireframes).wireframes.filterIsInstance<CapturedWireframe.Text>().single()

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
        // guaranteed not to contain text and is registered as text-free.
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
    fun `M not rasterize the background W map { background resolves to a fully transparent color }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTransparentRgb: Int,
        @FloatForgery(min = 0f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity, fakeBounds)
        val mockDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable, mockInternalLogger))
            .thenReturn(fakeTransparentRgb)
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
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Shape>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(registered).isEmpty()
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
                mockInternalLogger,
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
        stubCompoundDrawable(mockTextView, index = 0, widthPx = 20, heightPx = 20)
        whenever(mockTextView.paddingLeft).thenReturn(10)
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
        // x = view.x + paddingLeft; y = view.y + centered within the view's own height.
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
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        val mockBackgroundDrawable: Drawable = mock()
        whenever(mockTextView.background).thenReturn(mockBackgroundDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockBackgroundDrawable, mockInternalLogger))
            .thenReturn(null)
        stubCompoundDrawable(mockTextView, index = 0, widthPx = 20, heightPx = 20)
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
        assertThat(pixelWireframes.map { it.identity.wireId }.toSet()).hasSize(2)
        assertThat(registered).hasSize(2)
        assertThat(registered.map { it.wireframeIdentity.wireId }.toSet()).hasSize(2)
        assertThat(pixelWireframes).anyMatch { it.bounds == fakeBounds.toCaptured() }
    }

    @Test
    fun `M size the icon from its bounds W map { bounds differ from the intrinsic size }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(
            mockTextView,
            index = 0,
            widthPx = 40,
            heightPx = 30,
            intrinsicWidth = 20,
            intrinsicHeight = 20
        )
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val fakeRasterizedSizes = mutableListOf<Pair<Int, Int>>()
        val testedMapperWithRecordingRasterizer = CapturedTextViewMapper<TextView>(
            viewBoundsResolver = mockViewBoundsResolver,
            colorStringFormatter = mockColorStringFormatter,
            backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(
                mockInternalLogger,
                mockColorStringFormatter,
                mockDrawableToColorMapper
            ),
            compoundDrawableRasterizer = CompoundDrawableRasterizer { _, widthPx, heightPx ->
                fakeRasterizedSizes += widthPx to heightPx
                mockIconBitmap
            },
            internalLogger = mockInternalLogger
        )
        val mappingContext = mappingContext(fakeScope, fakeDensity = 1f, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapperWithRecordingRasterizer.map(mockTextView, mappingContext)
            as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
        assertThat(pixelWireframe.bounds.width).isEqualTo(40)
        assertThat(pixelWireframe.bounds.height).isEqualTo(30)
        assertThat(fakeRasterizedSizes).containsExactly(40 to 30)
    }

    @Test
    fun `M capture the icon W map { drawable has bounds but no intrinsic size }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(
            mockTextView,
            index = 0,
            widthPx = 24,
            heightPx = 24,
            intrinsicWidth = -1,
            intrinsicHeight = -1
        )
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val mappingContext = mappingContext(fakeScope, fakeDensity = 1f, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
        assertThat(pixelWireframe.bounds.width).isEqualTo(24)
        assertThat(pixelWireframe.bounds.height).isEqualTo(24)
    }

    @Test
    fun `M skip the icon W map { drawable has empty bounds }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(
            mockTextView,
            index = 0,
            widthPx = 0,
            heightPx = 0,
            intrinsicWidth = 20,
            intrinsicHeight = 20
        )
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
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>()).isEmpty()
        assertThat(registered).isEmpty()
    }

    @Test
    fun `M mask the icon by its bounds W map { MASK_LARGE_ONLY, small intrinsic size, large bounds }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 300)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(
            mockTextView,
            index = 0,
            widthPx = 120,
            heightPx = 120,
            intrinsicWidth = 20,
            intrinsicHeight = 20
        )
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val mappingContext = mappingContext(fakeScope, fakeDensity = 1f, imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val placeholder = result.wireframes.filterIsInstance<CapturedWireframe.PrivacyPlaceholder>().single()
        assertThat(placeholder.label).isEqualTo("Content Image")
        assertThat(placeholder.bounds.width).isEqualTo(120)
    }

    @Test
    fun `M give each icon its own pending owner W map { two compound drawables }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(mockTextView, index = 0, widthPx = 20, heightPx = 20)
        stubCompoundDrawable(mockTextView, index = 2, widthPx = 20, heightPx = 20)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        testedMapper.map(mockTextView, mappingContext)

        // Then
        val factory = mappingContext.identityFactory
        assertThat(registered.map { it.ownerIdentity }).containsExactly(
            factory.layer(mappingContext.ownerIdentity, "compound-drawable-0"),
            factory.layer(mappingContext.ownerIdentity, "compound-drawable-2")
        )
    }

    @Test
    fun `M offset the icons by the physical paddings W map { RTL, unequal horizontal padding }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(mockTextView, index = 0, widthPx = 20, heightPx = 20)
        stubCompoundDrawable(mockTextView, index = 2, widthPx = 20, heightPx = 20)
        whenever(mockTextView.paddingLeft).thenReturn(40)
        whenever(mockTextView.paddingRight).thenReturn(5)
        whenever(mockTextView.paddingStart).thenReturn(5)
        whenever(mockTextView.paddingEnd).thenReturn(40)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val mappingContext = mappingContext(fakeScope, fakeDensity = 1f, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframes = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()
        assertThat(pixelWireframes.map { it.bounds.x }).containsExactly(140L, 375L)
    }

    @Test
    fun `M center the icons within the compound-padded area W map { top padding and a perpendicular icon }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(mockTextView, index = 0, widthPx = 20, heightPx = 20)
        stubCompoundDrawable(mockTextView, index = 1, widthPx = 30, heightPx = 10)
        whenever(mockTextView.paddingLeft).thenReturn(16)
        whenever(mockTextView.compoundPaddingLeft).thenReturn(36)
        whenever(mockTextView.compoundPaddingTop).thenReturn(10)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val mappingContext = mappingContext(fakeScope, fakeDensity = 1f, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val fakeIcons = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().map { it.bounds }
        assertThat(fakeIcons.map { it.x to it.y }).containsExactly(116L to 220L, 253L to 200L)
    }

    @Test
    fun `M place the icon at its bounds origin W map { bounds start away from zero }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String
    ) {
        // Given
        val fakeBounds = GlobalBounds(x = 100, y = 200, width = 300, height = 50)
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(mockTextView, index = 0, widthPx = 20, heightPx = 20, originX = 10, originY = 5)
        whenever(mockTextView.paddingLeft).thenReturn(4)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val mappingContext = mappingContext(fakeScope, fakeDensity = 1f, imagePrivacy = ImagePrivacy.MASK_NONE)

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixelWireframe = result.wireframes.filterIsInstance<CapturedWireframe.Pixel>().single()
        assertThat(pixelWireframe.bounds.x).isEqualTo(114)
        assertThat(pixelWireframe.bounds.y).isEqualTo(220)
        assertThat(pixelWireframe.bounds.width).isEqualTo(20)
        assertThat(pixelWireframe.bounds.height).isEqualTo(20)
    }

    @Test
    fun `M drop the background without rasterizing W map { large bitmap background, MASK_LARGE_ONLY privacy }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        val mockPhoto: BitmapDrawable = mock()
        whenever(mockPhoto.intrinsicWidth).thenReturn(300)
        whenever(mockPhoto.intrinsicHeight).thenReturn(200)
        whenever(mockTextView.background).thenReturn(mockPhoto)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockPhoto, mockInternalLogger)).thenReturn(null)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).isEmpty()
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Text>()).hasSize(1)
        assertThat(registered).isEmpty()
        verifyNoInteractions(mockBitmap)
    }

    @Test
    fun `M capture the background W map { small bitmap background, MASK_LARGE_ONLY privacy }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        val mockPattern: BitmapDrawable = mock()
        whenever(mockPattern.intrinsicWidth).thenReturn(50)
        whenever(mockPattern.intrinsicHeight).thenReturn(50)
        whenever(mockTextView.background).thenReturn(mockPattern)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockPattern, mockInternalLogger)).thenReturn(null)
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            sink = PendingPixelCaptureSink { registered += it }
        )

        // When
        val result = testedMapper.map(mockTextView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.filterIsInstance<CapturedWireframe.Pixel>()).hasSize(1)
        assertThat(registered).hasSize(1)
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
        stubCompoundDrawable(mockTextView, index = 0, widthPx = 20, heightPx = 20)
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
    fun `M emit a PrivacyPlaceholder W map { small compound drawable icon, large intrinsic size, MASK_LARGE_ONLY }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int,
        @StringForgery(regex = "#[0-9A-F]{8}") fakeTextColorHexString: String,
        @IntForgery(min = 100, max = 2000) fakeLargeIntrinsicSide: Int,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView = stubTextView(fakeText, fakeTextColor, fakeTextColorHexString, fakeDensity = 1f, fakeBounds)
        stubCompoundDrawable(
            mockTextView,
            index = 0,
            widthPx = 20,
            heightPx = 20,
            intrinsicWidth = fakeLargeIntrinsicSide,
            intrinsicHeight = fakeLargeIntrinsicSide
        )
        stubDisplayMetrics(mockTextView, screenWidth = 1000, screenHeight = 1000)
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            fakeScope,
            fakeDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
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
        widthPx: Int,
        heightPx: Int,
        intrinsicWidth: Int = widthPx,
        intrinsicHeight: Int = heightPx,
        originX: Int = 0,
        originY: Int = 0
    ): Drawable {
        val mockDrawable: Drawable = mock()
        val fakeDrawableBounds = Rect().apply {
            left = originX
            top = originY
            right = originX + widthPx
            bottom = originY + heightPx
        }
        whenever(mockDrawable.bounds).thenReturn(fakeDrawableBounds)
        whenever(mockDrawable.intrinsicWidth).thenReturn(intrinsicWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(intrinsicHeight)
        val drawables = view.compoundDrawables
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

    companion object {
        @JvmStatic
        fun verticalPlacements(): List<Arguments> = listOf(
            Triple(Gravity.TOP, CapturedVerticalAlignment.TOP, false),
            Triple(Gravity.BOTTOM, CapturedVerticalAlignment.BOTTOM, false),
            Triple(Gravity.CENTER_VERTICAL, CapturedVerticalAlignment.CENTER, false),
            Triple(Gravity.TOP, CapturedVerticalAlignment.TOP, true),
            Triple(Gravity.BOTTOM, CapturedVerticalAlignment.BOTTOM, true),
            Triple(Gravity.CENTER_VERTICAL, CapturedVerticalAlignment.CENTER, true)
        ).flatMap { (fakeGravity, fakeExpectedGravity, fakeHasLayout) ->
            RelativeAlignment.entries.map { fakeAlignment ->
                Arguments.of(fakeGravity, fakeExpectedGravity, fakeHasLayout, fakeAlignment)
            }
        }

        @JvmStatic
        fun builtInFonts(): List<Arguments> = listOf(
            Arguments.of("DEFAULT", Typeface.DEFAULT, "roboto, sans-serif"),
            Arguments.of("DEFAULT_BOLD", Typeface.DEFAULT_BOLD, "roboto, sans-serif"),
            Arguments.of("SANS_SERIF", Typeface.SANS_SERIF, "roboto, sans-serif"),
            Arguments.of("MONOSPACE", Typeface.MONOSPACE, "monospace"),
            Arguments.of("SERIF", Typeface.SERIF, "serif")
        )
    }
}
