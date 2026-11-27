/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import java.io.PrintWriter
import java.lang.RuntimeException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ThrowableExtTest {

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Test
    fun `M return stack trace W loggableStackTrace() {simple exception}`() {
        // Given
        val stack = fakeThrowable.stackTrace

        // When
        val result = fakeThrowable.loggableStackTrace()

        // Then
        val lines = result.lines()
        assertThat(lines.first()).contains(fakeThrowable.message)
        stack.forEachIndexed { i, frame ->
            assertThat(lines[i + 1])
                .contains(frame.className)
                .contains(frame.methodName)
        }
    }

    @Test
    fun `M return stack trace W loggableStackTrace() {nested exception}`(
        @StringForgery message: String
    ) {
        // Given
        val topThrowable = RuntimeException(message, fakeThrowable)
        val topStack = topThrowable.stackTrace
        val stack = fakeThrowable.stackTrace

        // When
        val result = topThrowable.loggableStackTrace()

        // Then
        val lines = result.lines()
        assertThat(lines.first()).contains(message)
        topStack.forEachIndexed { i, frame ->
            assertThat(lines[i + 1])
                .contains(frame.className)
                .contains(frame.methodName)
        }

        val offset = topStack.size + 1
        assertThat(lines[offset])
            .contains("Caused by")
            .contains(fakeThrowable.message)
        stack.forEachIndexed { i, frame ->
            // When the "Caused by …" stacktrace has common frames with the previous one,
            // those are not displayed and replaced with "… n more"
            // In this test, there are at least 8 non-common frames between the fakeThrowable
            // and topThrowable
            if (i < 8) {
                assertThat(lines[i + offset + 1])
                    .contains(frame.className)
                    .contains(frame.methodName)
            }
        }
    }

    @Test
    fun `M return raw stack frames W loggableStackTrace() {printStackTrace throws before writing}`(
        @StringForgery message: String
    ) {
        // Given
        val throwable = object : Throwable("boom") {
            override fun printStackTrace(s: PrintWriter) {
                error(message)
            }
        }

        // When
        val result = throwable.loggableStackTrace()

        // Then
        assertThat(result).isEqualTo(throwable.stackTrace.loggableStackTrace())
    }

    @Test
    fun `M return partial output W loggableStackTrace() {printStackTrace throws after writing}`(
        @StringForgery fakePartial: String
    ) {
        // Given
        val throwable = object : Throwable("boom") {
            override fun printStackTrace(s: PrintWriter) {
                s.print(fakePartial)
                error("forced failure")
            }
        }

        // When
        val result = throwable.loggableStackTrace()

        // Then
        assertThat(result).isEqualTo(fakePartial)
    }

    @Test
    fun `M return message W loggableMessage()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val throwable = RuntimeException(fakeMessage)

        // When
        val result = throwable.loggableMessage()

        // Then
        assertThat(result).isEqualTo(fakeMessage)
    }

    @Test
    fun `M return null W loggableMessage() {getMessage throws}`() {
        // Given
        val throwable = object : Throwable() {
            override val message: String
                get() = error("boom")
        }

        // When
        val result = throwable.loggableMessage()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return true W isOrIsCausedByOutOfMemoryError() { OutOfMemoryError }`(
        @StringForgery fakeMessage: String
    ) {
        // When
        val result = OutOfMemoryError(fakeMessage).isOrIsCausedByOutOfMemoryError()

        // Then
        assertThat(result).isTrue
    }

    @Test
    fun `M return true W isOrIsCausedByOutOfMemoryError() { OutOfMemoryError deep in the cause chain }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val throwable = RuntimeException(fakeMessage, IllegalStateException(fakeMessage, OutOfMemoryError(fakeMessage)))

        // When
        val result = throwable.isOrIsCausedByOutOfMemoryError()

        // Then
        assertThat(result).isTrue
    }

    @Test
    fun `M return false W isOrIsCausedByOutOfMemoryError() { no OutOfMemoryError }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val throwable = RuntimeException(fakeMessage, IllegalStateException(fakeMessage))

        // When
        val result = throwable.isOrIsCausedByOutOfMemoryError()

        // Then
        assertThat(result).isFalse
    }

    @Test
    fun `M return false W isOrIsCausedByOutOfMemoryError() { cyclic cause chain }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val first = CyclicThrowable(fakeMessage)
        val second = CyclicThrowable(fakeMessage)
        first.nextCause = second
        second.nextCause = first

        // When
        val result = first.isOrIsCausedByOutOfMemoryError()

        // Then
        assertThat(result).isFalse
    }

    private class CyclicThrowable(message: String) : RuntimeException(message) {
        var nextCause: Throwable? = null

        override val cause: Throwable?
            get() = nextCause
    }
}
