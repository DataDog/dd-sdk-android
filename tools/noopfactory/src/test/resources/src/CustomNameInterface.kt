package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation(customName = "MyCustomNoOp")
interface CustomNameInterface {
    fun doSomething()
}
