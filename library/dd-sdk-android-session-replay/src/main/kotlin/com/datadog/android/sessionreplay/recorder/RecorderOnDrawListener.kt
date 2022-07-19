/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.app.Activity
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.sessionreplay.processor.Processor
import java.lang.ref.WeakReference

internal class RecorderOnDrawListener(
    activity: Activity,
    private val pixelsDensity: Float,
    private val processor: Processor,
    private val snapshotProducer: SnapshotProducer = SnapshotProducer(),
    private val handler: Handler = Handler(Looper.getMainLooper())
) : ViewTreeObserver.OnDrawListener {
    private var currentOrientation = Configuration.ORIENTATION_UNDEFINED
    private val trackedActivity: WeakReference<Activity> = WeakReference(activity)

    override fun onDraw() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(takeSnapshotRunnable, DEBOUNCE_DURATION_IN_MILLIS)
    }

    private val takeSnapshotRunnable: Runnable = Runnable {
        trackedActivity.get()?.let { activity ->
            activity.window?.let {
                checkForViewPortResize(activity, it.decorView)
                snapshotProducer.produce(it.decorView, pixelsDensity)?.let { node ->
                    processor.process(node)
                }
            }
        }
    }

    private fun checkForViewPortResize(activity: Activity, decorView: View) {
        val orientation = activity.resources.configuration.orientation
        if (currentOrientation != orientation) {
            processor.process(
                OrientationChanged(
                    decorView.width.densityNormalized(pixelsDensity),
                    decorView.height.densityNormalized(pixelsDensity)
                )
            )
        }
        currentOrientation = orientation
    }

    companion object {
        const val DEBOUNCE_DURATION_IN_MILLIS: Long = 16
    }
}
