/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.content.res.ColorStateList
import android.widget.TextView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllTextViewMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MaskAllTabLabelMapperTest {

    lateinit var testedMaskAllTabLabelMapper: MaskAllTabLabelMapper

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @IntForgery
    var fakeDefaultTextColor: Int = 0

    @StringForgery(regex = "#[0-9a-f]{6}ff")
    lateinit var fakeDefaultTextColorAsHtml: String

    @Mock
    lateinit var mockStringUtils: StringUtils

    @Mock
    lateinit var mockMaskAllTextViewMapper: MaskAllTextViewMapper

    @Forgery
    lateinit var fakeLabelWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @Mock
    lateinit var mockLabelView: TextView

    @Mock
    lateinit var mockColorStateList: ColorStateList

    @BeforeEach
    fun `set up`() {
        whenever(mockColorStateList.defaultColor).thenReturn(fakeDefaultTextColor)
        whenever(mockLabelView.textColors).thenReturn(mockColorStateList)
        whenever(
            mockStringUtils.formatColorAndAlphaAsHexa(
                fakeDefaultTextColor,
                MaskAllTabLabelMapper.OPAQUE_ALPHA
            )
        )
            .thenReturn(fakeDefaultTextColorAsHtml)

        whenever(mockMaskAllTextViewMapper.map(mockLabelView, fakeSystemInformation))
            .thenReturn(fakeLabelWireframes)
        testedMaskAllTabLabelMapper =
            MaskAllTabLabelMapper(mockMaskAllTextViewMapper, mockStringUtils)
    }

    @Test
    fun `M mask the selected text color W map`() {
        // Given
        val expectedWireframes = fakeLabelWireframes.map {
            it.copy(textStyle = it.textStyle.copy(color = fakeDefaultTextColorAsHtml))
        }

        // When
        val mappedWireframes = testedMaskAllTabLabelMapper.map(mockLabelView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedWireframes)
    }
}
