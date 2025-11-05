/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal.overlay

import android.annotation.SuppressLint
import android.app.Activity
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.datadog.android.insights.internal.DefaultInsightsCollector
import com.datadog.android.insights.internal.InsightStateStorage
import com.datadog.android.insights.internal.extensions.animateVisibility
import com.datadog.android.insights.internal.extensions.appendColored
import com.datadog.android.insights.internal.extensions.color
import com.datadog.android.insights.internal.extensions.setupChartView
import com.datadog.android.insights.internal.widgets.ChartView
import com.datadog.android.insights.internal.widgets.DragTouchListener
import com.datadog.android.insights.internal.widgets.TimelineView
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import com.datadog.android.rumdebugwidget.R

/**
 * A local overlay displaying performance metrics collected by the [com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector]
 * implementation registered in the SDK.
 * This overlay is only displayed when the app is running in debug mode and when an instance of
 * [com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector] is registered.
 * It can be attached to any [Activity] by calling [attach].
 */
@ExperimentalRumApi
internal class DefaultInsightsOverlay(
    private val insightsCollector: DefaultInsightsCollector
) : InsightsUpdatesListener {

    private var root: View? = null
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
    private var isTransitioning: Boolean = false

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
    @Suppress("LongMethod")
    fun attach(activity: Activity) {
        val parent = activity.findViewById<ViewGroup>(android.R.id.content)

        if (root == null) {
            val overlayView = activity.layoutInflater.inflate(
                R.layout.layout_dd_insights_overlay,
                parent,
                false
            )

            root = overlayView

            viewName = overlayView.findViewById(R.id.view_name)
            timelineView = overlayView.findViewById<TimelineView?>(R.id.timeline)?.apply {
                setOnClickListener {
                    isPaused = !isPaused
                    setPaused(isPaused)
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

            fab = overlayView.findViewById(R.id.fab)
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

            insightsCollector.addUpdateListener(this)
        }

        val storage = InsightStateStorage(activity)

        root?.let { overlayView ->
            val widgetView = overlayView.findViewById<View>(R.id.insights_widget).apply {
                setOnClickListener {
                    toggleOverlay(storage = storage, showWidget = false, widget = this, fab = fab)
                }
                setOnTouchListener(DragTouchListener(onUp = { storage.widgetPosition = x to y }))
                restoreVisibility(storage.widgetDisplayed)
            }

            fab?.apply {
                setOnClickListener {
                    toggleOverlay(storage = storage, showWidget = true, widget = widgetView, fab = this)
                }
                setOnTouchListener(DragTouchListener(onUp = { storage.fabPosition = x to y }))
                restoreVisibility(!storage.widgetDisplayed)
            }
        }

        root?.let { v ->
            if (v.parent != parent) {
                (v.parent as? ViewGroup)?.removeView(v)
                parent.addView(
                    v,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            v.bringToFront()
        }
    }

    private fun toggleOverlay(
        storage: InsightStateStorage,
        showWidget: Boolean,
        widget: View?,
        fab: View?
    ) {
        if (isTransitioning) return
        isTransitioning = true

        widget?.isClickable = false
        fab?.isClickable = false

        storage.widgetDisplayed = showWidget

        val done = {
            isTransitioning = false
            widget?.isClickable = true
            fab?.isClickable = true
        }

        if (showWidget) {
            fab?.animateVisibility(false) {
                widget?.animateVisibility(true) {
                    done()
                }
            }
        } else {
            widget?.animateVisibility(false) {
                fab?.animateVisibility(true) {
                    done()
                }
            }
        }
    }

    fun destroy() {
        insightsCollector.removeUpdateListener(this)
        root?.let { v ->
            (v.parent as? ViewGroup)?.removeView(v)
            timelineView?.setOnClickListener(null)
            fab?.setOnClickListener(null)
            fab?.setOnTouchListener(null)
            v.setOnClickListener(null)
            v.setOnTouchListener(null)
        }

        root = null
        viewName = null
        timelineView = null
        fab = null
        cpuValue = null
        vmMemoryValue = null
        nativeMemoryValue = null
        threadsValue = null
        gcValue = null
        slowFrameRate = null
        timelineLegend = null
    }

    private fun View.restoreVisibility(shouldBeVisible: Boolean) {
        isVisible = shouldBeVisible
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        isClickable = shouldBeVisible
    }

    override fun onDataUpdated() {
        if (isPaused) return
        timelineView?.let { timeline ->
            timeline.update(insightsCollector.eventsState, insightsCollector.maxSize)
            viewName?.text = insightsCollector.viewName
            cpuValue?.update(insightsCollector.cpuTicksPerSecond)
            gcValue?.update(insightsCollector.gcCallsPerSecond)
            vmMemoryValue?.update(insightsCollector.vmRssMb)
            nativeMemoryValue?.update(insightsCollector.nativeHeapMb)
            threadsValue?.update(insightsCollector.threadsCount.toDouble())
            slowFrameRate?.update(insightsCollector.slowFramesRate)
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
