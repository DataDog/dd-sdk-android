package com.example

import kotlin.CharSequence

internal class NoOpGenericInterface<T : CharSequence> : GenericInterface<T> {

    override fun doSomething() {
    }

    override fun doSomethingWithParams(t: T) {
    }

    override fun doSomethingWithNullableParams(t: T?) {
    }

    override fun doSomethingWithNullableReturn(): T? = null
}
