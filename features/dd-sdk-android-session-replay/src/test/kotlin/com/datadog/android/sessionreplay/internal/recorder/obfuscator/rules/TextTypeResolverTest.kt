/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules

import android.text.SpannableStringBuilder
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class TextTypeResolverTest {

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    lateinit var testedTextTypeResolver: TextTypeResolver

    @Mock
    lateinit var mockTextView: TextView

    @BeforeEach
    fun `set up`() {
        // make sure the initial mappingContext hasOptionSelectorParent is false
        fakeMappingContext = fakeMappingContext.copy(hasOptionSelectorParent = false)
        testedTextTypeResolver = TextTypeResolver()
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType password}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(
            EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                .or(EditorInfo.TYPE_CLASS_TEXT)
        )

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType web password}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(
            EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
                .or(EditorInfo.TYPE_CLASS_TEXT)
        )

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType number password}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(
            EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
                .or(EditorInfo.TYPE_CLASS_NUMBER)
        )

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType visible password}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                .or(EditorInfo.TYPE_CLASS_TEXT)
        )

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType email}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(
            EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                .or(EditorInfo.TYPE_CLASS_TEXT)
        )

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType web email}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(
            EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                .or(EditorInfo.TYPE_CLASS_TEXT)
        )

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType postal address}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(
            EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                .or(EditorInfo.TYPE_CLASS_TEXT)
        )

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as SENSITIVE_TEXT W resolveTextType {inputType phone number}`() {
        // Given
        whenever(mockTextView.inputType).thenReturn(EditorInfo.TYPE_CLASS_PHONE)

        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.SENSITIVE_TEXT)
    }

    @Test
    fun `M resolve as INPUT_TEXT W resolveTextType {EditText, text not empty}`(
        @StringForgery fakeText: String
    ) {
        // Given
        val fakeEditText: EditText = mock {
            whenever(it.text).thenReturn(SpannableStringBuilder().append(fakeText))
        }

        // When
        val textType = testedTextTypeResolver.resolveTextType(fakeEditText, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.INPUT_TEXT)
    }

    @Test
    fun `M resolve as INPUT_TEXT W resolveTextType {EditText, text is empty}`() {
        // Given
        val fakeEditText: EditText = mock {
            whenever(it.text).thenReturn(mock())
        }

        // When
        val textType = testedTextTypeResolver.resolveTextType(fakeEditText, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.INPUT_TEXT)
    }

    @Test
    fun `M resolve as INPUT_TEXT W resolveTextType {EditText, text is null}`() {
        // Given
        val fakeEditText: EditText = mock {
            whenever(it.text).thenReturn(null)
        }

        // When
        val textType = testedTextTypeResolver.resolveTextType(fakeEditText, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.INPUT_TEXT)
    }

    @Test
    fun `M resolve as OPTION_TEXT W resolveTextType {TextView, optionsSelector parent}`() {
        // Given
        val fakeText: TextView = mock()
        val fakeContextWithOptionSelector = fakeMappingContext.copy(hasOptionSelectorParent = true)

        // When
        val textType =
            testedTextTypeResolver.resolveTextType(fakeText, fakeContextWithOptionSelector)

        // Then
        assertThat(textType).isEqualTo(TextType.OPTION_TEXT)
    }

    @Test
    fun `M resolve as HINTS_TEXT W resolveTextType {EditText, text is empty, has hints}`(
        @StringForgery fakeHintsText: String
    ) {
        // Given
        val fakeEditText: EditText = mock {
            whenever(it.text).thenReturn(mock())
            whenever(it.hint).thenReturn(fakeHintsText)
        }

        // When
        val textType = testedTextTypeResolver.resolveTextType(fakeEditText, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.HINTS_TEXT)
    }

    @Test
    fun `M resolve as INPUT_TEXT W resolveTextType {EditText, text is null, has hints}`(
        @StringForgery fakeHintsText: String
    ) {
        // Given
        val fakeEditText: EditText = mock {
            whenever(it.text).thenReturn(null)
            whenever(it.hint).thenReturn(fakeHintsText)
        }

        // When
        val textType = testedTextTypeResolver.resolveTextType(fakeEditText, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.HINTS_TEXT)
    }

    @Test
    fun `M resolve as STATIC_TEXT W resolveTextType {TextView, no sensitive}`() {
        // When
        val textType = testedTextTypeResolver.resolveTextType(mockTextView, fakeMappingContext)

        // Then
        assertThat(textType).isEqualTo(TextType.STATIC_TEXT)
    }
}
