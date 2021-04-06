package com.example

import java.util.Date
import kotlin.ByteArray
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.Set
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.emptySet

@Suppress("RedundantUnitReturnType")
internal class NoOpSimpleInterface : SimpleInterface {

    public override fun doSomething(): Unit {
    }

    public override fun doSomethingWithParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray
    ): Unit {
    }

    public override fun doSomethingWithNullableParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray
    ): Unit {
    }

    public override fun doSomethingWithReturn(): Date = Date()

    public override fun doSomethingWithStringReturn(): String = ""

    public override fun doSomethingWithNullableReturn(): Date? = null

    public override fun doSomethingWithListReturn(): List<String> = emptyList()

    public override fun doSomethingWithMapReturn(): Map<String, String> = emptyMap()

    public override fun doSomethingWithSetReturn(): Set<String> = emptySet()
}
