package com.datadog.android.trace.internal.compat.function

internal interface IntFunction<R> {

    fun apply(value: Int): R
}
