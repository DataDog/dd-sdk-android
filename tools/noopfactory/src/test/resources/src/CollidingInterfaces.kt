package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface OuterInner {
    fun doSomething()
}

class Outer {
    @NoOpImplementation
    interface Inner {
        fun doSomething()
    }
}
