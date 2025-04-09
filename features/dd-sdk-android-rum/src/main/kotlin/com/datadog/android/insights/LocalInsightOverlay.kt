/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
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

    private val insightsCollector: DefaultInsightsCollector?
        get() = (Datadog.getInstance() as? InternalSdkCore)
            ?.getFeature(Feature.RUM_FEATURE_NAME)
            ?.unwrap<RumFeature>()
            ?.insightsCollector as? DefaultInsightsCollector

    fun attach(activity: Activity) {
        val overlayView = LayoutInflater.from(activity).inflate(
            R.layout.layout_dd_insights_overlay,
            activity.window.decorView as ViewGroup,
            true
        )
        val widgetView = overlayView.findViewById<View>(R.id.insights_widget)

        overlayView.findViewById<View>(R.id.insights_widget).setOnTouchListener(DragTouchListener())

        timelineView = overlayView.findViewById(R.id.timeline)
        viewName = overlayView.findViewById(R.id.view_name)

        overlayView.findViewById<View>(R.id.fab)
            .apply {
                setOnTouchListener(
                    DragTouchListener(
                        onUp = { animateRotateBy(360 - (rotation % 360)) }
                    )
                )
                setOnClickListener {
                    widgetView?.animateVisibility(!widgetView.isVisible)
                }
            }

        insightsCollector?.addUpdateListener(this)
    }

    fun detach(activity: Activity) {
        insightsCollector?.removeUpdateListener(this)
    }

    override fun onDataUpdated() {
        multiLet(insightsCollector, timelineView) { collector, timeline ->
            timeline.update(collector.eventsState, collector.maxSize)
            viewName?.text = collector.viewName
        }
    }
}
