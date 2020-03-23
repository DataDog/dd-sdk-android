/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.core.internal.domain.Serializer
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class RumEventSerializer : Serializer<RumEvent> {

    private val simpleDateFormat = SimpleDateFormat(ISO_8601, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // region Serializer

    override fun serialize(model: RumEvent): String {
        val root = JsonObject()

        // Event Context
        val context = model.context
        root.addProperty(TAG_APPLICATION_ID, context.applicationId.toString())
        root.addProperty(TAG_SESSION_ID, context.sessionId.toString())
        root.addProperty(TAG_VIEW_ID, context.viewId.toString())

        // Timestamp
        val formattedDate = simpleDateFormat.format(Date(model.timestamp))
        root.addProperty(TAG_DATE, formattedDate)

        // User Info
        addUserInfo(model, root)

        // Data
        addEventData(model.eventData, root)

        // custom attributes
        addCustomAttributes(model, root)

        return root.toString()
    }

    // endregion

    // region Internal

    private fun addUserInfo(model: RumEvent, root: JsonObject) {
        val email = model.userInfo.email
        val id = model.userInfo.id
        val name = model.userInfo.name

        if (!email.isNullOrEmpty()) {
            root.addProperty(TAG_USER_EMAIL, email)
        }
        if (!id.isNullOrEmpty()) {
            root.addProperty(TAG_USER_ID, id)
        }
        if (!name.isNullOrEmpty()) {
            root.addProperty(TAG_USER_NAME, name)
        }
    }

    private fun addEventData(eventData: RumEventData, root: JsonObject) {
        root.addProperty(TAG_EVENT_CATEGORY, eventData.category)
        when (eventData) {
            is RumEventData.Resource -> {
                root.addProperty(TAG_DURATION, eventData.durationNanoSeconds)
                root.addProperty(TAG_RESOURCE_KIND, eventData.kind.value)
                root.addProperty(TAG_HTTP_URL, eventData.url)
            }
            is RumEventData.UserAction -> {
                root.addProperty(TAG_EVENT_NAME, eventData.name)
                root.addProperty(TAG_EVENT_ID, eventData.id.toString())
                root.addProperty(TAG_DURATION, eventData.durationNanoSeconds)
            }
            is RumEventData.View -> {
                root.addProperty(TAG_RUM_DOC_VERSION, eventData.version)
                root.addProperty(TAG_VIEW_URL, eventData.name)
                root.addProperty(TAG_DURATION, eventData.durationNanoSeconds)
                root.addProperty(TAG_MEASURES_ERRORS, eventData.measures.errorCount)
                root.addProperty(TAG_MEASURES_RESOURCES, eventData.measures.resourceCount)
                root.addProperty(TAG_MEASURES_ACTIONS, eventData.measures.userActionCount)
            }
            is RumEventData.Error -> {
                val sw = StringWriter()
                eventData.throwable.printStackTrace(PrintWriter(sw))
                root.addProperty(TAG_MESSAGE, eventData.message)
                root.addProperty(TAG_ERROR_ORIGIN, eventData.origin)
                root.addProperty(TAG_ERROR_KIND, eventData.throwable.javaClass.simpleName)
                root.addProperty(TAG_ERROR_MESSAGE, eventData.throwable.message)
                root.addProperty(TAG_ERROR_STACK, sw.toString())
            }
        }
    }

    private fun addCustomAttributes(
        event: RumEvent,
        jsonEvent: JsonObject
    ) {
        event.attributes.forEach {
            val value = it.value
            val jsonValue = when (value) {
                null -> JsonNull.INSTANCE
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is Long -> JsonPrimitive(value)
                is Float -> JsonPrimitive(value)
                is Double -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Date -> JsonPrimitive(value.time)
                is JsonObject -> value
                is JsonArray -> value
                else -> JsonPrimitive(value.toString())
            }
            jsonEvent.add(it.key, jsonValue)
        }
    }

    // endregion

    companion object {
        private const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        internal const val TAG_DATE = "date"
        internal const val TAG_DURATION = "duration"
        internal const val TAG_ENV = "env"
        internal const val TAG_SERVICE = "service"
        internal const val TAG_MESSAGE = "message"
        internal const val TAG_USER_ID = "user.id"
        internal const val TAG_USER_EMAIL = "user.email"
        internal const val TAG_USER_NAME = "user.name"

        internal const val TAG_APPLICATION_ID = "application_id"
        internal const val TAG_SESSION_ID = "session_id"

        internal const val TAG_VIEW_ID = "view.id"
        internal const val TAG_VIEW_URL = "view.url"
        internal const val TAG_MEASURES_ERRORS = "view.measures.error_count"
        internal const val TAG_MEASURES_RESOURCES = "view.measures.resource_count"
        internal const val TAG_MEASURES_ACTIONS = "view.measures.user_action_count"

        internal const val TAG_EVENT_CATEGORY = "evt.category"
        internal const val TAG_EVENT_ID = "evt.id"
        internal const val TAG_EVENT_NAME = "evt.name"
        internal const val TAG_EVENT_UNSTOPPED = "evt.unstopped"
        internal const val TAG_EVENT_USER_ACTION_ID = "evt.user_action_id"

        internal const val TAG_RESOURCE_KIND = "resource.kind"

        internal const val TAG_ERROR_ORIGIN = "error.origin"
        internal const val TAG_ERROR_KIND = "error.kind"
        internal const val TAG_ERROR_MESSAGE = "error.message"
        internal const val TAG_ERROR_STACK = "error.stack"

        internal const val TAG_RUM_DOC_VERSION = "rum.document_version"

        internal const val TAG_HTTP_URL = "http.url"
        internal const val TAG_HTTP_METHOD = "http.method"
        internal const val TAG_HTTP_STATUS_CODE = "http.status_code"

        internal const val TAG_NETWORK_BYTES_WRITTEN = "network.bytes_written"
    }
}
