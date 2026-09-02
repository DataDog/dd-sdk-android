/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor

/**
 * Triggers a remote configuration refresh every time the app comes to the foreground.
 * OkHttp's ETag cache ensures the CDN fetch is a cheap 304 Not Modified when the config
 * has not changed since the last sync.
 */
internal class RemoteConfigLifecycleCallback(
    private val remoteConfigService: RemoteConfigService
) : ProcessLifecycleMonitor.Callback {

    // Both onStarted() and onStopped() are invoked on the main thread by ProcessLifecycleMonitor
    // (via Application.ActivityLifecycleCallbacks), so wasBackgrounded is only ever read and
    // written on the main thread. No AtomicBoolean or @Volatile needed.
    private var wasBackgrounded = false

    override fun onStarted() {
        if (wasBackgrounded) {
            // syncWithRemote() is non-blocking — it dispatches to an executor and returns immediately.
            remoteConfigService.syncWithRemote()
        }
    }

    override fun onResumed() {
        // NO-OP
    }

    override fun onStopped() {
        wasBackgrounded = true
    }

    override fun onPaused() {
        // NO-OP
    }
}
