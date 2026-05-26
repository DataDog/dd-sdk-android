package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface Bar {
    fun doSomething()
}

@NoOpImplementation(customName = "NoOpBar")
interface Foo {
    fun doSomething()
}
