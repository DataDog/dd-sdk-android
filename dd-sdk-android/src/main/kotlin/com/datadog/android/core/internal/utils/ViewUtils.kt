package com.datadog.android.core.internal.utils

import android.content.ComponentName

internal const val UNKNOWN_DESTINATION_URL = "Unknown"

internal fun Any.resolveViewUrl(): String {
    return when (this) {
        is String -> this
        else -> javaClass.canonicalName ?: javaClass.simpleName
    }
}

internal fun ComponentName.resolveViewUrl(): String {
    return when {
        packageName.isEmpty() -> className
        className.startsWith("$packageName.") -> className
        className.contains('.') -> className
        else -> "$packageName.$className"
    }
}
