package com.datadog.android.core.internal.utils

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
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
        assertThat(lines.get(offset))
            .contains("Caused by")
            .contains(fakeThrowable.message)
        stack.forEachIndexed { i, frame ->
            // When the "Caused by …" stacktrace has common frames with the previous one,
            // those are not displayed and replaced with "… n more"
            // In this test, there are at least 8 non common frames between the fakeThrowable
            // and topThrowable
            if (i < 8) {
                assertThat(lines[i + offset + 1])
                    .contains(frame.className)
                    .contains(frame.methodName)
            }
        }
    }
}
