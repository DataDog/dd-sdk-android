package com.example

import kotlin.CharSequence
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.Set
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.emptySet

@Suppress("RedundantUnitReturnType")
internal class NoOpGenericInterface<T : CharSequence> : GenericInterface<T> {

    public override fun doSomething(): Unit {
    }

    public override fun doSomethingWithParams(t: T): Unit {
    }

    public override fun doSomethingWithNullableParams(t: T?): Unit {
    }

    public override fun doSomethingWithNullableReturn(): T? = null

    public override fun doSomethingWithListReturn(): List<T> = emptyList()

    public override fun doSomethingWithMapReturn(): Map<T, T> = emptyMap()

    public override fun doSomethingWithSetReturn(): Set<T> = emptySet()
}
