package com.example

import com.datadog.tools.annotation.NoOpImplementation
import java.util.Date

@NoOpImplementation
interface SimpleInterface {
    fun doSomething()

    fun doSomethingWithParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray
    )

    fun doSomethingWithNullableParams(
        i: Int,
        s: String,
        d: Date,
        ba: ByteArray
    )

    fun doSomethingWithReturn(): Date

    fun doSomethingWithStringReturn(): String

    fun doSomethingWithNullableReturn(): Date?

    fun doSomethingWithListReturn(): List<String>

    fun doSomethingWithMapReturn(): Map<String, String>

    fun doSomethingWithSetReturn(): Set<String>

    val dateProperty: Date

    var mutableStringProperty: String

    val nullableDateProperty: Date?

    var listProperty: List<String>

    val mapProperty: Map<String, String>

    var setProperty: Set<String>
}
