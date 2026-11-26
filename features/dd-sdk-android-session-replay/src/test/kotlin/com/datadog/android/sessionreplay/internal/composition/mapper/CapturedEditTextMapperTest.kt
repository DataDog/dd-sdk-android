/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.text.Editable
import android.text.InputType
import android.text.Layout
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
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedEditTextMapperTest {

    private lateinit var testedMapper: CapturedEditTextMapper

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeViewId: UUID

    @BeforeEach
    fun `set up`() {
        testedMapper = CapturedEditTextMapper(
            viewBoundsResolver = mockViewBoundsResolver,
            backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(mockInternalLogger),
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M mask the field W map { sensitive inputType, MASK_SENSITIVE_INPUTS }`(
        @StringForgery fakeText: String,
        forge: Forge
    ) {
        // Given
        val fakeSensitiveInputType = forge.anElementFrom(SENSITIVE_INPUT_TYPES)
        val stubEditText = stubEditText(fakeText, fakeSensitiveInputType)

        // When
        val text = capturedText(stubEditText, mappingContext(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS))

        // Then
        assertThat(text).isEqualTo(FIXED_INPUT_MASK)
    }

    @Test
    fun `M leave the field unmasked W map { plain text inputType, MASK_SENSITIVE_INPUTS }`(
        @StringForgery fakeText: String
    ) {
        // Given
        val stubEditText = stubEditText(fakeText, InputType.TYPE_CLASS_TEXT)

        // When
        val text = capturedText(stubEditText, mappingContext(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS))

        // Then
        assertThat(text).isEqualTo(fakeText)
    }

    @Test
    fun `M record the rendered text W map { transformed plain text input, MASK_SENSITIVE_INPUTS }`(
        @StringForgery fakeText: String,
        @StringForgery fakeRenderedText: String
    ) {
        // Given
        val stubEditText = stubEditText(fakeText, InputType.TYPE_CLASS_TEXT)
        val stubLayout: Layout = mock()
        whenever(stubLayout.text).thenReturn(fakeRenderedText)
        whenever(stubEditText.layout).thenReturn(stubLayout)

        // When
        val text = capturedText(stubEditText, mappingContext(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS))

        // Then
        assertThat(text).isEqualTo(fakeRenderedText)
    }

    @Test
    fun `M mask the field regardless of sensitivity W map { input masking }`(
        @StringForgery fakeText: String,
        forge: Forge
    ) {
        // Given
        val fakePrivacy = forge.anElementFrom(TextAndInputPrivacy.MASK_ALL, TextAndInputPrivacy.MASK_ALL_INPUTS)
        val stubEditText = stubEditText(fakeText, InputType.TYPE_CLASS_TEXT)

        // When
        val text = capturedText(stubEditText, mappingContext(fakePrivacy))

        // Then
        assertThat(text).isEqualTo(FIXED_INPUT_MASK)
    }

    @Test
    fun `M obfuscate the hint W map { empty text, MASK_ALL }`(
        @StringForgery fakeHint: String
    ) {
        // Given
        val stubEditText = stubEditText("", InputType.TYPE_CLASS_TEXT)
        whenever(stubEditText.hint).thenReturn(fakeHint)

        // When
        val text = capturedText(stubEditText, mappingContext(TextAndInputPrivacy.MASK_ALL))

        // Then
        assertThat(text).isEqualTo(StringObfuscator.getStringObfuscator().obfuscate(fakeHint))
    }

    @Test
    fun `M leave the hint unmasked W map { empty text, input masking only }`(
        @StringForgery fakeHint: String,
        forge: Forge
    ) {
        // Given
        val fakePrivacy = forge.anElementFrom(
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            TextAndInputPrivacy.MASK_ALL_INPUTS
        )
        val stubEditText = stubEditText("", InputType.TYPE_CLASS_TEXT)
        whenever(stubEditText.hint).thenReturn(fakeHint)

        // When
        val text = capturedText(stubEditText, mappingContext(fakePrivacy))

        // Then
        assertThat(text).isEqualTo(fakeHint)
    }

    private fun mappingContext(textAndInputPrivacy: TextAndInputPrivacy): CapturedMappingContext {
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeViewId.toString()))
        return CapturedMappingContext(
            factory,
            factory.view(factory.window("window"), "edit-text-owner"),
            screenDensity = 1f,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = textAndInputPrivacy
        )
    }

    private fun stubEditText(text: String, inputType: Int): EditText {
        val stubEditable: Editable = mock()
        whenever(stubEditable.toString()).thenReturn(text)
        val stubEditText: EditText = mock {
            on { this.text } doReturn stubEditable
            on { this.inputType } doReturn inputType
            on { background } doReturn null
            on { compoundDrawables } doReturn arrayOfNulls(4)
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(stubEditText, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 20))
        return stubEditText
    }

    private fun capturedText(editText: EditText, mappingContext: CapturedMappingContext): String {
        val result = testedMapper.map(editText, mappingContext) as CapturedViewMapperResult.Wireframes
        return result.wireframes.filterIsInstance<CapturedWireframe.Text>().single().text
    }

    private companion object {
        const val FIXED_INPUT_MASK = "***"

        val SENSITIVE_INPUT_TYPES = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_PHONE
        )
    }
}
