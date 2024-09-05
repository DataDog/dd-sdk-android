package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapperTest.Companion.parametersMatrix
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.util.stream.Stream

internal abstract class EditTextMapperTest :
    BaseAsyncBackgroundWireframeMapperTest<EditText, EditTextMapper>() {

    @Mock
    lateinit var mockEditable: Editable

    @StringForgery
    lateinit var fakeText: String

    @StringForgery
    lateinit var fakeHint: String

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

        whenever(mockEditable.toString()) doReturn fakeText

        withTextAndInputPrivacy(privacyOption())

        testedWireframeMapper = EditTextMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    abstract fun expectedPrivacyCompliantText(text: String, isSensitive: Boolean): String

    abstract fun expectedPrivacyCompliantHint(hint: String): String

    abstract fun privacyOption(): TextAndInputPrivacy

    @ParameterizedTest(name = "{index} (typeface: {0}, align:{2}, gravity:{3})")
    @MethodSource("basicParametersMatrix")
    fun `M resolves wireframe W map {default input type}`(
        fakeTypeface: Typeface?,
        expectedFontFamily: String,
        fakeTextAlignment: Int,
        fakeTextGravity: Int,
        expectedHorizontal: MobileSegment.Horizontal,
        expectedVertical: MobileSegment.Vertical
    ) {
        // Given
        prepareMockView<EditText> { mockView ->
            whenever(mockView.typeface) doReturn fakeTypeface
            whenever(mockView.textSize) doReturn fakeFontSize
            whenever(mockView.currentTextColor) doReturn fakeTextColor
            whenever(mockView.textAlignment) doReturn fakeTextAlignment
            whenever(mockView.gravity) doReturn fakeTextGravity
            whenever(mockView.text) doReturn mockEditable
            whenever(mockView.hint) doReturn fakeHint
            whenever(mockView.inputType) doReturn InputType.TYPE_CLASS_TEXT
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
            .ignoringFieldsMatchingRegexes("textStyle.*") // Compared below, because of rounding error with density
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
                    text = expectedPrivacyCompliantText(fakeText, false),
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

    @ParameterizedTest(name = "{index} (inputType: {0})")
    @MethodSource("inputTypeParametersMatrix")
    fun `M resolves wireframe W map {varying input type}`(
        fakeInputType: Int,
        expectedIsSensitive: Boolean
    ) {
        // Given
        prepareMockView<EditText> { mockView ->
            whenever(mockView.typeface) doReturn null
            whenever(mockView.textSize) doReturn fakeFontSize
            whenever(mockView.currentTextColor) doReturn fakeTextColor
            whenever(mockView.textAlignment) doReturn TextView.TEXT_ALIGNMENT_GRAVITY
            whenever(mockView.gravity) doReturn Gravity.NO_GRAVITY
            whenever(mockView.text) doReturn mockEditable
            whenever(mockView.hint) doReturn fakeHint
            whenever(mockView.inputType) doReturn fakeInputType
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
            .ignoringFieldsMatchingRegexes("textStyle.*") // Compared below, because of rounding error with density
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
                    text = expectedPrivacyCompliantText(fakeText, expectedIsSensitive),
                    textStyle = MobileSegment.TextStyle(
                        family = TextViewMapper.SANS_SERIF_FAMILY_NAME,
                        size = expectedFontSize,
                        color = fakeTextColorHexString
                    ),
                    textPosition = MobileSegment.TextPosition(
                        padding = MobileSegment.Padding(0L, 0L, 0L, 0L),
                        alignment = MobileSegment.Alignment(
                            horizontal = MobileSegment.Horizontal.LEFT,
                            vertical = MobileSegment.Vertical.CENTER
                        )
                    )
                )
            )
        assertThat(textWireframe.textStyle.family).isEqualTo(TextViewMapper.SANS_SERIF_FAMILY_NAME)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeTextColorHexString)
        assertThat(textWireframe.textStyle.size).isCloseTo(expectedFontSize, offset(1L))
    }

    @ParameterizedTest(name = "{index} (inputType: {0})")
    @MethodSource("inputTypeParametersMatrix")
    fun `M resolves wireframe W map {varying input type, no text}`(
        fakeInputType: Int
    ) {
        // Given
        whenever(mockEditable.toString()) doReturn ""
        prepareMockView<EditText> { mockView ->
            whenever(mockView.typeface) doReturn null
            whenever(mockView.textSize) doReturn fakeFontSize
            whenever(mockView.currentTextColor) doReturn fakeTextColor
            whenever(mockView.textAlignment) doReturn TextView.TEXT_ALIGNMENT_GRAVITY
            whenever(mockView.gravity) doReturn Gravity.NO_GRAVITY
            whenever(mockView.text) doReturn mockEditable
            whenever(mockView.hint) doReturn fakeHint
            whenever(mockView.inputType) doReturn fakeInputType
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
            .ignoringFieldsMatchingRegexes("textStyle.*") // Compared below, because of rounding error with density
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
                    text = expectedPrivacyCompliantHint(fakeHint),
                    textStyle = MobileSegment.TextStyle(
                        family = TextViewMapper.SANS_SERIF_FAMILY_NAME,
                        size = expectedFontSize,
                        color = fakeTextColorHexString
                    ),
                    textPosition = MobileSegment.TextPosition(
                        padding = MobileSegment.Padding(0L, 0L, 0L, 0L),
                        alignment = MobileSegment.Alignment(
                            horizontal = MobileSegment.Horizontal.LEFT,
                            vertical = MobileSegment.Vertical.CENTER
                        )
                    )
                )
            )
        assertThat(textWireframe.textStyle.family).isEqualTo(TextViewMapper.SANS_SERIF_FAMILY_NAME)
        assertThat(textWireframe.textStyle.color).isEqualTo(fakeTextColorHexString)
        assertThat(textWireframe.textStyle.size).isCloseTo(expectedFontSize, offset(1L))
    }

    companion object {

        private val safeTextVariations = arrayOf(
            InputType.TYPE_TEXT_VARIATION_NORMAL,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_PERSON_NAME, // ??
            InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_TEXT_VARIATION_PHONETIC
        )

        private val safeNumberVariations = arrayOf(
            InputType.TYPE_NUMBER_VARIATION_NORMAL
        )

        private val safeDateTimeVariations = arrayOf(
            InputType.TYPE_DATETIME_VARIATION_NORMAL,
            InputType.TYPE_DATETIME_VARIATION_DATE,
            InputType.TYPE_DATETIME_VARIATION_TIME
        )

        private val sensitiveTextVariations = arrayOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        private val sensitiveNumberVariations = arrayOf(
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )

        private fun inputTypeUseCases(): MutableMap<Int, Boolean> {
            val inputTypeArgs = mutableMapOf<Int, Boolean>()
            safeTextVariations.forEach { inputTypeArgs[InputType.TYPE_CLASS_TEXT or it] = false }
            safeNumberVariations.forEach { inputTypeArgs[InputType.TYPE_CLASS_NUMBER or it] = false }
            safeDateTimeVariations.forEach { inputTypeArgs[InputType.TYPE_CLASS_DATETIME or it] = false }
            sensitiveTextVariations.forEach { inputTypeArgs[InputType.TYPE_CLASS_TEXT or it] = true }
            sensitiveNumberVariations.forEach { inputTypeArgs[InputType.TYPE_CLASS_NUMBER or it] = true }
            inputTypeArgs[InputType.TYPE_CLASS_PHONE] = true
            return inputTypeArgs
        }

        @JvmStatic
        fun basicParametersMatrix(): Stream<Arguments> {
            return parametersMatrix()
        }

        @JvmStatic
        fun inputTypeParametersMatrix(): Stream<Arguments> {
            val inputTypeArgs = inputTypeUseCases()

            val argsMatrix = mutableListOf<Arguments>()

            inputTypeArgs.forEach { (inputType, isSensitive) ->

                argsMatrix.add(
                    Arguments.of(
                        inputType,
                        isSensitive
                    )
                )
            }

            return argsMatrix.stream()
        }
    }
}
