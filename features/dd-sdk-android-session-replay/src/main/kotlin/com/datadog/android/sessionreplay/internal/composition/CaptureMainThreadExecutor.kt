/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

/**
 * Deliberately not [java.util.concurrent.Executor]: that interface's `execute` returns nothing, so
 * it can't hand back a way to cancel a posted-but-not-yet-run task - which this needs, to pull a
 * stale continuation out of the queue when a generation expires before its turn.
 * [java.util.concurrent.ExecutorService]/`Future` would give that back, but `Future.get()` blocks
 * the calling thread until the task completes - a real deadlock risk here, since every task this
 * posts can only run on the main thread, the same thread that must never call `get()` on it.
 * [CancellableCaptureWork] only exposes `cancel()`, so that footgun can't exist by construction.
 */
internal fun interface CaptureMainThreadExecutor {
    fun execute(task: () -> Unit): CancellableCaptureWork
}
