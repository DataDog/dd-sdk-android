/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import kotlin.collections.iterator

/**
 * DSL interface for computing field-level diffs between two instances of the same type [D].
 *
 * Each method compares the corresponding field in `old` vs `new` and marks the builder as
 * changed if a difference is detected. The result of each method is the new value (or a
 * derived diff object), or `null` when the field is unchanged.
 *
 * Use [computeDiffIfChanged] to build a diff object and return it only when at least one
 * field changed, or [computeDiffRequired] when the diff object must always be produced
 * (e.g. for required sub-objects that always appear in the output).
 */
interface DiffBuilder<D : Any> {

    /**
     * Returns `true` if any diff method detected a change since this builder was created.
     */
    fun anythingChanged(): Boolean

    /**
     * Compares the field returned by [getter] using equality.
     *
     * Returns the new value if it changed, `null` if it is unchanged.
     * Marks the builder as changed when the values differ.
     */
    fun <T> diffEquals(getter: D.() -> T): T?

    /**
     * Computes a partial diff of the map returned by [getter].
     *
     * Returns a map containing only the entries whose value changed or was added in `new`
     * compared to `old`. Deleted keys are not included. This assumes map entries are only
     * ever added or updated (e.g. feature flags).
     *
     * Marks the builder as changed when at least one entry differs. Always returns a map
     * (empty if nothing changed), never `null`.
     */
    fun <K : Any, V> diffMap(getter: D.() -> Map<K, V>): Map<K, V>

    /**
     * Computes the new elements appended to the list returned by [getter] since `old`.
     *
     * Returns only the elements beyond the original size (`new.drop(old.size)`). This
     * assumes the list is append-only (e.g. slow frames, foreground periods, event counters).
     *
     * Returns `null` if no new elements were appended. Marks the builder as changed when
     * new elements exist.
     */
    fun <T> diffList(getter: D.() -> List<T>?): List<T>?

    /**
     * Delegates diffing of a nested sub-object to [diffFunc].
     *
     * Extracts the sub-object via [getter], then calls [diffFunc] with `(old, new)`.
     * If [diffFunc] returns a non-null result, marks the builder as changed and propagates
     * the nested diff upward. Returns `null` if the nested diff detected no changes.
     *
     * Use this for structured sub-objects that have their own diff logic (e.g. [computeDiffIfChanged]).
     */
    fun <T, R : Any> diffMerge(getter: D.() -> T, diffFunc: (old: T, new: T) -> R?): R?

    /**
     * Always includes the field returned by [getter] in the diff output, but only marks
     * the builder as changed if the value actually differs between old and new.
     *
     * Use this for fields that must always be present in the diff payload regardless of
     * whether their value changed — typically identifiers (e.g. `view.id`, `session.id`)
     * needed by the receiver to correlate the update with the correct entity.
     */
    fun <T> diffRequired(getter: D.() -> T): T
}

/**
 * Computes a diff between [old] and [new] using the [block] DSL.
 *
 * Runs [block] to produce a diff object, then returns it only if at least one field
 * changed (i.e. [DiffBuilder.anythingChanged] is `true`). Returns `null` if nothing changed,
 * meaning no update event needs to be emitted.
 */
fun <D : Any, R : Any> computeDiffIfChanged(old: D, new: D, block: DiffBuilder<D>.() -> R): R? {
    val dsl = DiffBuilderImpl(oldObj = old, newObj = new)
    val result: R = dsl.block()

    return if (dsl.anythingChanged()) {
        result
    } else {
        null
    }
}

/**
 * Computes a diff between [old] and [new] using the [block] DSL and always returns the result.
 *
 * Unlike [computeDiffIfChanged], this always returns the diff object even if nothing changed.
 * Use this for objects that must always be present in the output (e.g. `application`,
 * `session`, `view` in a [ViewUpdateEvent]).
 */
fun <D : Any, R : Any> computeDiffRequired(old: D, new: D, block: DiffBuilder<D>.() -> R): R {
    val dsl = DiffBuilderImpl(oldObj = old, newObj = new)
    return dsl.block()
}

private class DiffBuilderImpl<D : Any>(private val oldObj: D, private val newObj: D) : DiffBuilder<D> {
    private var changed: Boolean = false

    override fun anythingChanged(): Boolean {
        return changed
    }

    override fun <T> diffEquals(getter: D.() -> T): T? {
        val old = getter(oldObj)
        val new = getter(newObj)

        if (old != new) {
            changed = true
            return new
        } else {
            return null
        }
    }

    override fun <K : Any, V> diffMap(getter: D.() -> Map<K, V>): Map<K, V> {
        val old = getter(oldObj)
        val new = getter(newObj)

        val diff = buildMap {
            for ((key, newValue) in new) {
                val oldValue = old[key]

                if (newValue != oldValue) {
                    put(key, newValue)
                }
            }
        }

        if (diff.isNotEmpty()) {
            changed = true
        }

        return diff
    }

    override fun <T> diffList(getter: D.() -> List<T>?): List<T>? {
        val old = getter(oldObj) ?: emptyList()
        val new = getter(newObj) ?: emptyList()

        val diff = new.drop(old.size)

        if (diff.isNotEmpty()) {
            changed = true
            return diff
        }

        return null
    }

    override fun <T, R : Any> diffMerge(getter: D.() -> T, diffFunc: (old: T, new: T) -> R?): R? {
        val old = getter(oldObj)
        val new = getter(newObj)

        val diff = diffFunc(old, new)

        if (diff != null) {
            changed = true
        }

        return diff
    }

    override fun <T> diffRequired(getter: D.() -> T): T {
        val old = getter(oldObj)
        val new = getter(newObj)
        if (old != new) {
            changed = true
        }
        return new
    }
}
