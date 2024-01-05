/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.widget

import android.app.IntentService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication
import java.io.IOException
import okhttp3.Request
import timber.log.Timber

/**
 * An [WidgetIntentService] to showcase tracking interactions with a home screen widget.
 */
class WidgetIntentService : IntentService("WidgetIntentService") {

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            LOAD_RANDOM_RESOURCE_ACTION -> {
                val widgetName = intent.getStringExtra(WIDGET_NAME_ARG)
                val widgetId = intent.getIntExtra(WIDGET_ID_ARG, 0)
                val hasRumContext = widgetId != 0 && widgetName != null

                if (hasRumContext) {
                    GlobalRumMonitor.get()
                        .startView(widgetId, widgetName ?: "DatadogWidget", emptyMap())
                    val clickedTargetName = intent.getStringExtra(WIDGET_CLICKED_TARGET_NAME)
                    if (clickedTargetName != null) {
                        GlobalRumMonitor.get()
                            .addAction(RumActionType.CLICK, clickedTargetName, emptyMap())
                    }
                }

                performRequest()

                if (hasRumContext) {
                    GlobalRumMonitor.get().stopView(widgetId)
                }
            }
            else -> {
            }
        }
    }

    private fun performRequest() {
        updateUIStatus(applicationContext, true)
        val okHttpClient = SampleApplication.getOkHttpClient(applicationContext)
        val builder = Request.Builder()
            .get()
            .url("https://www.datadoghq.com/")

        val request = builder.build()
        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body
            if (body != null) {
                val content: String = body.string()
                // Necessary to consume the response
                Timber.d("Response: $content")
            }
        } catch (e: IOException) {
            Timber.e("Error: ${e.message}")
        }
        updateUIStatus(applicationContext, false)
    }

    private fun updateUIStatus(context: Context, isLoading: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        appWidgetManager.getAppWidgetIds(
            ComponentName(
                context,
                DatadogWidgetsProvider::class.java
            )
        ).forEach {
            val remoteViews = RemoteViews(
                context.packageName,
                R.layout.datadog_widget
            )
            val visibility = if (isLoading) View.VISIBLE else View.GONE
            remoteViews.setTextViewText(R.id.status_field, context.getText(R.string.loading))
            remoteViews.setViewVisibility(R.id.status_field, visibility)
            appWidgetManager.updateAppWidget(it, remoteViews)
        }
    }

    companion object {
        internal const val LOAD_RANDOM_RESOURCE_ACTION = "load_random_resource"
        internal const val WIDGET_ID_ARG = "widget_id"
        internal const val WIDGET_NAME_ARG = "widget_name"
        internal const val WIDGET_CLICKED_TARGET_NAME = "widget_clicked_target_name"
    }
}
