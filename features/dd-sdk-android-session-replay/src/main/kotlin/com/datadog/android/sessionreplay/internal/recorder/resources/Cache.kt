/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

internal interface Cache<K : Any, V : Any> {

    fun put(value: V) {}
    fun put(key: String, value: V) {}
    fun get(key: String): V? = null
    fun size(): Int
    fun clear()

    companion object {
        internal const val DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS =
            "Cache instance does not implement ComponentCallbacks2"
    }
}
