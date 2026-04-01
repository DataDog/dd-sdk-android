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
    fun `M produce tab-prefixed frame lines W loggableStackTrace() { matches standard Java printStackTrace format }`() {
        // Given
        val stack = Thread.currentThread().stackTrace

        // When
        val result = stack.loggableStackTrace()

        // Then
        // The standard Java Throwable.printStackTrace() format requires "\tat ClassName.method(File:line)".
        // The Datadog deobfuscation-api retrace tool parses only lines with the "\tat " prefix.
        // This test will FAIL because the current implementation produces "at $it" (no leading tab).
        val lines = result.lines()
        lines.forEach { line ->
            assertThat(line)
                .withFailMessage(
                    "Expected frame line to start with '\\tat ' (tab + 'at ') to match " +
                        "standard Java printStackTrace format required by the deobfuscation-api, " +
                        "but was: '$line'"
                )
                .startsWith("\tat ")
        }
    }
}
