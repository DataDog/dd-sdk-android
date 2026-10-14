/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.Layout
import android.view.Gravity
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedPadding
import com.datadog.android.internal.sessionreplay.composition.CapturedVerticalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
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
    private val mockInternalLogger: InternalLogger = mock()
    private lateinit var fakeDefaultMappingContext: CapturedMappingContext
    private val testedMapper = CapturedTextViewMapper(
        viewBoundsResolver = mockViewBoundsResolver,
        colorStringFormatter = mockColorStringFormatter,
        backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(mockInternalLogger),
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
        fakeDefaultMappingContext = CapturedMappingContext(fakeIdentityFactory, fakeOwner, screenDensity = 1f)
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
        val testedColorMapper = CapturedTextViewMapper(
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

    @Test
    fun `M build exclusively from copied text and style W map() { live view changes after capture }`(
        @Forgery fakeContext: CapturedMappingContext,
        @StringForgery(regex = "[a-zA-Z0-9]{1,64}") fakeOriginalText: String
    ) {
        // Given
        val mockTextView: TextView = mock()
        val fakeText = StringBuilder(fakeOriginalText)
        mockTextView.stub {
            on { text } doReturn fakeText
            on { currentTextColor } doReturn 0x112233
            on { textSize } doReturn 20f
            on { paddingLeft } doReturn 4
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        whenever(mockColorStringFormatter.formatColorAsHexString(0x112233))
            .thenReturn("#112233FF")
        val fakeMappingContext = fakeContext.copy(screenDensity = 1f, bounds = null)
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
            on { text } doReturn fakeText
            on { background } doReturn null
            on { currentTextColor } doReturn fakeTextColor
        }
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeTextColor))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val fakeOwner = fakeFactory.view(fakeFactory.window("window"), "text-owner")
        val fakeMappingContext = CapturedMappingContext(fakeFactory, fakeOwner, screenDensity = fakeDensity)

        // When
        val result = testedMapper.map(mockTextView, fakeMappingContext) as CapturedViewMapperResult.Wireframes

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
        @FloatForgery(min = 0.1f, max = 4f) fakeDensity: Float,
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockTextView: TextView = mock {
            on { text } doReturn fakeText
            on { background } doReturn null
            on { currentTextColor } doReturn fakeTextColor
        }
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeTextColor))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val fakeOwner = fakeFactory.view(fakeFactory.window("window"), "text-owner")
        val fakeMappingContext = CapturedMappingContext(fakeFactory, fakeOwner, screenDensity = fakeDensity)

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

    private fun mockTextView(customize: KStubbing<TextView>.() -> Unit = {}): TextView {
        val mockTextView: TextView = mock {
            on { text } doReturn "text"
            customize()
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 40))
        whenever(mockColorStringFormatter.formatColorAsHexString(any())).thenReturn("#000000FF")
        return mockTextView
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
