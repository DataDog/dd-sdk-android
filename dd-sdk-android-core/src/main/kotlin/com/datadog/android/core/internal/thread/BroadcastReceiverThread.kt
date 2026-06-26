/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import android.os.HandlerThread

/**
 * A dedicated [HandlerThread] used as the dispatch thread for the SDK's
 * [android.content.BroadcastReceiver]s. Passing a [android.os.Handler] built from this thread's
 * looper to [android.content.Context.registerReceiver] ensures each `onReceive` callback is
 * delivered on this background thread instead of the main thread, avoiding ANRs caused by
 * per-call overhead on protected builds (e.g. PairIP).
 *
 * Lifecycle: start the thread before use and call [quitSafely] when the SDK is torn down.
 */
internal class BroadcastReceiverThread : HandlerThread("datadog-broadcast-receiver-thread")
