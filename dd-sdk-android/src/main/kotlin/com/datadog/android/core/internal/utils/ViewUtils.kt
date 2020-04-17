package com.datadog.android.core.internal.utils

internal fun Any.resolveViewName(): String {
    return javaClass.canonicalName ?: javaClass.simpleName
}
