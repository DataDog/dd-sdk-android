package com.example

import java.util.Date

interface NotAnnotatedInterface {
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
}
