/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

interface DDCoreSubscription<T> {
    fun addListener(listener: T)
    fun removeListener(listener: T)

    fun notify(block: T.() -> Unit)

    companion object {
        fun <T> create(): DDCoreSubscription<T> {
            return DDCoreSubscriptionImpl()
        }
    }
}

// TODO WAHAHA
private class DDCoreSubscriptionImpl<T>: DDCoreSubscription<T> {
    private val listeners = mutableSetOf<T>()

    override fun addListener(listener: T) {
        listeners.add(listener)
    }

    override fun removeListener(listener: T) {
        listeners.remove(listener)
    }

    override fun notify(block: T.() -> Unit) {
        listeners.forEach { it.block() }
    }
}
