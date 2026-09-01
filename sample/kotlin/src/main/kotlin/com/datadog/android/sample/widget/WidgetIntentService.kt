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
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/**
 * A service to showcase tracking interactions with a home screen widget.
 */
class WidgetIntentService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workMutex = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            try {
                workMutex.withLock {
                    handleIntent(intent)
                }
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            LOAD_RANDOM_RESOURCE_ACTION -> {
                val widgetName = intent.getStringExtra(WIDGET_NAME_ARG)
                val widgetId = intent.getIntExtra(WIDGET_ID_ARG, 0)
                val hasRumContext = widgetId != 0 && widgetName != null

                if (hasRumContext) {
                    GlobalRumMonitor.get()
                        .startView(widgetId, widgetName ?: "DatadogWidget")
                    val clickedTargetName = intent.getStringExtra(WIDGET_CLICKED_TARGET_NAME)
                    if (clickedTargetName != null) {
                        GlobalRumMonitor.get()
                            .addAction(RumActionType.CLICK, clickedTargetName)
                    }
                }

                try {
                    performRequest()
                } finally {
                    if (hasRumContext) {
                        GlobalRumMonitor.get().stopView(widgetId)
                    }
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
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body
                if (body != null) {
                    val content: String = body.string()
                    // Necessary to consume the response
                    Timber.d("Response: $content")
                }
            }
        } catch (e: IOException) {
            Timber.e("Error: ${e.message}")
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
