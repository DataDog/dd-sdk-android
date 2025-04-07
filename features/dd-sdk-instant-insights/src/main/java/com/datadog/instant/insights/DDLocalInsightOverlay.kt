/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.datadog.android.instant.insights.R

class DDLocalInsightOverlay {

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
    }
}
