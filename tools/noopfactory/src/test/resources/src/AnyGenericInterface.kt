package com.example

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface AnyGenericInterface<T : Any> {

    fun doSomethingWithListReturn(models: List<T>): List<T>

    fun map(event: T): T

    fun map(event: T, additionalParameters: Map<String, Any?>): T
}
