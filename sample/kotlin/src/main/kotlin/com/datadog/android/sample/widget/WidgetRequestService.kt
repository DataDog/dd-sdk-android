/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

class WidgetRequestService : Service() {

    private lateinit var requestThread: HandlerThread
    private lateinit var requestHandler: Handler

    override fun onCreate() {
        super.onCreate()
        requestThread = HandlerThread("WidgetRequestService").apply { start() }
        requestHandler = Handler(requestThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        requestHandler.post {
            try {
                if (intent?.action == LOAD_RANDOM_RESOURCE_ACTION) {
                    loadRandomResource(intent)
                }
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        requestThread.quit()
        super.onDestroy()
    }

    private fun loadRandomResource(intent: Intent) {
        val widgetName = intent.getStringExtra(WIDGET_NAME_ARG)
        val widgetId = intent.getIntExtra(WIDGET_ID_ARG, 0)
        val rum = if (widgetId != 0 && widgetName != null) GlobalRumMonitor.get() else null

        if (rum != null && widgetName != null) {
            rum.startView(widgetId, widgetName)
            intent.getStringExtra(WIDGET_CLICKED_TARGET_NAME)?.let { targetName ->
                rum.addAction(RumActionType.CLICK, targetName)
            }
        }

        try {
            performRequest()
        } finally {
            rum?.stopView(widgetId)
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
            okHttpClient.newCall(request).execute().use { response ->
                response.body?.let { body ->
                    Timber.d("Response: %s", body.string())
                }
            }
        } catch (e: IOException) {
            Timber.e("Error: %s", e.message)
        } finally {
            updateUIStatus(applicationContext, false)
        }
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
