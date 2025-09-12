/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.concurrent

/**
 * java.util.concurrent.CompletableFuture is available only starting from API 24, so here
 * is a simple class mimicking very basics of it.
 */
@Suppress("UndocumentedPublicFunction", "UndocumentedPublicProperty")
class CompletableFuture<T : Any> {
    @Volatile
    lateinit var value: T
        private set

    fun isComplete(): Boolean = this::value.isInitialized

    fun complete(value: T) {
        if (isComplete()) return
        this.value = value
    }
}
