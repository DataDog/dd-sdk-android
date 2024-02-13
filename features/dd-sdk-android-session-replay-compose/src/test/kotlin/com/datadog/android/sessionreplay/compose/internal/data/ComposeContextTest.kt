/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.data

import androidx.compose.runtime.tooling.CompositionGroup
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ComposeContextTest {

    @ParameterizedTest(name = "M parse the source info W parse {0}")
    @MethodSource("provideTestArguments")
    fun `M parse the compositionGroup sourceInfo W from()`(
        fakeSource: String,
        expectedOutput: ComposeContext
    ) {
        // Given
        val group: CompositionGroup = mock()
        whenever(group.sourceInfo) doReturn fakeSource

        // When
        val value = ComposeContext.from(group)

        // Then
        assertThat(value).isEqualTo(expectedOutput)
    }

    @Test
    fun `M return null W from() { invalid sourceInfo}`(
        @StringForgery fakeSource: String
    ) {
        // Given
        val group: CompositionGroup = mock()
        whenever(group.sourceInfo) doReturn fakeSource

        // When
        val value = ComposeContext.from(group)

        // Then
        assertThat(value).isNull()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun provideTestArguments(): Stream<Arguments> {
            return ComposeContextLexerTest.provideTestArguments()
        }
    }
}
