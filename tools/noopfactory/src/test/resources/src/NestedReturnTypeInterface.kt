package com.example

import com.datadog.tools.annotation.NoOpImplementation

interface Outer {
    interface Inner {
        fun getValue(): Int
    }
}

internal class NoOpOuterInner : Outer.Inner {
    override fun getValue(): Int = 0
}

@NoOpImplementation
interface NestedReturnTypeInterface {
    fun getInner(): Outer.Inner
}
