/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

/**
 * A [BackstackKeyResolver] that uses the [hashCode][Any.hashCode] of the item as its stable key.
 *
 * @param T the type of item in the backstack.
 */
class HashcodeBackstackKeyResolver<T> : BackstackKeyResolver<T> {

    override fun getStableKey(
        item: T
    ): String {
        return item.hashCode().toString()
    }
}
