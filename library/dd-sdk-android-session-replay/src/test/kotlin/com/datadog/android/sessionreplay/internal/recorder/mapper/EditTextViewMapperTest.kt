/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.os.Build
import android.widget.EditText
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EditTextViewMapperTest : BaseWireframeMapperTest() {

    private lateinit var testedEditTextViewMapper: EditTextViewMapper

    @Mock
    lateinit var mockuniqueIdentifierGenerator: UniqueIdentifierGenerator

    @Mock
    lateinit var mockTextWireframeMapper: TextWireframeMapper

    @Forgery
    lateinit var fakeTextWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @Mock
    lateinit var mockEditText: EditText

    @Mock
    lateinit var mockBackgroundTintList: ColorStateList

    @LongForgery
    var fakeGeneratedIdentifier: Long = 0L

    @IntForgery(min = 0, max = 0xffffff)
    var fakeBackgroundTintColor: Int = 0

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @BeforeEach
    fun `set up`() {
        whenever(mockBackgroundTintList.defaultColor).thenReturn(fakeBackgroundTintColor)
        whenever(
            mockuniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockEditText,
                EditTextViewMapper.UNDERLINE_KEY_NAME
            )
        ).thenReturn(fakeGeneratedIdentifier)
        whenever(mockEditText.backgroundTintList).thenReturn(mockBackgroundTintList)
        whenever(mockTextWireframeMapper.map(mockEditText, fakeSystemInformation))
            .thenReturn(fakeTextWireframes)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockEditText,
                fakeSystemInformation.screenDensity
            )
        )
            .thenReturn(fakeViewGlobalBounds)
        testedEditTextViewMapper = EditTextViewMapper(
            mockTextWireframeMapper,
            mockuniqueIdentifierGenerator,
            mockViewUtils
        )
    }

    @TestTargetApi(value = Build.VERSION_CODES.LOLLIPOP)
    @Test
    fun `M resolve the underline as ShapeWireframe W map()`() {
        // Given
        val expectedBackgroundColor =
            StringUtils.formatColorAndAlphaAsHexa(fakeBackgroundTintColor, OPAQUE_ALPHA_VALUE)
        val expectedHintShapeWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y +
                fakeViewGlobalBounds.height -
                EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            width = fakeViewGlobalBounds.width,
            height = EditTextViewMapper.UNDERLINE_HEIGHT_IN_PIXELS,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedBackgroundColor,
                opacity = mockEditText.alpha
            )
        )

        // When
        assertThat(testedEditTextViewMapper.map(mockEditText, fakeSystemInformation))
            .isEqualTo(fakeTextWireframes + expectedHintShapeWireframe)
    }

    @Test
    fun `M default to textMapper W map() { sdkVersion below LOLIPOP }`() {
        assertThat(testedEditTextViewMapper.map(mockEditText, fakeSystemInformation))
            .isEqualTo(fakeTextWireframes)
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
        assertThat(testedEditTextViewMapper.map(mockEditText, fakeSystemInformation))
            .isEqualTo(fakeTextWireframes)
    }
}
