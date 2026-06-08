package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation(customName = "MyPreexistingNoOp")
interface PreExistingFileCustomNameInterface {
    fun doSomething()
}
