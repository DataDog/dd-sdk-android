@file:Suppress("ktlint")

package com.example

import java.util.Date
import kotlin.ByteArray
import kotlin.Function0
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

internal class NoOpSimpleInterface : SimpleInterface {
    override val dateProperty: Date = Date()

    override var mutableStringProperty: String = ""

    override val nullableDateProperty: Date? = null

    override var listProperty: List<String> = emptyList()

    override val mapProperty: Map<String, String> = emptyMap()

    override var setProperty: Set<String> = emptySet()

    override fun doSomething() {
    }

    override fun doSomethingWithParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray,
    ) {
    }

    override fun doSomethingWithNullableParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray,
    ) {
    }

    override fun doSomethingWithReturn(): Date = Date()

    override fun doSomethingWithStringReturn(): String = ""

    override fun doSomethingWithNullableReturn(): Date? = null

    override fun doSomethingWithListReturn(): List<String> = emptyList()

    override fun doSomethingWithMapReturn(): Map<String, String> = emptyMap()

    override fun doSomethingWithSetReturn(): Set<String> = emptySet()

    override fun functionalReturnType(): Function0<Unit> = {}

    override fun functionalAliasReturnType(): FunctionalReturn = {}
}
