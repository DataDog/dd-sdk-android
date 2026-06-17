package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation(customName = "SharedName")
interface Alpha {
    fun doSomething()
}

@NoOpImplementation(customName = "SharedName")
interface Beta {
    fun doSomething()
}
