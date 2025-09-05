package com.datadog.android.trace.internal.compat.function

internal interface Consumer<T> {

    fun accept(t: T)
}
