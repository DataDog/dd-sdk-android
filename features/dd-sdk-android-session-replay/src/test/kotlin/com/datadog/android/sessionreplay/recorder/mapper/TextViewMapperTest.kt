package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.model.MobileSegment.Horizontal.LEFT
import com.datadog.android.sessionreplay.model.MobileSegment.Horizontal.RIGHT
import com.datadog.android.sessionreplay.model.MobileSegment.Vertical.BOTTOM
import com.datadog.android.sessionreplay.model.MobileSegment.Vertical.TOP
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.util.stream.Stream
import com.datadog.android.sessionreplay.model.MobileSegment.Horizontal.CENTER as CENTER_H
import com.datadog.android.sessionreplay.model.MobileSegment.Vertical.CENTER as CENTER_V

internal abstract class TextViewMapperTest :
    BaseAsyncBackgroundWireframeMapperTest<TextView, TextViewMapper<TextView>>() {

    @StringForgery
    lateinit var fakeText: String

    @FloatForgery(0f, 255f)
    var fakeFontSize: Float = 0f

    @IntForgery(min = 0, max = 0xffffff)
    var fakeTextColor: Int = 0

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeTextColorHexString: String

    @BeforeEach
    fun `set up`() {
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                fakeTextColor,
                OPAQUE_ALPHA_VALUE
            )
        ) doReturn fakeTextColorHexString

        withTextAndInputPrivacy(privacyOption())

        testedWireframeMapper = TextViewMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    abstract fun expectedPrivacyCompliantText(text: String): String

    abstract fun privacyOption(): TextAndInputPrivacy

    @ParameterizedTest(name = "{index} (typeface: {0}, align:{2}, gravity:{3})")
    @MethodSource("parametersMatrix")
    fun `M resolves wireframe W map`(
        fakeTypeface: Typeface?,
        expectedFontFamily: String,
        fakeTextAlignment: Int,
        fakeTextGravity: Int,
        expectedHorizontal: MobileSegment.Horizontal,
        expectedVertical: MobileSegment.Vertical
    ) {
        // Given
        prepareMockView<TextView> { mockView ->
            whenever(mockView.typeface) doReturn fakeTypeface
            whenever(mockView.textSize) doReturn fakeFontSize
            whenever(mockView.currentTextColor) doReturn fakeTextColor
            whenever(mockView.textAlignment) doReturn fakeTextAlignment
            whenever(mockView.gravity) doReturn fakeTextGravity
            whenever(mockView.text) doReturn fakeText
        }
        val expectedFontSize = (fakeFontSize / fakeMappingContext.systemInformation.screenDensity).toLong()

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        val textWireframe = wireframes[0] as MobileSegment.Wireframe.TextWireframe
        assertThat(textWireframe)
            .usingRecursiveComparison()
            .ignoringFields("textStyle") // Compared below, because of rounding error with density
            .isEqualTo(
                MobileSegment.Wireframe.TextWireframe(
                    id = fakeViewIdentifier,
                    x = fakeViewGlobalBounds.x,
                    y = fakeViewGlobalBounds.y,
                    width = fakeViewGlobalBounds.width,
                    height = fakeViewGlobalBounds.height,
                    clip = null,
                    shapeStyle = null,
                    border = null,
                    text = expectedPrivacyCompliantText(fakeText),
                    textStyle = MobileSegment.TextStyle(
                        family = expectedFontFamily,
                        size = expectedFontSize,
                        color = fakeTextColorHexString
                    ),
                    textPosition = MobileSegment.TextPosition(
                        padding = MobileSegment.Padding(0L, 0L, 0L, 0L),
                        alignment = MobileSegment.Alignment(
                            horizontal = expectedHorizontal,
                            vertical = expectedVertical
                        )
                    )
                )
            )
        assertThat(textWireframe.textStyle.family).isEqualTo(expectedFontFamily)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeTextColorHexString)
        assertThat(textWireframe.textStyle.size).isCloseTo(expectedFontSize, offset(1L))
    }

    companion object {

        @JvmStatic
        fun prepareStaticTypeface(name: String) {
            val mockTypeface: Typeface = mock()
            whenever(mockTypeface.toString()) doReturn name
            Typeface::class.java.setStaticValue(name, mockTypeface)
        }

        @JvmStatic
        fun prepareStaticTypeFaces() {
            if (Typeface.DEFAULT == null) {
                prepareStaticTypeface("DEFAULT")
            }
            if (Typeface.DEFAULT_BOLD == null) {
                prepareStaticTypeface("DEFAULT_BOLD")
            }
            if (Typeface.SANS_SERIF == null) {
                prepareStaticTypeface("SANS_SERIF")
            }
            if (Typeface.SERIF == null) {
                prepareStaticTypeface("SERIF")
            }
            if (Typeface.MONOSPACE == null) {
                prepareStaticTypeface("MONOSPACE")
            }
        }

        /**
         * Returns a map of use cases around a TextView's typeface,
         * where the key is the `TextView.typeface` value to set on the mock, and the value is
         * the font family name expected in the generated wireframe.
         */
        fun fontUseCases() = mapOf(
            Typeface.DEFAULT to TextViewMapper.SANS_SERIF_FAMILY_NAME,
            Typeface.DEFAULT_BOLD to TextViewMapper.SANS_SERIF_FAMILY_NAME,
            Typeface.SERIF to TextViewMapper.SERIF_FAMILY_NAME,
            Typeface.SANS_SERIF to TextViewMapper.SANS_SERIF_FAMILY_NAME,
            Typeface.MONOSPACE to TextViewMapper.MONOSPACE_FAMILY_NAME,
            org.mockito.kotlin.mock<Typeface>() to TextViewMapper.SANS_SERIF_FAMILY_NAME,
            null to TextViewMapper.SANS_SERIF_FAMILY_NAME
        )

        /**
         * Returns a map of use cases around a TextView's gravity,
         * where the key is the `TextView.gravity` value to set on the mock, and the value is
         * the pair of Horizontal and Vertical alignment expected in the generated wireframe.
         */
        fun gravityUseCases() = mapOf(

            Gravity.NO_GRAVITY to (LEFT to CENTER_V),

            Gravity.START or Gravity.TOP to (LEFT to TOP),
            Gravity.LEFT or Gravity.TOP to (LEFT to TOP),
            Gravity.CENTER or Gravity.TOP to (CENTER_H to TOP),
            Gravity.CENTER_HORIZONTAL or Gravity.TOP to (CENTER_H to TOP),
            Gravity.END or Gravity.TOP to (RIGHT to TOP),
            Gravity.RIGHT or Gravity.TOP to (RIGHT to TOP),

            Gravity.START or Gravity.CENTER to (LEFT to CENTER_V),
            Gravity.LEFT or Gravity.CENTER to (LEFT to CENTER_V),
            Gravity.CENTER or Gravity.CENTER to (CENTER_H to CENTER_V),
            Gravity.CENTER_HORIZONTAL or Gravity.CENTER to (CENTER_H to CENTER_V),
            Gravity.END or Gravity.CENTER to (RIGHT to CENTER_V),
            Gravity.RIGHT or Gravity.CENTER to (RIGHT to CENTER_V),

            Gravity.START or Gravity.CENTER_VERTICAL to (LEFT to CENTER_V),
            Gravity.LEFT or Gravity.CENTER_VERTICAL to (LEFT to CENTER_V),
            Gravity.CENTER or Gravity.CENTER_VERTICAL to (CENTER_H to CENTER_V),
            Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL to (CENTER_H to CENTER_V),
            Gravity.END or Gravity.CENTER_VERTICAL to (RIGHT to CENTER_V),
            Gravity.RIGHT or Gravity.CENTER_VERTICAL to (RIGHT to CENTER_V),

            Gravity.START or Gravity.BOTTOM to (LEFT to BOTTOM),
            Gravity.LEFT or Gravity.BOTTOM to (LEFT to BOTTOM),
            Gravity.CENTER or Gravity.BOTTOM to (CENTER_H to BOTTOM),
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM to (CENTER_H to BOTTOM),
            Gravity.END or Gravity.BOTTOM to (RIGHT to BOTTOM),
            Gravity.RIGHT or Gravity.BOTTOM to (RIGHT to BOTTOM)
        )

        /**
         * Returns a map of use cases around a TextView's gravity,
         * where the key is the `TextView.gravity` value to set on the mock, and the value is
         * the pair of Horizontal and Vertical alignment expected in the generated wireframe.
         * Note: it doesn't include the `TextView.TEXT_ALIGNMENT_GRAVITY` case as the expected output
         * depends on the `TextView.gravity` field.
         */
        fun textAlignmentCases() = mapOf(
            TextView.TEXT_ALIGNMENT_CENTER to (CENTER_H to CENTER_V),
            TextView.TEXT_ALIGNMENT_TEXT_END to (RIGHT to CENTER_V),
            TextView.TEXT_ALIGNMENT_VIEW_END to (RIGHT to CENTER_V),
            TextView.TEXT_ALIGNMENT_TEXT_START to (LEFT to CENTER_V),
            TextView.TEXT_ALIGNMENT_VIEW_START to (LEFT to CENTER_V)
        )

        @JvmStatic
        fun parametersMatrix(): Stream<Arguments> {
            prepareStaticTypeFaces()

            val fontArgs = fontUseCases()
            val alignmentArgs = textAlignmentCases()
            val gravityArgs = gravityUseCases()

            val argsMatrix = mutableListOf<Arguments>()
            fontArgs.forEach { (typeface, fontFamilyName) ->
                gravityArgs.forEach { (gravity, gravityWireframe) ->
                    alignmentArgs.forEach { (alignment, alignmentWireframe) ->
                        argsMatrix.add(
                            Arguments.of(
                                typeface,
                                fontFamilyName,
                                alignment,
                                gravity, // ignored
                                alignmentWireframe.first,
                                alignmentWireframe.second
                            )
                        )
                    }
                    argsMatrix.add(
                        Arguments.of(
                            typeface,
                            fontFamilyName,
                            TextView.TEXT_ALIGNMENT_GRAVITY,
                            gravity,
                            gravityWireframe.first,
                            gravityWireframe.second
                        )
                    )
                }
            }

            return argsMatrix.stream()
        }
    }
}
