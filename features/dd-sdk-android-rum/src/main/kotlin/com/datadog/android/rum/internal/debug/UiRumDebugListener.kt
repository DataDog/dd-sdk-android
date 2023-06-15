/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.debug

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.setPadding
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.NoOpAdvancedRumMonitor
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import java.util.Locale
import kotlin.math.pow

internal class UiRumDebugListener(private val sdkCore: FeatureSdkCore) :
    Application.ActivityLifecycleCallbacks, RumDebugListener {

    internal var rumViewsContainer: LinearLayout? = null

    private val viewsSnapshot = mutableListOf<String>()

    private val advancedRumMonitor by lazy {
        val monitor = GlobalRum.get(sdkCore) as? AdvancedRumMonitor
        if (monitor == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                MISSING_RUM_MONITOR_TYPE.format(
                    Locale.US,
                    AdvancedRumMonitor::class.qualifiedName
                )
            )
            NoOpAdvancedRumMonitor()
        } else {
            monitor
        }
    }

    // region Application.ActivityLifecycleCallbacks

    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // no-op
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        // no-op
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        if (advancedRumMonitor is NoOpAdvancedRumMonitor) {
            return
        }

        val contentView = findContentView(activity)
        if (contentView == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                CANNOT_FIND_CONTENT_VIEW_MESSAGE
            )
            return
        }

        rumViewsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // view added is not null
        contentView.addView(
            rumViewsContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        )

        advancedRumMonitor.setDebugListener(this)
    }

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        if (advancedRumMonitor is NoOpAdvancedRumMonitor) {
            return
        }
        findContentView(activity)?.removeView(rumViewsContainer)
        rumViewsContainer = null
        advancedRumMonitor.setDebugListener(null)
        viewsSnapshot.clear()
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        // no-op
    }

    @MainThread
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // no-op
    }

    @MainThread
    override fun onActivityDestroyed(activity: Activity) {
        // no-op
    }

    // endregion

    // region RumDebugListener

    @AnyThread
    override fun onReceiveRumActiveViews(viewNames: List<String>) {
        synchronized(viewsSnapshot) {
            if (viewsSnapshot.isEmpty() ||
                viewsSnapshot.size != viewNames.size ||
                viewsSnapshot.withIndex().any { it.value != viewNames.getOrNull(it.index) }
            ) {
                viewsSnapshot.clear()
                viewsSnapshot.addAll(viewNames)
                rumViewsContainer?.post {
                    @Suppress("ThreadSafety") // View.post() ensures we are in the UI Thread
                    showRumViewsInfo(viewNames)
                }
            }
        }
    }

    // endregion

    // region private

    @Suppress("MagicNumber")
    @UiThread
    private fun showRumViewsInfo(viewNames: List<String>) {
        rumViewsContainer?.run {
            removeAllViews()
            if (viewNames.isEmpty()) {
                @Suppress("UnsafeThirdPartyFunctionCall") // view added is not null
                addView(createDebugTextView(context, "No active RUM View", DEFAULT_ALPHA))
            } else {
                for (viewName in viewNames.reversed().withIndex()) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // view added is not null
                    addView(
                        createDebugTextView(
                            context,
                            viewName.value,
                            (255 * (0.75.pow(viewName.index + 1))).toInt()
                        )
                    )
                }
            }
        }
    }

    private fun createDebugTextView(context: Context, viewName: String, alpha: Int): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(
                Color.argb(
                    alpha,
                    ACTIVE_COLOR.red,
                    ACTIVE_COLOR.green,
                    ACTIVE_COLOR.blue
                )
            )
            setTextColor(Color.WHITE)
            setPadding(dpToPx(2f, context))
            text = viewName
        }
    }

    private fun findContentView(activity: Activity): FrameLayout? {
        return (activity.window.decorView as? ViewGroup)
            ?.findViewById<View>(android.R.id.content) as? FrameLayout
    }

    @Suppress("SameParameterValue", "MagicNumber")
    private fun dpToPx(dp: Float, context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        return ((dp * displayMetrics.density) + 0.5).toInt()
    }

    // endregion

    companion object {
        const val CANNOT_FIND_CONTENT_VIEW_MESSAGE =
            "Cannot enable RUM debugging, because root content view can't be found"
        const val MISSING_RUM_MONITOR_TYPE =
            "Cannot enable RUM debugging, because global RUM monitor" +
                " doesn't implement %s"
        const val DEFAULT_ALPHA = 200
        val ACTIVE_COLOR = Color.rgb(99, 44, 166)
    }
}
