package com.datadog.android.trace.internal.compat.function

internal interface BiFunction<T, U, R> {

    fun apply(t: T, u: U): R
}
