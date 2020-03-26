/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.rum.RumAttributes
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

        // App Context
        root.addProperty(RumAttributes.APPLICATION_VERSION, CoreFeature.packageVersion)
        root.addProperty(RumAttributes.APPLICATION_PACKAGE, CoreFeature.packageName)

        // Event Context
        val context = model.context
        root.addProperty(RumAttributes.APPLICATION_ID, context.applicationId.toString())
        root.addProperty(RumAttributes.SESSION_ID, context.sessionId.toString())
        root.addProperty(RumAttributes.VIEW_ID, context.viewId.toString())

        // Timestamp
        val formattedDate = simpleDateFormat.format(Date(model.timestamp))
        root.addProperty(RumAttributes.DATE, formattedDate)

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
            root.addProperty(RumAttributes.USER_EMAIL, email)
        }
        if (!id.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_ID, id)
        }
        if (!name.isNullOrEmpty()) {
            root.addProperty(RumAttributes.USER_NAME, name)
        }
    }

    private fun addEventData(eventData: RumEventData, root: JsonObject) {
        root.addProperty(RumAttributes.EVT_CATEGORY, eventData.category)
        when (eventData) {
            is RumEventData.Resource -> addResourceData(eventData, root)
            is RumEventData.UserAction -> addUserActionData(eventData, root)
            is RumEventData.View -> addViewData(eventData, root)
            is RumEventData.Error -> addErrorData(root, eventData)
        }
    }

    private fun addViewData(
        eventData: RumEventData.View,
        root: JsonObject
    ) {
        root.addProperty(RumAttributes.RUM_DOCUMENT_VERSION, eventData.version)
        root.addProperty(RumAttributes.VIEW_URL, eventData.name)
        root.addProperty(RumAttributes.DURATION, eventData.durationNanoSeconds)
        val measures = eventData.measures
        root.addProperty(RumAttributes.VIEW_MEASURES_ERROR_COUNT, measures.errorCount)
        root.addProperty(RumAttributes.VIEW_MEASURES_RESOURCE_COUNT, measures.resourceCount)
        root.addProperty(RumAttributes.VIEW_MEASURES_USER_ACTION_COUNT, measures.userActionCount)
    }

    private fun addUserActionData(
        eventData: RumEventData.UserAction,
        root: JsonObject
    ) {
        root.addProperty(RumAttributes.EVT_NAME, eventData.name)
        root.addProperty(RumAttributes.EVT_ID, eventData.id.toString())
        root.addProperty(RumAttributes.DURATION, eventData.durationNanoSeconds)
    }

    private fun addResourceData(
        eventData: RumEventData.Resource,
        root: JsonObject
    ) {
        root.addProperty(RumAttributes.DURATION, eventData.durationNanoSeconds)
        root.addProperty(RumAttributes.RESOURCE_KIND, eventData.kind.value)
        root.addProperty(RumAttributes.HTTP_URL, eventData.url)
    }

    private fun addErrorData(
        root: JsonObject,
        eventData: RumEventData.Error
    ) {
        root.addProperty(RumAttributes.ERROR_MESSAGE, eventData.message)
        root.addProperty(RumAttributes.ERROR_ORIGIN, eventData.origin)

        val throwable = eventData.throwable
        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            root.addProperty(RumAttributes.ERROR_KIND, throwable.javaClass.simpleName)
            root.addProperty(RumAttributes.ERROR_STACK, sw.toString())
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
    }
}
