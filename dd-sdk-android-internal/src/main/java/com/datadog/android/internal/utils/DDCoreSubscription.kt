/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import java.util.concurrent.CopyOnWriteArraySet

interface DDCoreSubscription<T> {
    fun addListener(listener: T)
    fun removeListener(listener: T)

    fun notifyListeners(block: T.() -> Unit)

    val size: Int

    companion object {
        fun <T> create(): DDCoreSubscription<T> {
            return DDCoreSubscriptionImpl()
        }
    }
}

private class DDCoreSubscriptionImpl<T>: DDCoreSubscription<T> {
    private val listeners = CopyOnWriteArraySet<T>()

    override fun addListener(listener: T) {
        listeners.add(listener)
    }

    override fun removeListener(listener: T) {
        listeners.remove(listener)
    }

    override fun notifyListeners(block: T.() -> Unit) {
        listeners.forEach { it.block() }
    }

    override val size: Int get() = listeners.size
}
