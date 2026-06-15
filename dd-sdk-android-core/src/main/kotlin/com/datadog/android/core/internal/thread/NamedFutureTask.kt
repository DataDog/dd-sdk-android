/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.internal.thread.NamedExecutionUnit
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask

/**
 * Wrapper around [FutureTask] to carry the name of [NamedExecutionUnit] runnable/callable in order to be
 * used in [ObservableLinkedBlockingQueue].
 */
internal class NamedFutureTask<V> : FutureTask<V>, NamedExecutionUnit {

    override val name: String

    constructor(name: String, callable: Callable<V>) : super(callable) {
        this.name = name
    }

    constructor(name: String, runnable: Runnable, value: V) : super(runnable, value) {
        this.name = name
    }
}
