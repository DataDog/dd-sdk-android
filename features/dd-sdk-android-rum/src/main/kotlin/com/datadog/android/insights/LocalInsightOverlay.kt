/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights

import android.annotation.SuppressLint
import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import com.datadog.android.Datadog
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.insights.extensions.animateRotateBy
import com.datadog.android.insights.extensions.animateVisibility
import com.datadog.android.insights.extensions.multiLet
import com.datadog.android.insights.widgets.DragTouchListener
import com.datadog.android.insights.widgets.TimelineView
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.R
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener

@ExperimentalRumApi
class LocalInsightOverlay : InsightsUpdatesListener {

    private var viewName: TextView? = null
    private var timelineView: TimelineView? = null

    private var fab: View? = null
    private var icon: View? = null

    private var cpuValue: TextView? = null
    private var vmMemoryValue: TextView? = null
    private var nativeMemoryValue: TextView? = null
    private var threadsValue: TextView? = null
    private var gcValue: TextView? = null

    private val insightsCollector: DefaultInsightsCollector?
        get() = (Datadog.getInstance() as? InternalSdkCore)
            ?.getFeature(Feature.RUM_FEATURE_NAME)
            ?.unwrap<RumFeature>()
            ?.insightsCollector as? DefaultInsightsCollector

    private var isPaused: Boolean = false

    @SuppressLint("SetTextI18n")
    fun attach(activity: Activity) {
        val overlayView = LayoutInflater.from(activity).inflate(
            R.layout.layout_dd_insights_overlay,
            activity.window.decorView as ViewGroup,
            true
        )
        val widgetView = overlayView.findViewById<View>(R.id.insights_widget)

        overlayView.findViewById<View>(R.id.insights_widget).setOnTouchListener(DragTouchListener())

        viewName = overlayView.findViewById(R.id.view_name)
        timelineView = overlayView.findViewById<TimelineView?>(R.id.timeline).also {
            it.setOnClickListener {
                isPaused = !isPaused
                timelineView?.setPaused(isPaused)
            }
        }
        icon = overlayView.findViewById<View>(R.id.icon).also {
            it.setOnClickListener {
                fab?.animateVisibility(true)
                widgetView?.animateVisibility(false)
            }
        }
        fab = overlayView.findViewById<View>(R.id.fab)
            .also {
                it.setOnTouchListener(
                    DragTouchListener(
                        onUp = { animateRotateBy(360 - (rotation % 360)) }
                    )
                )
                it.setOnClickListener {
                    fab?.animateVisibility(false)
                    widgetView?.animateVisibility(true)
                }
            }
        cpuValue = overlayView.findKeyValue(R.id.vital_cpu, "CPU (tics/s)")
        vmMemoryValue = overlayView.findKeyValue(R.id.vital_mem, "MEM (mb)")
        nativeMemoryValue = overlayView.findKeyValue(R.id.vital_native, "Native (mb)")
        threadsValue = overlayView.findKeyValue(R.id.vital_threads, "Threads")
        gcValue = overlayView.findKeyValue(R.id.vital_gc, "GC (calls/s)")

        insightsCollector?.addUpdateListener(this)
    }

    fun detach(activity: Activity) {
        insightsCollector?.removeUpdateListener(this)
    }

    @SuppressLint("SetTextI18n")
    override fun onDataUpdated() {
        if (isPaused) return
        multiLet(insightsCollector, timelineView) { collector, timeline ->
            timeline.update(collector.eventsState, collector.maxSize)
            viewName?.text = collector.viewName
            cpuValue?.text = collector.cpuTicksPerSecond.toString()
            gcValue?.text = collector.gcCallsPerSecond.toString()
            vmMemoryValue?.text = collector.vmRssMb.toString()
            nativeMemoryValue?.text = collector.nativeHeapMb.toString()
            threadsValue?.text = collector.threadsCount.toString()
        }
    }

    private fun View.findKeyValue(
        @IdRes id: Int,
        labelText: String
    ): TextView {
        findViewById<ViewGroup>(id).also { keyValue ->
            keyValue.findViewById<TextView>(R.id.label).text = labelText
            return keyValue.findViewById(R.id.value)
        }
    }
}
