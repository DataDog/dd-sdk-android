package com.datadog.android.trace.internal.compat.function

internal interface Supplier<T> {

    fun get(): T
}
