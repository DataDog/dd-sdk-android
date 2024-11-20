/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.text.AnnotatedString
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
class StringUtilsTest {
    @Test
    fun `M return null W resolveAnnotatedString with null value`() {
        // When
        val result = resolveAnnotatedString(null)

        // Then
        assertThat("null").isEqualTo(result)
    }

    @Test
    fun `M return correct text W resolveAnnotatedString with plain AnnotatedString`(
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleText: String
    ) {
        // Given
        val annotatedString = AnnotatedString(fakeSimpleText)

        // When
        val result = resolveAnnotatedString(annotatedString)

        // Then
        assertThat(fakeSimpleText).isEqualTo(result)
    }

    @Test
    fun `M return empty text W resolveAnnotatedString with empty collection`() {
        // When
        val result = resolveAnnotatedString(emptyList<Any>())

        // Then
        assertThat("").isEqualTo(result)
    }

    @Test
    fun `M return correct text W resolveAnnotatedString with collection of plain strings`(
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleTextA: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleTextB: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleTextC: String
    ) {
        // When
        val result =
            resolveAnnotatedString(listOf(fakeSimpleTextA, fakeSimpleTextB, fakeSimpleTextC))

        // Then
        assertThat(fakeSimpleTextA + fakeSimpleTextB + fakeSimpleTextC).isEqualTo(result)
    }

    @Test
    fun `M return correct text W resolveAnnotatedString with mixed collection`(
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleTextA: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleTextB: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleTextC: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeSimpleTextD: String
    ) {
        // Given
        val collection = listOf(
            fakeSimpleTextA,
            AnnotatedString(fakeSimpleTextB),
            listOf(fakeSimpleTextC, AnnotatedString(fakeSimpleTextD))
        )

        // When
        val result = resolveAnnotatedString(collection)

        // Then
        assertThat(fakeSimpleTextA + fakeSimpleTextB + fakeSimpleTextC + fakeSimpleTextD)
            .isEqualTo(result)
    }

    @Test
    fun `M return correct text W resolveAnnotatedString with other object type`(
        @IntForgery fakeNumber: Int
    ) {
        // When
        val result = resolveAnnotatedString(fakeNumber)

        // Then
        assertThat(fakeNumber.toString()).isEqualTo(result)
    }
}
