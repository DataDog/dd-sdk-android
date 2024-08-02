@file:Suppress("ktlint")

package com.example

import kotlin.CharSequence
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.Set
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.emptySet

internal class NoOpGenericInterface<T : CharSequence> : GenericInterface<T> {
    override val immutableProperty: T? = null

    override var mutableProperty: T? = null

    override var listProperty: List<T> = emptyList()

    override val mapProperty: Map<T, T> = emptyMap()

    override var setProperty: Set<T> = emptySet()

    override fun doSomething() {
    }

    override fun doSomethingWithParams(t: T) {
    }

    override fun doSomethingWithNullableParams(t: T?) {
    }

    override fun doSomethingWithNullableReturn(): T? = null

    override fun doSomethingWithListReturn(): List<T> = emptyList()

    override fun doSomethingWithMapReturn(): Map<T, T> = emptyMap()

    override fun doSomethingWithSetReturn(): Set<T> = emptySet()
}
