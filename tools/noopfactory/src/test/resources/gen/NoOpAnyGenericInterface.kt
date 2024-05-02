@file:Suppress("ktlint")

package com.example

import kotlin.Any
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.emptyList

internal class NoOpAnyGenericInterface<T : Any> : AnyGenericInterface<T> {
    override val immutableProperty: T? = null

    override var mutableProperty: T? = null

    override fun doSomethingWithListReturn(models: List<T>): List<T> = emptyList()

    override fun map(event: T): T = event

    override fun map(event: T, additionalParameters: Map<String, Any?>): T = event
}
