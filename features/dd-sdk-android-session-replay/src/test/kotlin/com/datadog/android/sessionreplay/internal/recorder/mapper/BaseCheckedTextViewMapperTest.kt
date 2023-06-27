/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.widget.CheckedTextView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal abstract class BaseCheckedTextViewMapperTest : BaseWireframeMapperTest() {

    lateinit var testedCheckedTextWireframeMapper: CheckedTextViewMapper

    @Mock
    lateinit var mockuniqueIdentifierGenerator: UniqueIdentifierGenerator

    @Mock
    lateinit var mockTextWireframeMapper: TextViewMapper

    @Forgery
    lateinit var fakeTextWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @LongForgery
    var fakeGeneratedIdentifier: Long = 0L

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    lateinit var mockCheckedTextView: CheckedTextView

    @Mock
    lateinit var mockCheckMarkTintList: ColorStateList

    @Mock
    lateinit var mockCheckMarkDrawable: Drawable

    @IntForgery(min = 0)
    var fakeCheckMarkHeight: Int = 0

    @IntForgery(min = 0)
    var fakePaddingTop: Int = 0

    @IntForgery(min = 0)
    var fakePaddingBottom: Int = 0

    @IntForgery(min = 0)
    var fakePaddingRight: Int = 0

    @IntForgery(min = 0, max = 0xffffff)
    var fakeCheckMarkTintColor: Int = 0

    @IntForgery(min = 0, max = 0xffffff)
    var fakeCurrentTextColor: Int = 0

    @FloatForgery(min = 1f)
    var fakeTextSize: Float = 1f

    @BeforeEach
    fun `set up`() {
        whenever(mockCheckMarkDrawable.intrinsicHeight).thenReturn(fakeCheckMarkHeight)
        mockCheckedTextView = mock {
            whenever(it.checkMarkDrawable).thenReturn(mockCheckMarkDrawable)
            whenever(it.totalPaddingTop).thenReturn(fakePaddingTop)
            whenever(it.totalPaddingBottom).thenReturn(fakePaddingBottom)
            whenever(it.totalPaddingRight).thenReturn(fakePaddingRight)
            whenever(it.textSize).thenReturn(fakeTextSize)
        }
        whenever(mockCheckedTextView.currentTextColor).thenReturn(fakeCurrentTextColor)
        whenever(mockCheckMarkTintList.defaultColor).thenReturn(fakeCheckMarkTintColor)
        whenever(
            mockuniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockCheckedTextView,
                CheckableTextViewMapper.CHECKABLE_KEY_NAME
            )
        ).thenReturn(fakeGeneratedIdentifier)
        whenever(mockTextWireframeMapper.map(mockCheckedTextView, fakeMappingContext))
            .thenReturn(fakeTextWireframes)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockCheckedTextView,
                fakeMappingContext.systemInformation.screenDensity
            )
        )
            .thenReturn(fakeViewGlobalBounds)
        testedCheckedTextWireframeMapper = setupTestedMapper()
    }

    internal abstract fun setupTestedMapper(): CheckedTextViewMapper

    internal open fun expectedCheckedShapeStyle(checkBoxColor: String): MobileSegment.ShapeStyle? {
        return MobileSegment.ShapeStyle(
            backgroundColor = checkBoxColor,
            opacity = mockCheckedTextView.alpha
        )
    }

    // region Unit Tests

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { text checked }`() {
        // Given
        whenever(mockCheckedTextView.isChecked).thenReturn(true)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize = resolveCheckBoxSize()
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width -
                fakePaddingRight.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            ),
            shapeStyle = expectedCheckedShapeStyle(expectedCheckBoxColor)
        )

        // When
        val resolvedWireframes = testedCheckedTextWireframeMapper.map(
            mockCheckedTextView,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { text not checked }`() {
        // Given
        whenever(mockCheckedTextView.isChecked).thenReturn(false)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize = resolveCheckBoxSize()
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width -
                fakePaddingRight.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            ),
            shapeStyle = null
        )

        // When
        val resolvedWireframes = testedCheckedTextWireframeMapper.map(
            mockCheckedTextView,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { checkMarkDrawable not available }`() {
        // Given
        whenever(mockCheckedTextView.checkMarkDrawable).thenReturn(null)
        whenever(mockCheckedTextView.isChecked).thenReturn(false)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width -
                fakePaddingRight.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y,
            width = 0,
            height = 0,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            ),
            shapeStyle = null
        )

        // When
        val resolvedWireframes = testedCheckedTextWireframeMapper.map(
            mockCheckedTextView,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { checkMarkTintList available }`() {
        // Given
        whenever(mockCheckedTextView.checkMarkTintList).thenReturn(mockCheckMarkTintList)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCheckMarkTintColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize = resolveCheckBoxSize()
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width -
                fakePaddingRight.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            )
        )

        // When
        val resolvedWireframes = testedCheckedTextWireframeMapper.map(
            mockCheckedTextView,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { checkMarkTintList is null }`() {
        // Given
        whenever(mockCheckedTextView.checkMarkTintList).thenReturn(null)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize = resolveCheckBoxSize()
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width -
                fakePaddingRight.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            )
        )

        // When
        val resolvedWireframes = testedCheckedTextWireframeMapper.map(
            mockCheckedTextView,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { checkMarkTintList not available }`() {
        // Given
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize = resolveCheckBoxSize()
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + fakeViewGlobalBounds.width -
                fakePaddingRight.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            )
        )

        // When
        val resolvedWireframes = testedCheckedTextWireframeMapper.map(
            mockCheckedTextView,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M ignore the checkbox W map() { unique id could not be generated }`() {
        // Given
        whenever(
            mockuniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockCheckedTextView,
                CheckableTextViewMapper.CHECKABLE_KEY_NAME
            )
        ).thenReturn(null)

        // When
        val resolvedWireframes = testedCheckedTextWireframeMapper.map(
            mockCheckedTextView,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
    }

    // endregion

    // region Internal

    private fun resolveCheckBoxSize(): Long {
        val size = fakeCheckMarkHeight - fakePaddingBottom - fakePaddingTop
        return size.toLong().densityNormalized(fakeMappingContext.systemInformation.screenDensity)
    }

    // endregion
}
