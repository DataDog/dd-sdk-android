package com.datadog.android.trace.internal.compat.function

internal interface BiConsumer<T, U> {

    fun accept(t: T, u: U)
}
