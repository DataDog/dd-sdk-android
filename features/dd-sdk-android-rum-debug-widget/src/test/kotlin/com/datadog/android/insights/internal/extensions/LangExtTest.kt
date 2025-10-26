/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal.extensions

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import kotlin.math.pow
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class LangExtTest {

    @Test
    fun `M convert nanos to millis W ms`(@LongForgery fakeNanos: Long) {
        assertThat(fakeNanos.ms).isEqualTo(fakeNanos / NANOS_PER_MILLI)
    }

    @Test
    fun `M convert bytes to megabytes W Mb`(@DoubleForgery fakeBytes: Double) {
        assertThat(fakeBytes.Mb).isEqualTo(fakeBytes / BYTES_PER_MB)
    }

    @Test
    fun `M return NaN W round {null}`() {
        // When
        val tested = (null as Double?).round(2)

        // Then
        assertThat(tested).isNaN
    }

    @Test
    fun `M return NaN W round {NaN}`() {
        // When
        val tested = Double.NaN.round(3)

        // Then
        assertThat(tested).isNaN
    }

    @Test
    fun `M round to given digits W round`(
        @DoubleForgery fakeValue: Double,
        @IntForgery(min = 0, max = 6) digits: Int
    ) {
        val multiplier = 10.0.pow(digits)
        val expected = (fakeValue * multiplier).roundToInt() / multiplier

        val tested = fakeValue.round(digits)

        assertThat(tested).isEqualTo(expected)
    }

    @Test
    fun `M clip value inside {min, max - size} W clip`(
        @IntForgery(min = 0, max = 200) size: Int,
        @IntForgery(min = 0, max = 500) minBoundSeed: Int,
        @IntForgery(min = 1, max = 500) spanSeed: Int,
        @FloatForgery(min = -500f, max = 1000f) insideXSeed: Float
    ) {
        // Ensure invariant min < max and room for 'size'
        val minBound = minBoundSeed
        val maxBound = (minBound + spanSeed + size).coerceAtLeast(minBound + size + 1)

        // Below-min case
        assertThat((-10f).clip(size, minBound, maxBound)).isEqualTo(minBound.toFloat())

        // Inside case: clamp an arbitrary inside value into range [min, max - size]
        val upper = (maxBound - size).toFloat()
        val insideX = insideXSeed.coerceIn(minBound.toFloat(), upper)
        assertThat(insideX.clip(size, minBound, maxBound)).isEqualTo(insideX)

        // Above-max case (beyond usable right edge)
        assertThat((upper + 100f).clip(size, minBound, maxBound)).isEqualTo(upper)
    }

    @Test
    fun `M invoke block only when both non-null W multiLet()`(
        @StringForgery fakeA: String,
        @IntForgery(min = -1000, max = 1000) fakeB: Int
    ) {
        var called = false

        // both non-null -> called
        multiLet(fakeA, fakeB) { _, _ -> called = true }
        assertThat(called).isTrue()

        // one null -> not called
        called = false
        multiLet(null as String?, fakeB) { _, _ -> called = true }
        assertThat(called).isFalse()

        called = false
        multiLet(fakeA, null as Int?) { _, _ -> called = true }
        assertThat(called).isFalse()
    }

    // endregion

    // region SpannableStringBuilder.appendColored

    @Test
    fun `M append text and set ForegroundColorSpan W appendColored {non-empty}`(
        @StringForgery fakeText: String,
        @IntForgery fakeColor: Int
    ) {
        val testedBuilder = SpannableStringBuilder()

        testedBuilder.appendColored(fakeText, fakeColor)

        assertThat(testedBuilder.toString()).isEqualTo(fakeText)

        val spans = testedBuilder.getSpans(0, testedBuilder.length, ForegroundColorSpan::class.java)
        assertThat(spans).hasSize(1)
        val span = spans.single()
        assertThat(testedBuilder.getSpanStart(span)).isEqualTo(0)
        assertThat(testedBuilder.getSpanEnd(span)).isEqualTo(fakeText.length)
        assertThat(span.foregroundColor).isEqualTo(fakeColor)
        assertThat(testedBuilder.getSpanFlags(span)).isEqualTo(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    @Test
    fun `M no-op W appendColored {empty}`() {
        // Given
        val testedBuilder = SpannableStringBuilder("Xeifba")

        // When
        testedBuilder.appendColored("", 123)

        // Then
        assertThat(testedBuilder.toString()).isEqualTo("X")
        val spans = testedBuilder.getSpans(0, testedBuilder.length, ForegroundColorSpan::class.java)
        assertThat(spans).isEmpty()
    }
}
