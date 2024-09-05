package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface ExperimentalInterface {
    @ExperimentalApi
    fun doSomethingExperimental()
}
