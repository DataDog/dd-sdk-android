/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils

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
