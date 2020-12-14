/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.utils.toJsonElement
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal class RumEventSerializer(
    private val dataConstraints: DataConstraints = DatadogDataConstraints()
) :
    Serializer<RumEvent> {

    // region Serializer

    override fun serialize(model: RumEvent): String {
        val json = model.event.toJson().asJsonObject

        addCustomAttributes(
            dataConstraints.validateAttributes(
                model.globalAttributes,
                keyPrefix = GLOBAL_ATTRIBUTE_PREFIX
            ),
            json,
            GLOBAL_ATTRIBUTE_PREFIX,
            knownAttributes
        )
        addCustomAttributes(
            dataConstraints.validateAttributes(
                model.userExtraAttributes,
                keyPrefix = USER_ATTRIBUTE_PREFIX,
                attributesGroupName = USER_EXTRA_GROUP_VERBOSE_NAME
            ),
            json,
            USER_ATTRIBUTE_PREFIX
        )
        model.customTimings?.let {
            addCustomAttributes(
                dataConstraints.validateAttributes(
                    it,
                    keyPrefix = VIEW_CUSTOM_TIMINGS_ATTRIBUTE_PREFIX,
                    attributesGroupName = CUSTOM_TIMINGS_GROUP_VERBOSE_NAME
                ),
                json,
                VIEW_CUSTOM_TIMINGS_ATTRIBUTE_PREFIX
            )
        }

        return json.toString()
    }

    // endregion

    // region Internal

    private fun addCustomAttributes(
        attributes: Map<String, Any?>,
        jsonEvent: JsonObject,
        keyPrefix: String,
        knownAttributesKeys: Set<String> = emptySet()
    ) {
        attributes.forEach {
            val rawKey = it.key
            val key =
                if (rawKey in knownAttributesKeys) rawKey else "$keyPrefix.$rawKey"
            val value = it.value
            jsonEvent.add(key, value.toJsonElement())
        }
    }

    // endregion

    companion object {
        internal val knownAttributes = setOf(
            RumAttributes.ACTION_GESTURE_DIRECTION,
            RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID,
            RumAttributes.ACTION_TARGET_PARENT_CLASSNAME,
            RumAttributes.ACTION_TARGET_PARENT_INDEX,
            RumAttributes.ACTION_TARGET_CLASS_NAME,
            RumAttributes.ACTION_TARGET_RESOURCE_ID,
            RumAttributes.ACTION_TARGET_TITLE,
            RumAttributes.ERROR_RESOURCE_METHOD,
            RumAttributes.ERROR_RESOURCE_STATUS_CODE,
            RumAttributes.ERROR_RESOURCE_URL
        )

        internal const val GLOBAL_ATTRIBUTE_PREFIX: String = "context"
        internal const val USER_ATTRIBUTE_PREFIX: String = "$GLOBAL_ATTRIBUTE_PREFIX.usr"
        internal const val VIEW_CUSTOM_TIMINGS_ATTRIBUTE_PREFIX: String = "view.custom_timings"
        internal const val USER_EXTRA_GROUP_VERBOSE_NAME = "user extra information"
        internal const val CUSTOM_TIMINGS_GROUP_VERBOSE_NAME = "view custom timings"
    }
}

private fun Any.toJson(): JsonElement {
    return when (this) {
        is ViewEvent -> toJson()
        is ActionEvent -> toJson()
        is ResourceEvent -> toJson()
        is ErrorEvent -> toJson()
        else -> JsonObject()
    }
}
