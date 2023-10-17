/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal class ShowTouchRecordsHelper(window: Window) {
    private var viewGroup: ScrollView? = null
    private var debugTextView: TextView? = null
    private val windowReference = WeakReference(window)

    fun displayTouchRecord(timestamp: Long, appContext: Context) {
        val window = windowReference.get()
        if (viewGroup == null && window != null) {
            viewGroup = ScrollView(appContext)
            findContentView(window)?.addView(
                viewGroup,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    150
                ).apply {
                    gravity = Gravity.BOTTOM
                }
            )
        }

        if (debugTextView == null) {
            debugTextView = createDebugTextView(appContext, simpleDateFormat.format(timestamp), 255)
            viewGroup?.addView(debugTextView)
        } else {
            val newContent = simpleDateFormat.format(timestamp) + "\n" + debugTextView?.text.toString()
            debugTextView?.setText(newContent)
        }
    }

    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        .apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun findContentView(window: Window): FrameLayout? {
        return (window.decorView as? ViewGroup)
            ?.findViewById<View>(android.R.id.content) as? FrameLayout
    }

    private fun createDebugTextView(context: Context, timestamp: String, alpha: Int): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(
                Color.argb(
                    alpha,
                    Color.red(ACTIVE_COLOR),
                    Color.green(ACTIVE_COLOR),
                    Color.blue(ACTIVE_COLOR)
                )
            )
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val paddingPx = dpToPx(4f, context)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            text = timestamp
        }
    }

    private fun dpToPx(dp: Float, context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        return ((dp * displayMetrics.density) + 0.5).toInt()
    }

    companion object {
        private val ACTIVE_COLOR = Color.rgb(99, 44, 166)
    }
}
