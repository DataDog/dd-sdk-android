package com.example

import java.util.Date
import kotlin.ByteArray
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.Set
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.emptySet

internal class NoOpSimpleInterface : SimpleInterface {

    override fun doSomething() {
    }

    override fun doSomethingWithParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray
    ) {
    }

    override fun doSomethingWithNullableParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray
    ) {
    }

    override fun doSomethingWithReturn(): Date = Date()

    override fun doSomethingWithStringReturn(): String = ""

    override fun doSomethingWithNullableReturn(): Date? = null

    override fun doSomethingWithListReturn(): List<String> = emptyList()

    override fun doSomethingWithMapReturn(): Map<String, String> = emptyMap()

    override fun doSomethingWithSetReturn(): Set<String> = emptySet()
}
