/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
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

    @RepeatedTest(16)
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

    @Test
    fun `M use tab prefix W loggableStackTrace() { each line must start with tab+at for deobfuscation }`() {
        // Given
        // The Datadog backend deobfuscation-api retrace tool only processes stack frame lines
        // that start with "\tat " (tab + "at "). Lines starting with "at " (no tab) are ignored
        // and left obfuscated. This test verifies Array<StackTraceElement>.loggableStackTrace()
        // produces the standard JVM format required for deobfuscation to work correctly.
        val stack = Thread.currentThread().stackTrace

        // When
        val result = stack.loggableStackTrace()

        // Then
        val lines = result.lines()
        lines.forEachIndexed { index, line ->
            assertThat(line)
                .withFailMessage(
                    "Line $index in loggableStackTrace() output does not start with \"\\tat \" " +
                        "(tab + at). The backend deobfuscation-api retrace tool requires this " +
                        "prefix to recognize and deobfuscate stack frames. " +
                        "Actual line: \"$line\""
                )
                .startsWith("\tat ")
        }
    }
}
