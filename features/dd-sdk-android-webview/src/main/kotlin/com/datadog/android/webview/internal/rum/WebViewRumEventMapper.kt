/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.webview.internal.rum.domain.NativeRumViewsCache
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject

internal class WebViewRumEventMapper(
    private val nativeRumViewsCache: NativeRumViewsCache
) {

    @Throws(
        ClassCastException::class,
        IllegalStateException::class,
        NumberFormatException::class
    )
    fun mapEvent(
        event: JsonObject,
        rumContext: RumContext?,
        timeOffset: Long,
        sessionReplayEnabled: Boolean
    ): JsonObject {
        val containerObject = JsonObject().apply {
            addProperty(SOURCE_KEY_NAME, SOURCE_VALUE)
        }
        event.get(DATE_KEY_NAME)?.asLong?.let { eventDate ->
            nativeRumViewsCache.resolveLastParentIdForBrowserEvent(eventDate)?.let {
                containerObject.add(
                    VIEW_KEY_NAME,
                    JsonObject().apply {
                        addProperty(ID_KEY_NAME, it)
                    }
                )
            }
            event.addProperty(DATE_KEY_NAME, eventDate + timeOffset)
        }
        event.add(CONTAINER_KEY_NAME, containerObject)
        val dd = event.get(DD_KEY_NAME)?.asJsonObject
        if (dd != null) {
            val ddSession = dd.get(DD_SESSION_KEY_NAME)?.asJsonObject ?: JsonObject()
            dd.add(DD_SESSION_KEY_NAME, ddSession)
            if (!sessionReplayEnabled) {
                // RUM-4084 disable webview SR if host app doesn't have SR
                dd.remove(DD_REPLAY_STATS)
            }
        }

        if (rumContext != null) {
            val application = event.getAsJsonObject(APPLICATION_KEY_NAME)?.asJsonObject
                ?: JsonObject()
            val session = event.getAsJsonObject(SESSION_KEY_NAME)?.asJsonObject ?: JsonObject()
            application.addProperty(ID_KEY_NAME, rumContext.applicationId)
            session.addProperty(ID_KEY_NAME, rumContext.sessionId)
            if (!sessionReplayEnabled) {
                // RUM-4084 disable webview SR if host app doesn't have SR
                session.remove(SESSION_HAS_REPLAY_NAME)
            }
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
        internal const val DD_REPLAY_STATS = "replay_stats"
        internal const val DATE_KEY_NAME = "date"
        internal const val ID_KEY_NAME = "id"
        internal const val SESSION_HAS_REPLAY_NAME = "has_replay"
        internal const val VIEW_KEY_NAME = "view"
        internal const val CONTAINER_KEY_NAME = "container"
        internal const val SOURCE_KEY_NAME = "source"
        internal const val SOURCE_VALUE = "android"
    }
}
