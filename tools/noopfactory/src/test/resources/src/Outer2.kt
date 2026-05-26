package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface Outer2 {

    @NoOpImplementation
    interface Inner2 {
        fun doSomething(): Int

        val name: String
    }

    fun getInner(): Inner2
}
