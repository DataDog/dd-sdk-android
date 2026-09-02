/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Test fixtures — a simple nested data model used across all tests.
 */
private data class State(
    val value: Int,
    val label: String,
    val tags: Map<String, String>,
    val items: List<Int>,
    val nested: Nested?
) {
    data class Nested(val id: String, val count: Int)
}

private data class StateDiff(
    val value: Int?,
    val label: String?,
    val tags: Map<String, String>,
    val items: List<Int>?,
    val nested: NestedDiff?
) {
    data class NestedDiff(val id: String, val count: Int?)
}

private fun diffNested(old: State.Nested, new: State.Nested): StateDiff.NestedDiff? {
    return computeDiffIfChanged(old, new) {
        StateDiff.NestedDiff(
            id = diffRequired(State.Nested::id),
            count = diffEquals(State.Nested::count)
        )
    }
}

private fun diffState(old: State, new: State): StateDiff? {
    return computeDiffIfChanged(old, new) {
        StateDiff(
            value = diffEquals(State::value),
            label = diffEquals(State::label),
            tags = diffMap(State::tags),
            items = diffList(State::items),
            nested = if (old.nested != null && new.nested != null) {
                diffMerge({ nested!! }, ::diffNested)
            } else {
                diffEquals(State::nested)?.let { diffNested(State.Nested("", 0), it) }
            }
        )
    }
}

class DiffBuilderTest {

    // region computeDiffIfChanged

    @Test
    fun `M return null W computeDiffIfChanged { no fields changed, no nested }`() {
        // Given — no nested object so diffRequired is never triggered
        val state = State(1, "hello", mapOf("k" to "v"), listOf(1, 2), nested = null)

        // When
        val result = diffState(state, state.copy())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return diff W computeDiffIfChanged { at least one field changed }`() {
        // Given
        val old = State(1, "hello", emptyMap(), emptyList(), null)
        val new = old.copy(value = 2)

        // When
        val result = diffState(old, new)

        // Then
        assertThat(result).isNotNull()
    }

    // endregion

    // region computeDiffRequired

    @Test
    fun `M always return result W computeDiffRequired { no fields changed }`() {
        // Given
        val nested = State.Nested("id", 5)

        // When
        val result = computeDiffRequired(nested, nested.copy()) {
            StateDiff.NestedDiff(
                id = diffRequired(State.Nested::id),
                count = diffEquals(State.Nested::count)
            )
        }

        // Then
        assertThat(result).isNotNull()
        assertThat(result.id).isEqualTo("id")
        assertThat(result.count).isNull()
    }

    // endregion

    // region diffEquals

    @Test
    fun `M return null W diffEquals { value unchanged }`() {
        // Given
        val old = State(42, "hello", emptyMap(), emptyList(), null)
        val new = old.copy()

        // When
        val result = diffState(old, new)

        // Then — nothing changed so entire diff is null
        assertThat(result).isNull()
    }

    @Test
    fun `M return new value W diffEquals { value changed }`() {
        // Given
        val old = State(1, "hello", emptyMap(), emptyList(), null)
        val new = old.copy(value = 99)

        // When
        val result = diffState(old, new)

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.value).isEqualTo(99)
        assertThat(result.label).isNull() // label did not change
    }

    @Test
    fun `M return null for field W diffEquals { only other field changed }`() {
        // Given
        val old = State(1, "hello", emptyMap(), emptyList(), null)
        val new = old.copy(label = "world")

        // When
        val result = diffState(old, new)!!

        // Then
        assertThat(result.value).isNull()
        assertThat(result.label).isEqualTo("world")
    }

    // endregion

    // region diffMap

    @Test
    fun `M return empty map W diffMap { map unchanged }`() {
        // Given
        val tags = mapOf("env" to "prod", "version" to "1.0")
        val old = State(1, "a", tags, emptyList(), null)
        val new = old.copy()

        // When
        val result = diffState(old, new)

        // Then — nothing changed so entire diff is null
        assertThat(result).isNull()
    }

    @Test
    fun `M return only changed entries W diffMap { one entry changed }`() {
        // Given
        val old = State(1, "a", mapOf("env" to "prod", "version" to "1.0"), emptyList(), null)
        val new = old.copy(tags = mapOf("env" to "prod", "version" to "2.0"))

        // When
        val result = diffState(old, new)!!

        // Then
        assertThat(result.tags).containsOnly(entry("version", "2.0"))
    }

    @Test
    fun `M return only new entries W diffMap { entry added }`() {
        // Given
        val old = State(1, "a", mapOf("env" to "prod"), emptyList(), null)
        val new = old.copy(tags = mapOf("env" to "prod", "region" to "us-east"))

        // When
        val result = diffState(old, new)!!

        // Then
        assertThat(result.tags).containsOnly(entry("region", "us-east"))
    }

    @Test
    fun `M not include deleted entries W diffMap { entry removed }`() {
        // Given
        val old = State(1, "a", mapOf("env" to "prod", "version" to "1.0"), emptyList(), null)
        val new = old.copy(tags = mapOf("env" to "prod"))

        // When
        val result = diffState(old, new)

        // Then — deletions are not tracked, so no change detected
        assertThat(result).isNull()
    }

    // endregion

    // region diffList

    @Test
    fun `M return null W diffList { list unchanged }`() {
        // Given
        val old = State(1, "a", emptyMap(), listOf(1, 2, 3), null)
        val new = old.copy()

        // When
        val result = diffState(old, new)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return only appended elements W diffList { elements appended }`() {
        // Given
        val old = State(1, "a", emptyMap(), listOf(1, 2, 3), null)
        val new = old.copy(items = listOf(1, 2, 3, 4, 5))

        // When
        val result = diffState(old, new)!!

        // Then
        assertThat(result.items).containsExactly(4, 5)
    }

    @Test
    fun `M return null W diffList { list shrunk (not tracked) }`() {
        // Given
        val old = State(1, "a", emptyMap(), listOf(1, 2, 3), null)
        val new = old.copy(items = listOf(1, 2))

        // When
        val result = diffState(old, new)

        // Then — shrinking is not tracked
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W diffList { null list treated as empty }`() {
        // Given
        val old = State(1, "a", emptyMap(), emptyList(), null)
        val new = old.copy(items = emptyList())

        // When
        val result = diffState(old, new)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region diffMerge

    @Test
    fun `M return null W diffMerge { nested is null in both old and new }`() {
        // Given
        val old = State(1, "a", emptyMap(), emptyList(), nested = null)
        val new = old.copy()

        // When
        val result = diffState(old, new)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W diffMerge { nested unchanged with diffRequired field }`() {
        // Given — nested is unchanged; diffRequired only marks changed if value differs
        val old = State(1, "a", emptyMap(), emptyList(), State.Nested("id", 5))
        val new = old.copy()

        // When
        val result = diffState(old, new)

        // Then — no change detected despite diffRequired, because id value is the same
        assertThat(result).isNull()
    }

    @Test
    fun `M return nested diff W diffMerge { nested field changed }`() {
        // Given
        val old = State(1, "a", emptyMap(), emptyList(), State.Nested("id", 5))
        val new = old.copy(nested = State.Nested("id", 10))

        // When
        val result = diffState(old, new)!!

        // Then
        assertThat(result.nested).isNotNull()
        val nested = result.nested!!
        assertThat(nested.id).isEqualTo("id") // id is diffRequired — always present
        assertThat(nested.count).isEqualTo(10)
    }

    @Test
    fun `M propagate change to parent W diffMerge { only nested field changed }`() {
        // Given
        val old = State(1, "a", emptyMap(), emptyList(), State.Nested("id", 5))
        val new = old.copy(nested = State.Nested("id", 99))

        // When
        val result = diffState(old, new)

        // Then — nested change propagates changed=true to parent
        assertThat(result).isNotNull()
        assertThat(result!!.value).isNull()
        assertThat(result.label).isNull()
        assertThat(result.nested).isNotNull()
    }

    // endregion

    // region diffRequired

    @Test
    fun `M always include field W diffRequired { value unchanged }`() {
        // Given
        val nested = State.Nested("my-id", 5)

        // When
        val result = computeDiffRequired(nested, nested.copy()) {
            StateDiff.NestedDiff(
                id = diffRequired(State.Nested::id),
                count = diffEquals(State.Nested::count)
            )
        }

        // Then — id always present even though nothing changed
        assertThat(result.id).isEqualTo("my-id")
        assertThat(result.count).isNull()
    }

    @Test
    fun `M return null W diffRequired { value unchanged }`() {
        // Given
        val nested = State.Nested("my-id", 5)

        // When
        val result = computeDiffIfChanged(nested, nested.copy()) {
            StateDiff.NestedDiff(
                id = diffRequired(State.Nested::id),
                count = diffEquals(State.Nested::count)
            )
        }

        // Then — diffRequired does NOT mark changed when value is the same
        assertThat(result).isNull()
    }

    @Test
    fun `M mark changed and return new value W diffRequired { value changed }`() {
        // Given
        val old = State.Nested("old-id", 5)
        val new = State.Nested("new-id", 5)

        // When
        val result = computeDiffIfChanged(old, new) {
            StateDiff.NestedDiff(
                id = diffRequired(State.Nested::id),
                count = diffEquals(State.Nested::count)
            )
        }

        // Then — diffRequired marks changed when value differs, and returns the new value
        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo("new-id")
        assertThat(result.count).isNull()
    }

    // endregion

    // region multiple fields

    @Test
    fun `M capture all changed fields W diffState { multiple fields changed }`() {
        // Given
        val old = State(
            value = 1,
            label = "old",
            tags = mapOf("env" to "staging"),
            items = listOf(10),
            nested = State.Nested("id", 1)
        )
        val new = State(
            value = 2,
            label = "new",
            tags = mapOf("env" to "prod"),
            items = listOf(10, 20),
            nested = State.Nested("id", 2)
        )

        // When
        val result = diffState(old, new)!!

        // Then
        assertThat(result.value).isEqualTo(2)
        assertThat(result.label).isEqualTo("new")
        assertThat(result.tags).containsOnly(entry("env", "prod"))
        assertThat(result.items).containsExactly(20)
        assertThat(result.nested!!.count).isEqualTo(2)
    }

    // endregion
}

private fun entry(key: String, value: String) =
    org.assertj.core.data.MapEntry.entry(key, value)
