/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.widget.TextView
import com.datadog.android.sessionreplay.recorder.aMockView
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
internal class MaskAllTextViewWireframeMapperTest : BaseTextViewWireframeMapperTest() {

    @Mock
    lateinit var mockStringObfuscator: StringObfuscator

    @StringForgery
    lateinit var fakeMaskedStringValue: String

    // region super

    override fun initTestedMapper(): TextWireframeMapper {
        whenever(mockStringObfuscator.obfuscate(fakeText)).thenReturn(fakeMaskedStringValue)
        return MaskAllTextWireframeMapper(stringObfuscator = mockStringObfuscator)
    }

    override fun resolveTextValue(textView: TextView): String {
        return fakeMaskedStringValue
    }

    // endregion

    // region Unit tests

    @Test
    fun `M resolve a TextWireframe with masked text W map(){TextView}`(
        forge: Forge
    ) {
        // Given
        whenever(mockStringObfuscator.obfuscate(fakeText)).thenReturn(fakeMaskedStringValue)
        val mockTextView: TextView = forge.aMockView<TextView>().apply {
            whenever(this.text).thenReturn(fakeText)
            whenever(this.typeface).thenReturn(mock())
        }

        // When
        val textWireframe = testedTextWireframeMapper.map(mockTextView, fakePixelDensity)

        // Then
        val expectedWireframe = mockTextView.toTextWireframe().copy(text = fakeMaskedStringValue)
        assertThat(textWireframe).isEqualTo(expectedWireframe)
    }

    // endregion
}
