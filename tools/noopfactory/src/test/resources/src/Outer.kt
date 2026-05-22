package com.example

import com.datadog.tools.annotation.NoOpImplementation

interface Outer {

    @NoOpImplementation
    interface Inner {
        fun doSomething(): Int

        val name: String
    }
}
