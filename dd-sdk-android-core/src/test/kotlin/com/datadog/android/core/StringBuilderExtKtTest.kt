package com.datadog.android.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StringBuilderExtKtTest {

    @Test
    fun `M add char W addIfNotEmpty {buffer is not empty}`() {
        // Given
        val initialData = "something inside buffer already"
        val buffer = StringBuilder(initialData)

        // When
        buffer.appendIfNotEmpty(' ')

        // Then

        assertThat(buffer.toString()).isEqualTo("$initialData ")
    }

    @Test
    fun `M add str W addIfNotEmpty {buffer is not empty}`() {
        // Given
        val initialData = "something inside buffer already"
        val buffer = StringBuilder(initialData)

        // When
        buffer.appendIfNotEmpty(" ")

        // Then

        assertThat(buffer.toString()).isEqualTo("$initialData ")
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
