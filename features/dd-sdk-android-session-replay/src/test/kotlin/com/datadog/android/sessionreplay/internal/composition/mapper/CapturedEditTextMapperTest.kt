/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.text.Editable
import android.text.InputType
import android.widget.EditText
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedEditTextMapperTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockInternalLogger: InternalLogger = mock()
    private val testedMapper = CapturedEditTextMapper(
        viewBoundsResolver = mockViewBoundsResolver,
        internalLogger = mockInternalLogger
    )

    private fun mappingContext(
        textAndInputPrivacy: TextAndInputPrivacy,
        fakeScope: String
    ): CapturedMappingContext {
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        val owner = factory.view(factory.window("window"), "edit-text-owner")
        return CapturedMappingContext(
            factory,
            owner,
            screenDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = textAndInputPrivacy
        )
    }

    private fun mockEditText(text: String, inputType: Int): EditText {
        val mockEditText: EditText = mock()
        val mockEditable: Editable = mock()
        whenever(mockEditable.toString()).thenReturn(text)
        whenever(mockEditText.text).thenReturn(mockEditable)
        whenever(mockEditText.inputType).thenReturn(inputType)
        whenever(mockEditText.background).thenReturn(null)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockEditText, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 20))
        return mockEditText
    }

    private fun capturedText(mockEditText: EditText, mappingContext: CapturedMappingContext): String {
        val result = testedMapper.map(mockEditText, mappingContext) as CapturedViewMapperResult.Wireframes
        return result.wireframes.filterIsInstance<CapturedWireframe.Text>().single().text
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ]
    )
    fun `M mask the field W map { sensitive inputType, MASK_SENSITIVE_INPUTS }`(
        sensitiveInputType: Int,
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String
    ) {
        // Given
        val mockEditText = mockEditText(fakeText, sensitiveInputType)
        val mappingContext = mappingContext(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS, fakeScope)

        // When
        val text = capturedText(mockEditText, mappingContext)

        // Then
        assertThat(text).isEqualTo(FIXED_INPUT_MASK)
    }

    @Test
    fun `M leave the field unmasked W map { plain text inputType, MASK_SENSITIVE_INPUTS }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String
    ) {
        // Given
        val mockEditText = mockEditText(fakeText, InputType.TYPE_CLASS_TEXT)
        val mappingContext = mappingContext(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS, fakeScope)

        // When
        val text = capturedText(mockEditText, mappingContext)

        // Then
        assertThat(text).isEqualTo(fakeText)
    }

    @Test
    fun `M mask the field regardless of sensitivity W map { MASK_ALL_INPUTS }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String
    ) {
        // Given
        val mockEditText = mockEditText(fakeText, InputType.TYPE_CLASS_TEXT)
        val mappingContext = mappingContext(TextAndInputPrivacy.MASK_ALL_INPUTS, fakeScope)

        // When
        val text = capturedText(mockEditText, mappingContext)

        // Then
        assertThat(text).isEqualTo(FIXED_INPUT_MASK)
    }

    @Test
    fun `M mask the field regardless of sensitivity W map { MASK_ALL }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeText: String
    ) {
        // Given
        val mockEditText = mockEditText(fakeText, InputType.TYPE_CLASS_TEXT)
        val mappingContext = mappingContext(TextAndInputPrivacy.MASK_ALL, fakeScope)

        // When
        val text = capturedText(mockEditText, mappingContext)

        // Then
        assertThat(text).isEqualTo(FIXED_INPUT_MASK)
    }

    @Test
    fun `M obfuscate the hint W map { empty text, MASK_ALL }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeHint: String
    ) {
        // Given
        val mockEditText = mockEditText("", InputType.TYPE_CLASS_TEXT)
        whenever(mockEditText.hint).thenReturn(fakeHint)
        val mappingContext = mappingContext(TextAndInputPrivacy.MASK_ALL, fakeScope)

        // When
        val text = capturedText(mockEditText, mappingContext)

        // Then
        assertThat(text).isEqualTo(StringObfuscator.getStringObfuscator().obfuscate(fakeHint))
    }

    @Test
    fun `M leave the hint unmasked W map { empty text, MASK_SENSITIVE_INPUTS }`(
        @StringForgery fakeScope: String,
        @StringForgery fakeHint: String
    ) {
        // Given
        val mockEditText = mockEditText("", InputType.TYPE_CLASS_TEXT)
        whenever(mockEditText.hint).thenReturn(fakeHint)
        val mappingContext = mappingContext(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS, fakeScope)

        // When
        val text = capturedText(mockEditText, mappingContext)

        // Then
        assertThat(text).isEqualTo(fakeHint)
    }

    private companion object {
        const val FIXED_INPUT_MASK = "***"
    }
}
