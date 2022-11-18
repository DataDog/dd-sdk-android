/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.sessionreplay.processor.Processor
import java.lang.ref.WeakReference

internal class RecorderOnDrawListener(
    activity: Activity,
    private val pixelsDensity: Float,
    private val processor: Processor,
    private val snapshotProducer: SnapshotProducer,
    private val debouncer: Debouncer = Debouncer()
) : ViewTreeObserver.OnDrawListener {
    private var currentOrientation = Configuration.ORIENTATION_UNDEFINED
    private val trackedActivity: WeakReference<Activity> = WeakReference(activity)

    override fun onDraw() {
        debouncer.debounce(takeSnapshotRunnable)
    }

    private val takeSnapshotRunnable: Runnable = Runnable {
        trackedActivity.get()?.let { activity ->
            activity.window?.let {
                snapshotProducer.produce(it.decorView, pixelsDensity)?.let { node ->
                    processor.process(node, resolveOrientationChange(activity, it.decorView))
                }
            }
        }
    }

    private fun resolveOrientationChange(activity: Activity, decorView: View): OrientationChanged? {
        val orientation = activity.resources.configuration.orientation
        val orientationChanged =
            if (currentOrientation != orientation) {
                OrientationChanged(
                    decorView.width.densityNormalized(pixelsDensity),
                    decorView.height.densityNormalized(pixelsDensity)
                )
            } else {
                null
            }
        currentOrientation = orientation
        return orientationChanged
    }
}
