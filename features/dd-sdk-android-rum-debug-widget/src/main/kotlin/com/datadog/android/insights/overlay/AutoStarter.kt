/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.overlay

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.datadog.android.insights.DefaultInsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollectorProvider
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("OPT_IN_USAGE")
internal object AutoStarter : DefaultLifecycleObserver {
    private var app: Application? = null
    private var overlayManager: OverlayManager? = null
    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    private const val RETRY_DELAY_MS = 500L
    private const val MAX_RETRIES = 20
    private var attempts = 0

    fun install(application: Application) {
        if (app != null) return
        app = application
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        maybeStartOrSchedule()
    }

    override fun onStart(owner: LifecycleOwner) {
        maybeStartOrSchedule()
    }

    private fun maybeStartOrSchedule() {
        if (started.get()) return

        val collector = InsightsCollectorProvider.insightsCollector as? DefaultInsightsCollector
        val application = app ?: return

        if (collector != null) {
            if (started.compareAndSet(false, true)) {
                overlayManager = OverlayManager(application).also { it.start() }
            }
        } else if (attempts++ < MAX_RETRIES) {
            handler.postDelayed({ maybeStartOrSchedule() }, RETRY_DELAY_MS)
        }
    }
}
