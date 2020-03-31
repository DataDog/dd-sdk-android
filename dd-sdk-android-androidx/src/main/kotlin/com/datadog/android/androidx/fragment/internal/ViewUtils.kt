package com.datadog.android.androidx.fragment.internal

internal fun Any.resolveViewName(): String {
    return javaClass.canonicalName ?: javaClass.simpleName
}
