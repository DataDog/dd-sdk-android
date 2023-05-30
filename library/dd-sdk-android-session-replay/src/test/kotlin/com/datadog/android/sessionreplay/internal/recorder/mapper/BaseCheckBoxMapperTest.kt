/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.CheckBox
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.datadog.tools.unit.annotations.TestTargetApi
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
internal abstract class BaseCheckBoxMapperTest : BaseWireframeMapperTest() {

    lateinit var testedCheckBoxMapper: CheckBoxMapper

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

    private lateinit var mockCheckBox: CheckBox

    @IntForgery(min = 0, max = 0xffffff)
    var fakeCurrentTextColor: Int = 0

    @FloatForgery(min = 1f, max = 100f)
    var fakeTextSize: Float = 1f

    @IntForgery(min = CheckableCompoundButtonMapper.DEFAULT_CHECKABLE_HEIGHT_IN_PX.toInt(), max = 100)
    var fakeIntrinsicDrawableHeight = 1

    @BeforeEach
    fun `set up`() {
        mockCheckBox = mock {
            whenever(it.textSize).thenReturn(fakeTextSize)
            whenever(it.currentTextColor).thenReturn(fakeCurrentTextColor)
        }
        whenever(
            mockuniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockCheckBox,
                CheckableTextViewMapper.CHECKABLE_KEY_NAME
            )
        ).thenReturn(fakeGeneratedIdentifier)
        whenever(mockTextWireframeMapper.map(mockCheckBox, fakeMappingContext))
            .thenReturn(fakeTextWireframes)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockCheckBox,
                fakeMappingContext.systemInformation.screenDensity
            )
        )
            .thenReturn(fakeViewGlobalBounds)
        testedCheckBoxMapper = setupTestedMapper()
    }

    internal abstract fun setupTestedMapper(): CheckBoxMapper

    internal open fun expectedCheckedShapeStyle(checkBoxColor: String): MobileSegment.ShapeStyle? {
        return MobileSegment.ShapeStyle(
            backgroundColor = checkBoxColor,
            opacity = mockCheckBox.alpha
        )
    }

    // region Unit Tests

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { checked, above M }`() {
        // Given
        val mockDrawable = mock<Drawable> {
            whenever(it.intrinsicHeight).thenReturn(fakeIntrinsicDrawableHeight)
        }
        whenever(mockCheckBox.buttonDrawable).thenReturn(mockDrawable)
        whenever(mockCheckBox.isChecked).thenReturn(true)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize =
            resolveCheckBoxSize(fakeIntrinsicDrawableHeight.toLong())
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + CheckableCompoundButtonMapper.MIN_PADDING_IN_PX
                .densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y + (fakeViewGlobalBounds.height - checkBoxSize) / 2,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            ),
            shapeStyle = expectedCheckedShapeStyle(expectedCheckBoxColor)
        )

        // When
        val resolvedWireframes = testedCheckBoxMapper.map(
            mockCheckBox,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { not checked, above M }`() {
        // Given
        val mockDrawable = mock<Drawable> {
            whenever(it.intrinsicHeight).thenReturn(fakeIntrinsicDrawableHeight)
        }
        whenever(mockCheckBox.buttonDrawable).thenReturn(mockDrawable)
        whenever(mockCheckBox.isChecked).thenReturn(false)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize =
            resolveCheckBoxSize(fakeIntrinsicDrawableHeight.toLong())
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + CheckableCompoundButtonMapper.MIN_PADDING_IN_PX
                .densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y + (fakeViewGlobalBounds.height - checkBoxSize) / 2,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            ),
            shapeStyle = null
        )

        // When
        val resolvedWireframes = testedCheckBoxMapper.map(
            mockCheckBox,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { checked }`() {
        // Given
        whenever(mockCheckBox.isChecked).thenReturn(true)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize =
            resolveCheckBoxSize(CheckableCompoundButtonMapper.DEFAULT_CHECKABLE_HEIGHT_IN_PX)
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + CheckableCompoundButtonMapper.MIN_PADDING_IN_PX
                .densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y + (fakeViewGlobalBounds.height - checkBoxSize) / 2,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            ),
            shapeStyle = expectedCheckedShapeStyle(expectedCheckBoxColor)
        )

        // When
        val resolvedWireframes = testedCheckBoxMapper.map(
            mockCheckBox,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes + expectedCheckBoxWireframe)
    }

    @Test
    fun `M resolve the checkbox as ShapeWireframe W map() { not checked }`() {
        // Given
        whenever(mockCheckBox.isChecked).thenReturn(false)
        val expectedCheckBoxColor = StringUtils.formatColorAndAlphaAsHexa(
            fakeCurrentTextColor,
            OPAQUE_ALPHA_VALUE
        )
        val checkBoxSize =
            resolveCheckBoxSize(CheckableCompoundButtonMapper.DEFAULT_CHECKABLE_HEIGHT_IN_PX)
        val expectedCheckBoxWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x + CheckableCompoundButtonMapper.MIN_PADDING_IN_PX
                .densityNormalized(fakeMappingContext.systemInformation.screenDensity),
            y = fakeViewGlobalBounds.y + (fakeViewGlobalBounds.height - checkBoxSize) / 2,
            width = checkBoxSize,
            height = checkBoxSize,
            border = MobileSegment.ShapeBorder(
                color = expectedCheckBoxColor,
                width = CheckableTextViewMapper.CHECKABLE_BORDER_WIDTH
            ),
            shapeStyle = null
        )

        // When
        val resolvedWireframes = testedCheckBoxMapper.map(
            mockCheckBox,
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
                mockCheckBox,
                CheckableTextViewMapper.CHECKABLE_KEY_NAME
            )
        ).thenReturn(null)

        // When
        val resolvedWireframes = testedCheckBoxMapper.map(
            mockCheckBox,
            fakeMappingContext
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
    }

    // endregion

    // region Internal

    private fun resolveCheckBoxSize(checkBoxSize: Long): Long {
        val density = fakeMappingContext.systemInformation.screenDensity
        val size = checkBoxSize - 2 * CheckableCompoundButtonMapper.MIN_PADDING_IN_PX
        return size.densityNormalized(density)
    }

    // endregion
}
