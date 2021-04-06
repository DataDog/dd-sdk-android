package com.example

import kotlin.Any
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.emptyList

@Suppress("RedundantUnitReturnType")
internal class NoOpAnyGenericInterface<T : Any> : AnyGenericInterface<T> {

    public override fun doSomethingWithListReturn(models: List<T>): List<T> = emptyList()

    public override fun map(event: T): T = event

    public override fun map(event: T, additionalParameters: Map<String, Any?>): T = event
}
