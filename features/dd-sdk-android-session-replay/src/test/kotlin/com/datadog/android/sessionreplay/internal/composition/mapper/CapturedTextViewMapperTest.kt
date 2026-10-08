/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.text.Layout
import android.view.Gravity
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.CapturedAlignment
import com.datadog.android.sessionreplay.internal.composition.CapturedHorizontalAlignment
import com.datadog.android.sessionreplay.internal.composition.CapturedPadding
import com.datadog.android.sessionreplay.internal.composition.CapturedTextPosition
import com.datadog.android.sessionreplay.internal.composition.CapturedVerticalAlignment
import com.datadog.android.sessionreplay.internal.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
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
import org.mockito.kotlin.mock
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
    private val testedMapper = CapturedTextViewMapper(
        viewBoundsResolver = mockViewBoundsResolver,
        colorStringFormatter = mockColorStringFormatter,
        backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(),
        internalLogger = mockInternalLogger
    )

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
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.background).thenReturn(null)
        whenever(mockTextView.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockColorStringFormatter.formatColorAndAlphaAsHexString(fakeTextColor, OPAQUE_ALPHA_VALUE))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "text-owner")
        val mappingContext = CapturedMappingContext(factory, owner, screenDensity = fakeDensity)

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
        whenever(mockTextView.text).thenReturn(fakeText)
        whenever(mockTextView.background).thenReturn(null)
        whenever(mockTextView.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockColorStringFormatter.formatColorAndAlphaAsHexString(fakeTextColor, OPAQUE_ALPHA_VALUE))
            .thenReturn(fakeColorHexString)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "text-owner")
        val mappingContext = CapturedMappingContext(factory, owner, screenDensity = fakeDensity)

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
        whenever(mockTextView.text).thenReturn("text")
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, 1f)
        ).thenReturn(GlobalBounds(0, 0, 100, 40))
        whenever(mockColorStringFormatter.formatColorAndAlphaAsHexString(any(), any())).thenReturn("#000000FF")
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
}
