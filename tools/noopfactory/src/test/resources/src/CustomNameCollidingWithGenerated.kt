package com.example

import com.datadog.tools.annotation.NoOpImplementation

// Bar is declared first so it is processed first, putting "NoOpBar" in generatedNames
// before Foo's customName is resolved — this ensures the customName collision path fires.
@NoOpImplementation
interface Bar {
    fun doSomething()
}

@NoOpImplementation(customName = "NoOpBar")
interface Foo {
    fun doSomething()
}
