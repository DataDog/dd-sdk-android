/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.os.Build
import android.widget.EditText
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.datadog.tools.unit.annotations.TestTargetApi
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

internal abstract class BaseEditTextViewMapperTest : BaseWireframeMapperTest() {

    private lateinit var testedEditTextViewMapper: EditTextViewMapper

    @Mock
    lateinit var mockuniqueIdentifierGenerator: UniqueIdentifierGenerator

    @Mock
    lateinit var mockTextWireframeMapper: TextViewMapper

    @Forgery
    lateinit var fakeTextWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @Mock
    lateinit var mockEditText: EditText

    @Mock
    lateinit var mockBackgroundTintList: ColorStateList

    @LongForgery
    var fakeGeneratedIdentifier: Long = 0L

    @IntForgery
    var fakeBackgroundTintColor: Int = 0

    @IntForgery
    var fakeTextColor: Int = 0

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockStringUtils: StringUtils

    @BeforeEach
    fun `set up`() {
        whenever(mockEditText.currentTextColor).thenReturn(fakeTextColor)
        whenever(mockBackgroundTintList.defaultColor).thenReturn(fakeBackgroundTintColor)
        whenever(
            mockuniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockEditText,
                EditTextViewMapper.UNDERLINE_KEY_NAME
            )
        ).thenReturn(fakeGeneratedIdentifier)
        whenever(mockEditText.backgroundTintList).thenReturn(mockBackgroundTintList)
        whenever(mockTextWireframeMapper.map(mockEditText, fakeMappingContext))
            .thenReturn(fakeTextWireframes)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockEditText,
                fakeMappingContext.systemInformation.screenDensity
            )
        )
            .thenReturn(fakeViewGlobalBounds)
        testedEditTextViewMapper = initTestInstance()
    }

    abstract fun initTestInstance(): EditTextViewMapper

    @TestTargetApi(value = Build.VERSION_CODES.LOLLIPOP)
    @Test
    fun `M resolve the underline as ShapeWireframe W map()`(forge: Forge) {
        // Given
        val fakeExpectedUnderlineColor = forge.aStringMatching("#[0-9A-Fa-f]{8}")
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                fakeBackgroundTintColor,
                OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedUnderlineColor)
        val expectedUnderlineShapeWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y +
                fakeViewGlobalBounds.height -
                EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            width = fakeViewGlobalBounds.width,
            height = EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedUnderlineColor,
                opacity = mockEditText.alpha
            )
        )

        // When
        assertThat(testedEditTextViewMapper.map(mockEditText, fakeMappingContext))
            .isEqualTo(fakeTextWireframes + expectedUnderlineShapeWireframe)
    }

    @TestTargetApi(value = Build.VERSION_CODES.LOLLIPOP)
    @Test
    fun `M resolve the underline color from textColor W map{Lollipop, backgroundTint is null}`(
        forge: Forge
    ) {
        // Given
        whenever(mockEditText.backgroundTintList).thenReturn(null)
        val fakeExpectedUnderlineColor = forge.aStringMatching("#[0-9A-Fa-f]{8}")
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                fakeTextColor,
                OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedUnderlineColor)
        val expectedUnderlineShapeWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y +
                fakeViewGlobalBounds.height -
                EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            width = fakeViewGlobalBounds.width,
            height = EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedUnderlineColor,
                opacity = mockEditText.alpha
            )
        )

        // When
        assertThat(testedEditTextViewMapper.map(mockEditText, fakeMappingContext))
            .isEqualTo(fakeTextWireframes + expectedUnderlineShapeWireframe)
    }

    @Test
    fun `M resolve the underline color from textColor W map{ bellow Lollipop }`(forge: Forge) {
        // Given
        val fakeExpectedUnderlineColor = forge.aStringMatching("#[0-9A-Fa-f]{8}")
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                fakeTextColor,
                OPAQUE_ALPHA_VALUE
            )
        )
            .thenReturn(fakeExpectedUnderlineColor)
        val expectedUnderlineShapeWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y +
                fakeViewGlobalBounds.height -
                EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            width = fakeViewGlobalBounds.width,
            height = EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeExpectedUnderlineColor,
                opacity = mockEditText.alpha
            )
        )

        // When
        assertThat(testedEditTextViewMapper.map(mockEditText, fakeMappingContext))
            .isEqualTo(fakeTextWireframes + expectedUnderlineShapeWireframe)
    }

    @Test
    fun `M ignore the underline W map() { unique id could not be generated }`() {
        // Given
        whenever(
            mockuniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockEditText,
                EditTextViewMapper.UNDERLINE_KEY_NAME
            )
        ).thenReturn(null)

        // Then
        assertThat(testedEditTextViewMapper.map(mockEditText, fakeMappingContext))
            .isEqualTo(fakeTextWireframes)
    }
}
