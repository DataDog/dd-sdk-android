/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

internal fun interface CaptureMainThreadExecutor {
    fun execute(task: () -> Unit): CancellableCaptureWork
}

internal fun interface CaptureTaskScheduler {
    fun schedule(delayNs: Long, task: () -> Unit): CancellableCaptureWork

    fun shutdown() = Unit
}
