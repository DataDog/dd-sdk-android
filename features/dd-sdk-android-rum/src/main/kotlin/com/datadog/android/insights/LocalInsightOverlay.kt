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
import android.widget.Toast
import com.datadog.android.Datadog
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.R
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import com.datadog.instant.insights.DragTouchListener

class LocalInsightOverlay : InsightsUpdatesListener {
    private val insightsCollector: InsightsCollector?
        get() {
            val sdkCore = Datadog.getInstance() as InternalSdkCore
            val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.unwrap<RumFeature>()
            return rumFeature?.insightsCollector
        }

    fun attach(activity: Activity) {
        val container = activity.window.decorView as ViewGroup
        val overlay = LayoutInflater.from(activity).inflate(
            R.layout.layout_dd_instant_insights_overlay,
            container,
            true
        )
        overlay.findViewById<View>(R.id.fab).apply {
            setOnTouchListener(DragTouchListener())
            setOnClickListener {
                Toast.makeText(activity, "Click", Toast.LENGTH_SHORT).show()
            }
        }

        insightsCollector?.addUpdateListener(this)
    }

    fun detach(activity: Activity) {
        insightsCollector?.removeUpdateListener(this)
    }

    override fun onUpdate() {
    }
}
