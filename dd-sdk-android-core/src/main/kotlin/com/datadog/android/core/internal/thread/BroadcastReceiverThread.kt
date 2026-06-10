/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import android.os.Handler
import android.os.HandlerThread

/**
 * A wrapper around a dedicated [HandlerThread] used as the dispatch thread for
 * the SDK's [android.content.BroadcastReceiver]s. Passing [handler] to
 * [android.content.Context.registerReceiver] ensures each `onReceive` callback
 * is delivered on this background thread instead of the main thread, avoiding
 * ANRs caused by per-call overhead on protected builds (e.g. PairIP).
 *
 * Lifecycle: the underlying thread is started eagerly at construction time.
 * Call [shutdown] when the SDK is torn down to release the thread.
 */
internal class BroadcastReceiverThread {

    private val handlerThread: HandlerThread =
        @Suppress("UnsafeThirdPartyFunctionCall") // constructed once; start() is called exactly once here
        HandlerThread("datadog-broadcast-receiver-thread").apply { start() }

    val handler: Handler = Handler(handlerThread.looper)

    fun shutdown() {
        handlerThread.quitSafely()
    }
}
