/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.sessionreplay.processor.Processor
import com.datadog.android.sessionreplay.recorder.Debouncer
import com.datadog.android.sessionreplay.recorder.OrientationChanged
import com.datadog.android.sessionreplay.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.recorder.densityNormalized
import java.lang.ref.WeakReference

internal class WindowsOnDrawListener(
    ownerActivity: Activity,
    zOrderedWindows: List<Window>,
    private val pixelsDensity: Float,
    private val processor: Processor,
    private val snapshotProducer: SnapshotProducer,
    private val debouncer: Debouncer = Debouncer()
) : ViewTreeObserver.OnDrawListener {

    private var currentOrientation = Configuration.ORIENTATION_UNDEFINED
    internal val ownerActivityReference: WeakReference<Activity> = WeakReference(ownerActivity)
    internal val weakReferencedWindows: List<WeakReference<Window>>

    init {
        weakReferencedWindows = zOrderedWindows.map { WeakReference(it) }
    }

    override fun onDraw() {
        debouncer.debounce(resolveTakeSnapshotRunnable())
    }

    private fun resolveTakeSnapshotRunnable(): Runnable = Runnable {
        if (weakReferencedWindows.isEmpty()) {
            return@Runnable
        }
        val ownerActivity = ownerActivityReference.get() ?: return@Runnable
        val ownerActivityWindow = ownerActivity.window ?: return@Runnable

        // we will always consider the ownerActivityWindow as the root
        val orientationChanged = resolveOrientationChange(
            ownerActivity,
            ownerActivityWindow.decorView
        )
        // is is very important to have the windows sorted by their z-order
        val nodes = weakReferencedWindows
            .mapNotNull { it.get() }
            .mapNotNull {
                val windowDecorView = it.decorView
                snapshotProducer.produce(ownerActivity.theme, windowDecorView, pixelsDensity)
            }
        if (nodes.isNotEmpty()) {
            processor.processScreenSnapshots(nodes, orientationChanged)
        }
    }

    private fun resolveOrientationChange(activity: Activity, decorView: View):
        OrientationChanged? {
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
