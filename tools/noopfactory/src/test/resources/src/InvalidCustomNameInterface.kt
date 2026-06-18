package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation(customName = "My.Invalid.Name")
interface InvalidCustomNameInterface {
    fun doSomething()
}
