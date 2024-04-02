package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation(publicNoOpImplementation = true)
interface PublicImplementation {
    fun doSomething()
}
