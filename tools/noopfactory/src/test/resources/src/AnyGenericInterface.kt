package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface AnyGenericInterface<T : Any> {

    fun doSomethingWithListReturn(models: List<T>): List<T>
}
