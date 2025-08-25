/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.datadog.android.api.SdkCore
import com.datadog.android.internal.utils.NextDrawListener.Companion.onNextDraw
import com.datadog.android.internal.utils.onDecorViewReady
import com.datadog.android.rum.RumMonitor
import io.opentelemetry.api.trace.Tracer
import java.time.Instant

class BetterAppStartupTimeManager(
    private val context: Context,
    private val tracer: Tracer,
    private val sdkCore: SdkCore,
    private val rumMonitor: RumMonitor,
) : Application.ActivityLifecycleCallbacks {

    var firstDraw = false
    val handler = Handler(Looper.getMainLooper())

    var firstPostEnqueued: Boolean? = null
    
    fun launch() {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)

        firstPostEnqueued = true

        Handler().post {
            Log.w("WAHAHA", "[START_TYPE]: first frame")
            firstPostEnqueued = false
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

        when (firstPostEnqueued) {
            true -> if (savedInstanceState == null) {
                Log.w("WAHAHA", "[START_TYPE]: Cold start")
            } else {
                Log.w("WAHAHA", "[START_TYPE]: LukeWarm start")
            }
            false -> Log.w("WAHAHA", "[START_TYPE]: Warm start")
            null -> Log.w("WAHAHA", "[START_TYPE]: WAT start")
        }

        if (firstDraw) return

        val name = activity::class.java.simpleName
        val window = activity.window
        window.onDecorViewReady {
            window.decorView.onNextDraw {
                if (firstDraw) return@onNextDraw
                firstDraw = true
                handler.postAtFrontOfQueue {
                    val start = Process.getStartUptimeMillis()
                    val now = SystemClock.uptimeMillis()
                    val startDurationMs = now - start

                    val epoch = Instant.now()

                    val rootSpan = tracer.spanBuilder("better_app_start")
                        .setStartTimestamp(epoch)
                        .startSpan()

                    attachTraceToRumView(rootSpan, sdkCore)

                    rootSpan.end(epoch.plusMillis(startDurationMs))

//                    rumMonitor.sendDurationVital(
//                        startMs = System.currentTimeMillis() - startDurationMs,
//                        durationMs = startDurationMs,
//                        name = "TTID"
//                    )
                    rumMonitor.addTiming("TTID")

                    Log.d(
                        "WAHAHA",
                        "Displayed $name in $startDurationMs ms"
                    )
                }
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        
    }

    override fun onActivityPaused(activity: Activity) {
        
    }

    override fun onActivityResumed(activity: Activity) {
        
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        
    }

    override fun onActivityStarted(activity: Activity) {
        
    }

    override fun onActivityStopped(activity: Activity) {
        
    }

}

private fun isForegroundProcess(): Boolean {
    val processInfo = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(processInfo)
    return processInfo.importance == IMPORTANCE_FOREGROUND
}

