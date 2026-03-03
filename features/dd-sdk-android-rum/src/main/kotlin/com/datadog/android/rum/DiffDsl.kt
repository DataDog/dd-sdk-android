/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

interface DiffDsl<D> {
    fun anythingChanged(): Boolean

    fun <T> diffEquals(getter: D.() -> T): T?
    fun <K : Any, V> diffMap(getter: D.() -> Map<K,V>): Map<K, V>
    fun <T> diffList(getter: D.() -> List<T>?): List<T>?
    fun <T, R : Any> diffMerge(getter: D.() -> T, diffFunc: (old: T, new: T) -> R?): R?
    fun <T> diffRequired(getter: D.() -> T): T
}

fun <D, R : Any> computeDiff(old: D, new: D, block: DiffDsl<D>.() -> R): R? {
    val dsl = DiffDslImpl(oldObj = old, newObj = new)
    val result: R = dsl.block()

    return if (dsl.anythingChanged()) {
        result
    } else {
        null
    }
}

fun <D, R : Any> computeDiffRequired(old: D, new: D, block: DiffDsl<D>.() -> R): R {
    val dsl = DiffDslImpl(oldObj = old, newObj = new)
    return dsl.block()
}

class DiffDslImpl<D>(private val oldObj: D, private val newObj: D): DiffDsl<D> {
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
        changed = true
        return getter(newObj)
    }
}

// example usage

data class SomeState(
    val x: Int,
    val y: Int,
    val nested: Nested
) {
    data class Nested(
        val id: String,
        val flags: Map<String, Any?>,
        val arr: List<Int>
    )
}

data class SomeStateDiff(
    val x: Int?,
    val y: Int?,
    val nested: NestedDiff?
) {
    data class NestedDiff(
        val id: String, // id is required, thus NestedDiff will always exist and have id.
        val flags: Map<String, Any?>?,
        val arr: List<Int>?
    )
}

fun diffSomeState(old: SomeState, new: SomeState): SomeStateDiff? {
    return computeDiff(old = old, new = new) {
        SomeStateDiff(
            x = diffEquals(SomeState::x),
            y = diffEquals(SomeState::y),
            nested = diffMerge(SomeState::nested, ::diffNested)
        )
    }
}

fun diffNested(old: SomeState.Nested, new: SomeState.Nested): SomeStateDiff.NestedDiff? {
    return computeDiff(old = old, new = new) {
        SomeStateDiff.NestedDiff(
            flags = diffMap(SomeState.Nested::flags),
            arr = diffList(SomeState.Nested::arr),
            id = diffRequired(SomeState.Nested::id)
        )
    }
}
