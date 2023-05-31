/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules

import android.widget.TextView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.aMockTextView
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class TextValueResolverTest {

    private lateinit var testedResolver: TextValueResolver

    @BeforeEach
    fun `set up`() {
        testedResolver = TextValueResolver()
    }

    @Test
    fun `M resolve to empty String W resolveTextValue(){textView with no text or hint}`(forge: Forge) {
        // Given
        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn("")
        }

        // When
        val resolvedText = testedResolver.resolveTextValue(mockTextView)

        // Then
        assertThat(resolvedText).isEmpty()
    }

    @Test
    fun `M resolve to hint String W resolveTextValue(){textView with hint and no text}`(forge: Forge) {
        // Given
        val fakeExpectedHint = forge.aString()

        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn("")
            whenever(this.hint).thenReturn(fakeExpectedHint)
        }

        // When
        val resolvedText = testedResolver.resolveTextValue(mockTextView)

        // Then
        assertThat(resolvedText).isEqualTo(fakeExpectedHint)
    }

    @Test
    fun `M resolve to text String W resolveTextValue(){textView with text and no hint}`(forge: Forge) {
        // Given
        val fakeExpectedText = forge.aString()

        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeExpectedText)
            whenever(this.hint).thenReturn("")
        }

        // When
        val resolvedText = testedResolver.resolveTextValue(mockTextView)

        // Then
        assertThat(resolvedText).isEqualTo(fakeExpectedText)
    }

    @Test
    fun `M resolve to text String W resolveTextValue(){textView with text and hint}`(forge: Forge) {
        // Given
        val fakeExpectedText = forge.aString()
        val fakeExpectedHint = forge.aString()

        val mockTextView: TextView = forge.aMockTextView().apply {
            whenever(this.text).thenReturn(fakeExpectedText)
            whenever(this.hint).thenReturn(fakeExpectedHint)
        }

        // When
        val resolvedText = testedResolver.resolveTextValue(mockTextView)

        // Then
        assertThat(resolvedText).isEqualTo(fakeExpectedText)
    }
}
