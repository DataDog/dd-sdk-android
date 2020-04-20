package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface GenericInterface<T : CharSequence> {

    fun doSomething()

    fun doSomethingWithParams(t: T)

    fun doSomethingWithNullableParams(t: T?)

    fun doSomethingWithNullableReturn(): T?
}
