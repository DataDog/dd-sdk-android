/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.navigation

import android.os.Build
import android.os.Parcelable
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController

internal inline fun <reified T : Parcelable> NavController.navigate(@IdRes id: Int, arg: T) {
    val bundle = bundleOf(getArgKey<T>() to arg)
    navigate(id, bundle)
}

@Suppress("DEPRECATION")
internal inline fun <reified T : Parcelable> Fragment.args(): Lazy<T> {
    return lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(getArgKey<T>(), T::class.java)!!
        } else {
            arguments?.getParcelable(getArgKey<T>())!!
        }
    }
}

private inline fun <reified T> getArgKey(): String {
    return T::class.qualifiedName ?: "arg"
}
