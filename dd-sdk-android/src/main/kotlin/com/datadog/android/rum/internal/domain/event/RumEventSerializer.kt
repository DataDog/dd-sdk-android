/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject

internal class RumEventSerializer(
    private val dataConstraints: DataConstraints = DatadogDataConstraints()
) : Serializer<Any> {

    // region Serializer

    override fun serialize(model: Any): String {
        return when (model) {
            is ViewEvent -> {
                serializeViewEvent(model)
            }
            is ErrorEvent -> {
                serializeErrorEvent(model)
            }
            is ActionEvent -> {
                serializeActionEvent(model)
            }
            is ResourceEvent -> {
                serializeResourceEvent(model)
            }
            is LongTaskEvent -> {
                serializeLongTaskEvent(model)
            }
            else -> {
                JsonObject().toString()
            }
        }
    }

    // endregion

    // region Internal

    private fun serializeViewEvent(model: ViewEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
        )
        val sanitizedView = model.view.copy(
            customTimings = model.view.customTimings?.copy(
                additionalProperties = dataConstraints.validateTimings(
                    model.view.customTimings.additionalProperties
                )
            )
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            context = sanitizedContext,
            view = sanitizedView
        )

        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeErrorEvent(model: ErrorEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeResourceEvent(model: ResourceEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeActionEvent(model: ActionEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeLongTaskEvent(model: LongTaskEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun validateContextAttributes(attributes: Map<String, Any?>): Map<String, Any?> {
        return dataConstraints.validateAttributes(
            attributes,
            keyPrefix = GLOBAL_ATTRIBUTE_PREFIX,
            reservedKeys = ignoredAttributes
        )
    }

    private fun validateUserAttributes(attributes: Map<String, Any?>): Map<String, Any?> {
        return dataConstraints.validateAttributes(
            attributes,
            keyPrefix = USER_ATTRIBUTE_PREFIX,
            attributesGroupName = USER_EXTRA_GROUP_VERBOSE_NAME,
            reservedKeys = ignoredAttributes
        )
    }

    private fun extractKnownAttributes(jsonObject: JsonObject): JsonObject {
        if (jsonObject.has(GLOBAL_ATTRIBUTE_PREFIX)) {
            val contextObject = jsonObject.getAsJsonObject(GLOBAL_ATTRIBUTE_PREFIX)
            contextObject
                .entrySet()
                .filter { it.key in knownAttributes }
                .forEach {
                    contextObject.remove(it.key)
                    jsonObject.add(it.key, it.value)
                }
        }
        return jsonObject
    }

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

        internal val ignoredAttributes = setOf(
            RumAttributes.INTERNAL_TIMESTAMP,
            RumAttributes.INTERNAL_ERROR_TYPE,
            RumAttributes.INTERNAL_ERROR_SOURCE_TYPE,
            RumAttributes.INTERNAL_ERROR_IS_CRASH
        )

        internal const val GLOBAL_ATTRIBUTE_PREFIX: String = "context"
        internal const val USER_ATTRIBUTE_PREFIX: String = "usr"
        internal const val USER_EXTRA_GROUP_VERBOSE_NAME = "user extra information"
    }
}
