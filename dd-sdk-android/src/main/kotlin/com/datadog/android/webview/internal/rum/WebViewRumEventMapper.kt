/*
 * Unless explicitly stated otherwise all files in event repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import java.lang.ClassCastException
import java.lang.IllegalStateException
import java.lang.NumberFormatException

internal class WebViewRumEventMapper {

    @Throws(
        ClassCastException::class,
        IllegalStateException::class,
        NumberFormatException::class
    )
    fun mapEvent(
        event: JsonObject,
        context: RumContext?,
        timeOffset: Long
    ): JsonObject {

        event.get(DATE_KEY_NAME)?.asLong?.let {
            event.addProperty(DATE_KEY_NAME, it + timeOffset)
        }
        val dd = event.get(DD_KEY_NAME)?.asJsonObject
        if (dd != null) {
            val ddSession = dd.get(DD_SESSION_KEY_NAME)?.asJsonObject ?: JsonObject()
            ddSession.addProperty(SESSION_PLAN_KEY_NAME, ViewEvent.Plan.PLAN_1.toJson().asInt)
            dd.add(DD_SESSION_KEY_NAME, ddSession)
        }
        if (context != null) {
            val application = event.getAsJsonObject(APPLICATION_KEY_NAME)?.asJsonObject
                ?: JsonObject()
            val session = event.getAsJsonObject(SESSION_KEY_NAME)?.asJsonObject ?: JsonObject()
            application.addProperty(ID_KEY_NAME, context.applicationId)
            session.addProperty(ID_KEY_NAME, context.sessionId)
            event.add(APPLICATION_KEY_NAME, application)
            event.add(SESSION_KEY_NAME, session)
        }
        return event
    }

    companion object {
        internal const val APPLICATION_KEY_NAME = "application"
        internal const val SESSION_KEY_NAME = "session"
        internal const val DD_KEY_NAME = "_dd"
        internal const val DD_SESSION_KEY_NAME = "session"
        internal const val SESSION_PLAN_KEY_NAME = "plan"
        internal const val DATE_KEY_NAME = "date"
        internal const val ID_KEY_NAME = "id"
    }
}
