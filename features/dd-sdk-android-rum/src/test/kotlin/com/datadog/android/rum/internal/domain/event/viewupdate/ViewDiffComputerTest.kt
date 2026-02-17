/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class ViewDiffComputerTest {

    private lateinit var testedComputer: ViewDiffComputer

    @BeforeEach
    fun setUp() {
        testedComputer = ViewDiffComputer()
    }

    // region Primitive Field Tests

    @Test
    fun `M detect changed string field W computeDiff()`() {
        // Given
        val lastSent = mapOf("url" to "https://old.com")
        val current = mapOf("url" to "https://new.com")

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("url" to "https://new.com"))
    }

    @Test
    fun `M detect changed integer field W computeDiff()`() {
        // Given
        val lastSent = mapOf("time_spent" to 100)
        val current = mapOf("time_spent" to 200)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("time_spent" to 200))
    }

    @Test
    fun `M detect changed boolean field W computeDiff()`() {
        // Given
        val lastSent = mapOf("is_active" to false)
        val current = mapOf("is_active" to true)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("is_active" to true))
    }

    @Test
    fun `M detect changed double field W computeDiff()`() {
        // Given
        val lastSent = mapOf("sample_rate" to 50.0)
        val current = mapOf("sample_rate" to 75.5)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("sample_rate" to 75.5))
    }

    @Test
    fun `M ignore unchanged fields W computeDiff()`() {
        // Given
        val lastSent = mapOf("url" to "https://example.com", "time_spent" to 100)
        val current = mapOf("url" to "https://example.com", "time_spent" to 100)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEmpty()
    }

    @Test
    fun `M detect multiple changed fields W computeDiff()`() {
        // Given
        val lastSent = mapOf("url" to "https://old.com", "time_spent" to 100, "count" to 5)
        val current = mapOf("url" to "https://new.com", "time_spent" to 200, "count" to 5)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("url" to "https://new.com", "time_spent" to 200))
    }

    // endregion

    // region Nested Object Tests

    @Test
    fun `M detect changed nested field W computeDiff()`() {
        // Given
        val lastSent = mapOf("view" to mapOf("action" to mapOf("count" to 0)))
        val current = mapOf("view" to mapOf("action" to mapOf("count" to 1)))

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(
            mapOf("view" to mapOf("action" to mapOf("count" to 1)))
        )
    }

    @Test
    fun `M preserve unchanged nested fields W computeDiff()`() {
        // Given
        val lastSent = mapOf(
            "view" to mapOf(
                "action" to mapOf("count" to 1),
                "error" to mapOf("count" to 0)
            )
        )
        val current = mapOf(
            "view" to mapOf(
                "action" to mapOf("count" to 2),
                "error" to mapOf("count" to 0)
            )
        )

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        // Only changed nested field included
        assertThat(diff).isEqualTo(
            mapOf("view" to mapOf("action" to mapOf("count" to 2)))
        )
    }

    @Test
    fun `M handle deeply nested changes W computeDiff()`() {
        // Given
        val lastSent = mapOf(
            "view" to mapOf(
                "performance" to mapOf(
                    "lcp" to mapOf("timestamp" to 100)
                )
            )
        )
        val current = mapOf(
            "view" to mapOf(
                "performance" to mapOf(
                    "lcp" to mapOf("timestamp" to 341)
                )
            )
        )

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(
            mapOf("view" to mapOf("performance" to mapOf("lcp" to mapOf("timestamp" to 341))))
        )
    }

    @Test
    fun `M handle empty nested object W computeDiff()`() {
        // Given
        val lastSent = mapOf("view" to mapOf("action" to mapOf("count" to 1)))
        val current = mapOf("view" to mapOf<String, Any?>())

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        // When a nested field is removed, it should be reported as null
        assertThat(diff).isEqualTo(mapOf("view" to mapOf("action" to null)))
    }

    // endregion

    // region Array Tests

    @Test
    fun `M detect new array elements W computeDiff()`() {
        // Given
        val lastSent = mapOf(
            "slow_frames" to listOf(
                mapOf("start" to 100, "duration" to 20)
            )
        )
        val current = mapOf(
            "slow_frames" to listOf(
                mapOf("start" to 100, "duration" to 20),
                mapOf("start" to 250, "duration" to 15)
            )
        )

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(
            mapOf("slow_frames" to listOf(mapOf("start" to 250, "duration" to 15)))
        )
    }

    @Test
    fun `M return only new array elements W computeDiff()`() {
        // Given
        val lastSent = mapOf("frames" to listOf(1, 2, 3))
        val current = mapOf("frames" to listOf(1, 2, 3, 4, 5))

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("frames" to listOf(4, 5)))
    }

    @Test
    fun `M ignore unchanged arrays W computeDiff()`() {
        // Given
        val lastSent = mapOf("frames" to listOf(1, 2, 3))
        val current = mapOf("frames" to listOf(1, 2, 3))

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEmpty()
    }

    @Test
    fun `M handle empty arrays W computeDiff()`() {
        // Given
        val lastSent = mapOf("frames" to emptyList<Any>())
        val current = mapOf("frames" to listOf(1, 2))

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("frames" to listOf(1, 2)))
    }

    @Test
    fun `M handle array shrinking W computeDiff()`() {
        // Given (edge case - shouldn't happen in RUM)
        val lastSent = mapOf("frames" to listOf(1, 2, 3))
        val current = mapOf("frames" to listOf(1, 2))

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        // When array shrinks, send full current array
        assertThat(diff).isEqualTo(mapOf("frames" to listOf(1, 2)))
    }

    @Test
    fun `M handle array element changes W computeDiff()`() {
        // Given (same size but different elements)
        val lastSent = mapOf("frames" to listOf(1, 2, 3))
        val current = mapOf("frames" to listOf(1, 2, 4))

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        // Elements changed: send full array
        assertThat(diff).isEqualTo(mapOf("frames" to listOf(1, 2, 4)))
    }

    // endregion

    // region Null Handling Tests

    @Test
    fun `M detect field deletion W computeDiff()`() {
        // Given
        val lastSent = mapOf("loading_time" to 200)
        val current = mapOf("loading_time" to null)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("loading_time" to null))
    }

    @Test
    fun `M detect field addition W computeDiff()`() {
        // Given
        val lastSent = mapOf("url" to "https://example.com")
        val current = mapOf("url" to "https://example.com", "loading_time" to 200)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("loading_time" to 200))
    }

    @Test
    fun `M handle both values null W computeDiff()`() {
        // Given
        val lastSent = mapOf("optional_field" to null)
        val current = mapOf("optional_field" to null)

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEmpty()
    }

    @Test
    fun `M detect removed field W computeDiff()`() {
        // Given (field present in lastSent but missing in current)
        val lastSent = mapOf("url" to "https://example.com", "loading_time" to 200)
        val current = mapOf("url" to "https://example.com")

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(mapOf("loading_time" to null))
    }

    // endregion

    // region Complex Scenarios

    @Test
    fun `M handle mix of primitives, objects, and arrays W computeDiff()`() {
        // Given
        val lastSent = mapOf(
            "time_spent" to 100,
            "view" to mapOf("action" to mapOf("count" to 1)),
            "slow_frames" to listOf(mapOf("start" to 100))
        )
        val current = mapOf(
            "time_spent" to 200,
            "view" to mapOf("action" to mapOf("count" to 1)),
            "slow_frames" to listOf(
                mapOf("start" to 100),
                mapOf("start" to 250)
            )
        )

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).isEqualTo(
            mapOf(
                "time_spent" to 200,
                "slow_frames" to listOf(mapOf("start" to 250))
            )
        )
    }

    @Test
    fun `M return empty map W nothing changed`() {
        // Given
        val viewData = mapOf(
            "time_spent" to 100,
            "view" to mapOf("action" to mapOf("count" to 1)),
            "slow_frames" to listOf(mapOf("start" to 100))
        )

        // When
        val diff = testedComputer.computeDiff(viewData, viewData)

        // Then
        assertThat(diff).isEmpty()
    }

    @Test
    fun `M handle large view data W computeDiff()`() {
        // Given - simulate typical view with 100+ fields
        val lastSent = mutableMapOf<String, Any?>()
        val current = mutableMapOf<String, Any?>()

        // Add 100 fields
        for (i in 0 until 100) {
            lastSent["field_$i"] = "value_$i"
            current["field_$i"] = "value_$i"
        }

        // Change 5 fields
        current["field_10"] = "changed_10"
        current["field_25"] = "changed_25"
        current["field_50"] = "changed_50"
        current["field_75"] = "changed_75"
        current["field_99"] = "changed_99"

        // When
        val diff = testedComputer.computeDiff(lastSent, current)

        // Then
        assertThat(diff).hasSize(5)
        assertThat(diff).containsEntry("field_10", "changed_10")
        assertThat(diff).containsEntry("field_25", "changed_25")
        assertThat(diff).containsEntry("field_50", "changed_50")
        assertThat(diff).containsEntry("field_75", "changed_75")
        assertThat(diff).containsEntry("field_99", "changed_99")
    }

    // endregion

    // region Performance Tests

    @Test
    fun `M complete in under 5ms W computeDiff() for typical view`() {
        // Given - create a typical view with 150 fields
        val typicalView = mutableMapOf<String, Any?>()

        // Add primitive fields
        for (i in 0 until 50) {
            typicalView["string_field_$i"] = "value_$i"
        }
        for (i in 0 until 25) {
            typicalView["int_field_$i"] = i
        }
        for (i in 0 until 25) {
            typicalView["bool_field_$i"] = (i % 2 == 0)
        }

        // Add nested objects
        typicalView["view"] = mapOf(
            "action" to mapOf("count" to 5),
            "resource" to mapOf("count" to 10),
            "error" to mapOf("count" to 0)
        )

        // Add arrays
        typicalView["slow_frames"] = listOf(
            mapOf("start" to 100, "duration" to 20),
            mapOf("start" to 250, "duration" to 15)
        )

        val updatedView = typicalView.toMutableMap().apply {
            put("time_spent", 200)
            put("string_field_10", "changed")
            put("int_field_5", 999)
        }

        // When - measure time
        val startTime = System.nanoTime()
        val diff = testedComputer.computeDiff(typicalView, updatedView)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        // Then
        assertThat(elapsedMs).isLessThan(5.0)
        assertThat(diff).hasSize(3)
    }

    @Test
    fun `M complete quickly W computeDiff() for unchanged large view`() {
        // Given - large view with no changes
        val largeView = mutableMapOf<String, Any?>()
        for (i in 0 until 200) {
            largeView["field_$i"] = "value_$i"
        }

        // When
        val startTime = System.nanoTime()
        val diff = testedComputer.computeDiff(largeView, largeView)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        // Then
        assertThat(elapsedMs).isLessThan(5.0)
        assertThat(diff).isEmpty()
    }

    // endregion
}
