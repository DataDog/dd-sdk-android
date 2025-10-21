/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights

import android.annotation.SuppressLint
import android.app.Activity
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.datadog.android.insights.internal.InsightStateStorage
import com.datadog.android.insights.internal.extensions.animateVisibility
import com.datadog.android.insights.internal.extensions.appendColored
import com.datadog.android.insights.internal.extensions.color
import com.datadog.android.insights.internal.extensions.multiLet
import com.datadog.android.insights.internal.extensions.setupChartView
import com.datadog.android.insights.internal.widgets.ChartView
import com.datadog.android.insights.internal.widgets.DragTouchListener
import com.datadog.android.insights.internal.widgets.TimelineView
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollectorProvider
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import com.datadog.android.rumdebugwidget.R

/**
 * A local overlay displaying performance metrics collected by the [com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector]
 * implementation registered in the SDK.
 * This overlay is only displayed when the app is running in debug mode and when an instance of
 * [com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector] is registered.
 * It can be attached to any [android.app.Activity] by calling [attach].
 */
@ExperimentalRumApi
class LocalInsightOverlay : InsightsUpdatesListener {

    private var viewName: TextView? = null
    private var timelineView: TimelineView? = null
    private var fab: View? = null
    private var cpuValue: ChartView? = null
    private var vmMemoryValue: ChartView? = null
    private var nativeMemoryValue: ChartView? = null
    private var threadsValue: ChartView? = null
    private var gcValue: ChartView? = null
    private var slowFrameRate: ChartView? = null
    private var timelineLegend: TextView? = null

    private var isPaused: Boolean = false

    private val insightsCollector: DefaultInsightsCollector?
        get() = InsightsCollectorProvider.insightsCollector as? DefaultInsightsCollector

    /**
     * Attaches the overlay to the given [activity].
     * It will add a floating button to show/hide the overlay and will display various performance
     * metrics collected by the [com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector]
     * implementation registered in the SDK.
     * If no [com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector] is registered,
     * this method does nothing.
     *
     * @param activity The activity on which to attach the overlay.
     */
    @SuppressLint("SetTextI18n")
    @Suppress("LongMethod")
    fun attach(activity: Activity) {
        if (insightsCollector == null) return
        val storage = InsightStateStorage(activity)

        val overlayView = activity.layoutInflater.inflate(
            R.layout.layout_dd_insights_overlay,
            activity.window.decorView as ViewGroup,
            true
        )
        val widgetView = overlayView.findViewById<View>(R.id.insights_widget).apply {
            setOnClickListener {
                storage.widgetDisplayed = false
                it.animateVisibility(false)
                fab?.animateVisibility(true)
            }
            setOnTouchListener(DragTouchListener(onUp = { storage.widgetPosition = x to y }))
            restoreVisibility(storage.widgetDisplayed)
        }

        viewName = overlayView.findViewById(R.id.view_name)
        timelineView = overlayView.findViewById<TimelineView?>(R.id.timeline)?.apply {
            setOnClickListener {
                isPaused = !isPaused
                timelineView?.setPaused(isPaused)
            }
        }
        timelineLegend = overlayView.findViewById<TextView?>(R.id.timeline_legend)?.apply {
            text = SpannableStringBuilder()
                .append(SEP)
                .appendColored(ACTION, color(R.color.timeline_action)).append(SEP)
                .appendColored(RESOURCE, color(R.color.timeline_resource)).append(SEP)
                .appendColored(SLOW_FRAME, color(R.color.timeline_slow_frame)).append(SEP)
                .appendColored(FROZEN_FRAME, color(R.color.timeline_freeze_frame)).append(SEP)
        }
        fab = overlayView.findViewById<View>(R.id.fab).apply {
            setOnClickListener {
                storage.widgetDisplayed = true
                it.animateVisibility(false)
                widgetView?.animateVisibility(true)
            }
            setOnTouchListener(DragTouchListener(onUp = { storage.fabPosition = x to y }))
            restoreVisibility(!storage.widgetDisplayed)
        }
        cpuValue = overlayView.setupChartView(R.id.vital_cpu, "CPU (tics/s)")
        vmMemoryValue = overlayView.setupChartView(R.id.vital_mem, "MEM (mb)")
        nativeMemoryValue = overlayView.setupChartView(R.id.vital_native, "Native (mb)")
        threadsValue = overlayView.setupChartView(
            id = R.id.vital_threads,
            labelText = "Threads",
            enableChart = false
        )
        gcValue = overlayView.setupChartView(
            id = R.id.vital_gc,
            labelText = "GC (calls/s)",
            enableChart = false
        )
        slowFrameRate = overlayView.setupChartView(
            id = R.id.vital_slow_frame_rate,
            labelText = "SFR (ms/s)",
            enableChart = false
        )

        insightsCollector?.addUpdateListener(this)
    }

    /**
     * Detaches the overlay from the current [Activity].
     */
    fun detach() {
        insightsCollector?.removeUpdateListener(this)
    }

    private fun View.restoreVisibility(shouldBeVisible: Boolean) {
        isVisible = shouldBeVisible
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
    }

    @SuppressLint("SetTextI18n")
    override fun onDataUpdated() {
        if (isPaused) return
        multiLet(insightsCollector, timelineView) { collector, timeline ->
            timeline.update(collector.eventsState, collector.maxSize)
            viewName?.text = collector.viewName
            cpuValue?.update(collector.cpuTicksPerSecond)
            gcValue?.update(collector.gcCallsPerSecond)
            vmMemoryValue?.update(collector.vmRssMb)
            nativeMemoryValue?.update(collector.nativeHeapMb)
            threadsValue?.update(collector.threadsCount.toDouble())
            slowFrameRate?.update(collector.slowFramesRate)
        }
    }

    companion object {
        private const val SEP = " | "
        private const val ACTION = "Action"
        private const val RESOURCE = "Resource"
        private const val SLOW_FRAME = "Slow"
        private const val FROZEN_FRAME = "Frozen"
    }
}
