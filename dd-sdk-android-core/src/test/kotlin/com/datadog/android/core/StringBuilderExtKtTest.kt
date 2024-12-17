package com.datadog.android.core

import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StringBuilderExtKtTest {

    @Test
    fun `M add char W addIfNotEmpty {buffer is not empty}`(
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
    fun `M add str W addIfNotEmpty {buffer is not empty}`(
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
    fun `M not add any char W addIfNotEmpty {buffer is empty}`() {
        // Given
        val buffer = StringBuilder()

        // When
        buffer.appendIfNotEmpty(' ')

        // Then

        assertThat(buffer.toString()).isEqualTo("")
    }

    @Test
    fun `M not add any str W addIfNotEmpty {buffer is empty}`() {
        // Given
        val buffer = StringBuilder()

        // When
        buffer.appendIfNotEmpty(" ")

        // Then

        assertThat(buffer.toString()).isEqualTo("")
    }
}
