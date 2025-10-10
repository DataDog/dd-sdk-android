/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class StringBuilderExtKtTest {

    @Test
    fun `M add char W appendIfNotEmpty {buffer is not empty}`(
        @StringForgery(regex = ".+") initialContent: String
    ) {
        // Given
        val buffer = StringBuilder(initialContent)

        // When
        buffer.appendIfNotEmpty(' ')

        // Then
        assertThat(buffer.toString()).isEqualTo("$initialContent ")
    }

    @Test
    fun `M add str W appendIfNotEmpty {buffer is not empty}`(
        @StringForgery(regex = ".+") initialContent: String
    ) {
        // Given
        val buffer = StringBuilder(initialContent)

        // When
        buffer.appendIfNotEmpty(" ")

        // Then
        assertThat(buffer.toString()).isEqualTo("$initialContent ")
    }

    @Test
    fun `M not add any char W appendIfNotEmpty {buffer is empty}`() {
        // Given
        val buffer = StringBuilder()

        // When
        buffer.appendIfNotEmpty(' ')

        // Then
        assertThat(buffer.toString()).isEqualTo("")
    }

    @Test
    fun `M not add any str W appendIfNotEmpty {buffer is empty}`() {
        // Given
        val buffer = StringBuilder()

        // When
        buffer.appendIfNotEmpty(" ")

        // Then
        assertThat(buffer.toString()).isEqualTo("")
    }
}
