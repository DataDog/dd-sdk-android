/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

/**
 * Computes the difference between two view states to enable partial view updates.
 *
 * This class implements the diff algorithm that identifies which fields have changed
 * between the last sent view event and the current view state. The resulting diff
 * contains only the changed fields, which are sent in a view_update event.
 *
 * **Algorithm:**
 * - Primitives: Include if value changed
 * - Objects: Recurse and include nested changes only
 * - Arrays: Include only newly appended elements
 * - Null: Include to signal field deletion
 *
 * **Performance:** Designed to complete in <5ms for typical views (100-150 fields)
 */
internal class ViewDiffComputer {

    /**
     * Computes the difference between last sent and current view state.
     * Returns a map containing only changed fields.
     *
     * The diff algorithm processes each field in the current state and compares it
     * with the corresponding field in the last sent state. Based on the field type
     * and values, it determines whether to include the field in the diff.
     *
     * **Rules:**
     * - Primitives (String, Int, Boolean, etc.): Include if value changed
     * - Objects (nested Map): Recurse and include only changed nested fields
     * - Arrays (List): Include only newly appended elements
     * - Null: Include to signal field deletion
     *
     * **Performance:** O(n) where n is the number of fields in the view.
     * Typical execution time: 1-3ms for 100-150 fields.
     *
     * @param lastSent The last sent event data (baseline for comparison)
     * @param current The current view state (target state)
     * @return Map containing only changed fields, empty if no changes
     */
    fun computeDiff(
        lastSent: Map<String, Any?>,
        current: Map<String, Any?>
    ): Map<String, Any?> {
        val diff = mutableMapOf<String, Any?>()

        // Check all fields in current state
        for ((key, currentValue) in current) {
            val lastValue = lastSent[key]

            when {
                // No change: skip this field
                currentValue == lastValue -> continue

                // Both are lists: check for appended elements
                currentValue is List<*> && lastValue is List<*> -> {
                    val newElements = getNewArrayElements(lastValue, currentValue)
                    if (newElements.isNotEmpty()) {
                        diff[key] = newElements
                    }
                }

                // Both are maps: recurse for nested objects
                currentValue is Map<*, *> && lastValue is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val nestedDiff = computeDiff(
                        lastValue as Map<String, Any?>,
                        currentValue as Map<String, Any?>
                    )
                    if (nestedDiff.isNotEmpty()) {
                        diff[key] = nestedDiff
                    }
                }

                // Field deleted (null in current, non-null in last)
                currentValue == null && lastValue != null -> {
                    diff[key] = null
                }

                // Field added (null in last, non-null in current)
                currentValue != null && lastValue == null -> {
                    diff[key] = currentValue
                }

                // All other cases: value changed (primitives, type changes, etc.)
                else -> {
                    diff[key] = currentValue
                }
            }
        }

        // Check for deleted fields (present in lastSent but not in current)
        for (key in lastSent.keys) {
            if (key !in current && lastSent[key] != null) {
                diff[key] = null
            }
        }

        return diff
    }

    /**
     * For arrays, returns only newly appended elements.
     * Assumes arrays only grow (elements are not removed or reordered).
     *
     * This optimization is based on the RUM use case where arrays like
     * slow_frames, page_states, and in_foreground_periods only grow by
     * appending new elements at the end.
     *
     * **Edge cases:**
     * - Array grew: Return only new elements at the end
     * - Array shrunk: Return full current array (unexpected but handled)
     * - Same size but different elements: Return full current array
     * - Same size and identical: Return empty list
     *
     * @param lastSent Previously sent array
     * @param current Current array state
     * @return List containing only new elements, empty if array didn't grow
     */
    private fun getNewArrayElements(
        lastSent: List<*>,
        current: List<*>
    ): List<*> {
        return when {
            current.size > lastSent.size -> {
                // Array grew: return elements added at the end
                current.subList(lastSent.size, current.size)
            }
            current.size < lastSent.size -> {
                // Array shrunk (unexpected in RUM): treat as full replacement
                // This should not happen in normal RUM operation
                current
            }
            else -> {
                // Same size: check if elements are identical
                if (current == lastSent) {
                    emptyList<Any>()
                } else {
                    // Elements changed but size same: treat as full replacement
                    current
                }
            }
        }
    }

    /**
     * Helper to check if two values are deeply equal.
     * Handles nested structures correctly by recursing into Maps and Lists.
     *
     * This is more reliable than using == for nested structures as it
     * ensures proper comparison of Map and List contents.
     *
     * @param a First value to compare
     * @param b Second value to compare
     * @return true if values are deeply equal, false otherwise
     */
    private fun deepEquals(a: Any?, b: Any?): Boolean {
        return when {
            a === b -> true // Reference equality or both null
            a == null || b == null -> a == b // One is null, other is not
            a is Map<*, *> && b is Map<*, *> -> mapsDeepEquals(a, b)
            a is List<*> && b is List<*> -> listsDeepEquals(a, b)
            else -> a == b // Primitives and other types
        }
    }

    /**
     * Deep equality check for maps.
     * Recursively compares all key-value pairs.
     *
     * @param a First map
     * @param b Second map
     * @return true if maps are deeply equal
     */
    private fun mapsDeepEquals(a: Map<*, *>, b: Map<*, *>): Boolean {
        if (a.size != b.size) return false
        return a.all { (key, value) -> deepEquals(value, b[key]) }
    }

    /**
     * Deep equality check for lists.
     * Recursively compares all elements by index.
     *
     * @param a First list
     * @param b Second list
     * @return true if lists are deeply equal
     */
    private fun listsDeepEquals(a: List<*>, b: List<*>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { deepEquals(a[it], b[it]) }
    }
}
