package com.example

import com.datadog.tools.annotation.NoOpImplementation
import java.util.Date

@NoOpImplementation
class NotAnInterface {
    fun doSomething() {
        // No Op
    }

    val dateProperty: Date = Date()

    var mutableStringProperty: String = ""

    val nullableDateProperty: Date? = null

    var listProperty: List<String> = emptyList()

    val mapProperty: Map<String, String> = emptyMap()

    var setProperty: Set<String> = emptySet()
}
