package com.example

import java.util.Date
import kotlin.ByteArray
import kotlin.Int
import kotlin.String

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
}
