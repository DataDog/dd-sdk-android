package com.datadog.android.core.internal.utils

import android.content.ComponentName
import androidx.navigation.ActivityNavigator
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator

internal const val UNKNOWN_DESTINATION_URL = "Unknown"

internal fun Any.resolveViewUrl(): String {
    return when (this) {
        is FragmentNavigator.Destination -> className
        is DialogFragmentNavigator.Destination -> className
        is ActivityNavigator.Destination -> component?.resolveViewUrl() ?: UNKNOWN_DESTINATION_URL
        else -> javaClass.canonicalName ?: javaClass.simpleName
    }
}

internal fun ComponentName.resolveViewUrl(): String {
    return when {
        className.startsWith(packageName) -> className
        className.contains('.') -> className
        else -> "$packageName.$className"
    }
}
