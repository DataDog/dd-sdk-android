/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.webview

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParseException

internal class RumEventConsumer(
    private val dataWriter: DataWriter<Any>,
    private val timeProvider: TimeProvider
) {

    private var currentOffset = timeProvider.getServerOffsetMillis()

    fun consume(event: JsonObject, eventType: String) {
        if (!isSessionDropped()) {
            val mappedEvent = map(event, eventType)
            if (mappedEvent != null) {
                dataWriter.write(mappedEvent)
            }
        }
    }

    private fun map(event: JsonObject, eventType: String): JsonObject? {
        val rumContext = GlobalRum.getRumContext()

        return try {
            when (eventType) {
                VIEW_EVENT_TYPE -> {
                    currentOffset = timeProvider.getServerOffsetMillis()
                    ViewEvent.fromJson(event.toString()).mapToMobile(rumContext, currentOffset)
                }
                ACTION_EVENT_TYPE -> {
                    ActionEvent.fromJson(event.toString()).mapToMobile(rumContext, currentOffset)
                }
                RESOURCE_EVENT_TYPE -> {
                    ResourceEvent.fromJson(event.toString()).mapToMobile(rumContext, currentOffset)
                }
                ERROR_EVENT_TYPE -> {
                    ErrorEvent.fromJson(event.toString()).mapToMobile(rumContext, currentOffset)
                }
                LONG_TASK_EVENT_TYPE -> {
                    LongTaskEvent.fromJson(event.toString()).mapToMobile(rumContext, currentOffset)
                }
                else -> {
                    null
                }
            }
        } catch (e: JsonParseException) {
            sdkLogger.e(JSON_PARSING_ERROR_MESSAGE, e)
            null
        }
    }

    private fun isSessionDropped(): Boolean {
        val appScope = (GlobalRum.get() as? DatadogRumMonitor)?.rootScope as? RumApplicationScope
            ?: return false
        return (appScope.childScope as? RumSessionScope)?.keepSession ?: false
    }

    private fun ViewEvent.mapToMobile(context: RumContext, timeOffset: Long): JsonObject {
        val copy = this.copy(
            application = this.application.copy(id = context.applicationId),
            session = this.session.copy(id = context.sessionId),
            service = CoreFeature.serviceName,
            date = this.date + timeOffset
        )
        return copy.toJson().asJsonObject
    }

    private fun ActionEvent.mapToMobile(context: RumContext, timeOffset: Long): JsonObject {
        val copy = this.copy(
            application = this.application.copy(id = context.applicationId),
            session = this.session.copy(id = context.sessionId),
            service = CoreFeature.serviceName,
            date = this.date + timeOffset
        )
        return copy.toJson().asJsonObject
    }

    private fun ResourceEvent.mapToMobile(context: RumContext, timeOffset: Long): JsonObject {
        val copy = this.copy(
            application = this.application.copy(id = context.applicationId),
            session = this.session.copy(id = context.sessionId),
            service = CoreFeature.serviceName,
            date = this.date + timeOffset
        )
        return copy.toJson().asJsonObject
    }

    private fun ErrorEvent.mapToMobile(context: RumContext, timeOffset: Long): JsonObject {
        val copy = this.copy(
            application = this.application.copy(id = context.applicationId),
            session = this.session.copy(id = context.sessionId),
            service = CoreFeature.serviceName,
            date = this.date + timeOffset
        )
        return copy.toJson().asJsonObject
    }

    private fun LongTaskEvent.mapToMobile(context: RumContext, timeOffset: Long): JsonObject {
        val copy = this.copy(
            application = this.application.copy(id = context.applicationId),
            session = this.session.copy(id = context.sessionId),
            service = CoreFeature.serviceName,
            date = this.date + timeOffset
        )
        return copy.toJson().asJsonObject
    }

    companion object {
        const val VIEW_EVENT_TYPE = "view"
        const val ACTION_EVENT_TYPE = "action"
        const val RESOURCE_EVENT_TYPE = "resource"
        const val ERROR_EVENT_TYPE = "error"
        const val LONG_TASK_EVENT_TYPE = "long_task"
        const val JSON_PARSING_ERROR_MESSAGE = "The RUM event could not be serialized"
    }
}
