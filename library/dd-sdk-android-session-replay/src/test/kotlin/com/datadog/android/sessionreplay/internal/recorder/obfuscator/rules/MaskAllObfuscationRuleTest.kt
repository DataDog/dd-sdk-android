/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MaskAllObfuscationRuleTest : BaseObfuscationRuleTest() {

    lateinit var testedRule: MaskAllObfuscationRule

    @BeforeEach
    fun `set up`() {
        super.setUp()
        testedRule = MaskAllObfuscationRule(
            defaultStringObfuscator = mockDefaultStringObfuscator,
            fixedLengthStringObfuscator = mockFixedLengthStringObfuscator,
            textTypeResolver = mockTextTypeResolver,
            textValueResolver = mockTextValueResolver
        )
    }

    @Test
    fun `M resolve as fix length mask W resolveObfuscatedValue { SENSITIVE_TEXT }`() {
        // Given
        whenever(mockTextTypeResolver.resolveTextType(mockTextView))
            .thenReturn(TextType.SENSITIVE_TEXT)

        // When
        val obfuscatedTextValue = testedRule.resolveObfuscatedValue(mockTextView)

        // Then
        assertThat(obfuscatedTextValue).isEqualTo(fakeFixedLengthMask)
    }

    @Test
    fun `M resolve as fix length mask W resolveObfuscatedValue { INPUT_TEXT }`() {
        // Given
        whenever(mockTextTypeResolver.resolveTextType(mockTextView))
            .thenReturn(TextType.INPUT_TEXT)

        // When
        val obfuscatedTextValue = testedRule.resolveObfuscatedValue(mockTextView)

        // Then
        assertThat(obfuscatedTextValue).isEqualTo(fakeFixedLengthMask)
    }

    @Test
    fun `M resolve as fix length mask W resolveObfuscatedValue { OPTION_TEXT }`() {
        // Given
        whenever(mockTextTypeResolver.resolveTextType(mockTextView))
            .thenReturn(TextType.OPTION_TEXT)

        // When
        val obfuscatedTextValue = testedRule.resolveObfuscatedValue(mockTextView)

        // Then
        assertThat(obfuscatedTextValue).isEqualTo(fakeFixedLengthMask)
    }

    @Test
    fun `M resolve as fix length mask W resolveObfuscatedValue { HINTS_TEXT }`() {
        // Given
        whenever(mockTextTypeResolver.resolveTextType(mockTextView))
            .thenReturn(TextType.HINTS_TEXT)

        // When
        val obfuscatedTextValue = testedRule.resolveObfuscatedValue(mockTextView)

        // Then
        assertThat(obfuscatedTextValue).isEqualTo(fakeFixedLengthMask)
    }

    @Test
    fun `M resolve as preserved length text mask W resolveObfuscatedValue { STATIC_TEXT }`() {
        // Given
        whenever(mockTextTypeResolver.resolveTextType(mockTextView))
            .thenReturn(TextType.STATIC_TEXT)

        // When
        val obfuscatedTextValue = testedRule.resolveObfuscatedValue(mockTextView)

        // Then
        assertThat(obfuscatedTextValue).isEqualTo(fakeDefaultMask)
    }
}
