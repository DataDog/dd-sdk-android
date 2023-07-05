package com.example

import com.datadog.tools.annotation.NoOpImplementation

interface RootInterface {
    fun rootMethod()

    val immutableProperty: String
}

interface ParentInterface : RootInterface {
    fun parentMethod()
    var mutableProperty: String
}

@NoOpImplementation
interface InheritedInterface : ParentInterface {
    fun doSomething()
}
