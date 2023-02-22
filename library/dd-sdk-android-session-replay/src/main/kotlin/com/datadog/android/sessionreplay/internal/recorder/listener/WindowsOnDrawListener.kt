/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.app.Activity
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.sessionreplay.internal.processor.Processor
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import java.lang.ref.WeakReference

internal class WindowsOnDrawListener(
    ownerActivity: Activity,
    zOrderedWindows: List<Window>,
    private val processor: Processor,
    private val snapshotProducer: SnapshotProducer,
    private val debouncer: Debouncer = Debouncer(),
    private val miscUtils: MiscUtils = MiscUtils
) : ViewTreeObserver.OnDrawListener {

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

        // is is very important to have the windows sorted by their z-order
        val systemInformation = miscUtils.resolveSystemInformation(ownerActivity)
        val nodes = weakReferencedWindows
            .mapNotNull { it.get() }
            .mapNotNull {
                val windowDecorView = it.decorView
                snapshotProducer.produce(windowDecorView, systemInformation)
            }
        if (nodes.isNotEmpty()) {
            processor.processScreenSnapshots(
                nodes,
                systemInformation
            )
        }
    }
}
