package com.datadog.android.support.fragment.internal

internal fun Any.resolveViewName(): String {
    return javaClass.canonicalName ?: javaClass.simpleName
}
