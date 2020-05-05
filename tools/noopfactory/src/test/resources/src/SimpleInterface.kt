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
}
