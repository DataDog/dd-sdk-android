package com.example

import kotlin.Any
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.emptyList

@Suppress(
    "RedundantUnitReturnType"
)
internal class NoOpAnyGenericInterface<T : Any> : AnyGenericInterface<T> {

    public override fun doSomethingWithListReturn(models: List<T>): List<T> = emptyList()
}
