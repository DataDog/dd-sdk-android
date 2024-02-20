package com.datadog.android.core.internal.utils

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ThreadExtTest {

    @Test
    fun `M return name W Thread#State#asString()`(
        @Forgery state: Thread.State
    ) {
        // When
        assertThat(state.asString()).isEqualTo(state.name.lowercase())
    }

    @Test
    fun `M return stack trace W loggableStackTrace()`() {
        // Given
        val stack = Thread.currentThread().stackTrace

        // When
        val result = stack.loggableStackTrace()

        // Then
        val lines = result.lines()
        stack.forEachIndexed { i, frame ->
            assertThat(lines[i]).contains(frame.toString())
        }
    }
}
