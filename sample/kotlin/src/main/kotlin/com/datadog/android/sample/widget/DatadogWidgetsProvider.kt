/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.datadog.android.sample.R

/**
 * Implementation of App Widget functionality.
 */
class DatadogWidgetsProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(
                context,
                appWidgetManager,
                appWidgetId
            )
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(
            context.packageName,
            R.layout.datadog_widget
        )
        val loadResourceIntent = buildLoadResourceIntent(context, appWidgetId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getService(
            context.applicationContext,
            0,
            loadResourceIntent,
            flags
        )
        views.setOnClickPendingIntent(R.id.perform_http_request_button, pendingIntent)

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun buildLoadResourceIntent(
        context: Context,
        appWidgetId: Int
    ): Intent {
        val loadResourceIntent =
            Intent(context.applicationContext, WidgetIntentService::class.java)
                .apply {
                    action = WidgetIntentService.LOAD_RANDOM_RESOURCE_ACTION
                    putExtra(WidgetIntentService.WIDGET_NAME_ARG, "DatadogWidget")
                    putExtra(WidgetIntentService.WIDGET_ID_ARG, appWidgetId)
                    val resources = context.applicationContext.resources
                    val targetName =
                        resources.getResourceEntryName(R.id.perform_http_request_button)
                    putExtra(WidgetIntentService.WIDGET_CLICKED_TARGET_NAME, targetName)
                }
        return loadResourceIntent
    }
}
