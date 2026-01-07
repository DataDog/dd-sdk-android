/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.constraints.DataConstraints
import com.datadog.android.core.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.utils.JsonSerializer.safeMapValuesToJson
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.RumVitalAppLaunchEvent
import com.datadog.android.rum.model.RumVitalOperationStepEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent
import com.google.gson.JsonObject

@Suppress("TooManyFunctions")
internal class RumEventSerializer(
    private val internalLogger: InternalLogger,
    private val dataConstraints: DataConstraints = DatadogDataConstraints(internalLogger)
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
            is RumVitalOperationStepEvent -> {
                serializeVitalOperationStepEvent(model)
            }
            is RumVitalAppLaunchEvent -> {
                serializeVitalAppLaunchEvent(model)
            }
            is TelemetryDebugEvent -> {
                model.toJson().toString()
            }
            is TelemetryErrorEvent -> {
                model.toJson().toString()
            }
            is TelemetryConfigurationEvent -> {
                model.toJson().toString()
            }
            is TelemetryUsageEvent -> {
                model.toJson().toString()
            }
            is JsonObject -> {
                model.toString()
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
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedAccount = model.account?.copy(
            additionalProperties = validateAccountAttributes(model.account.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
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
            account = sanitizedAccount,
            context = sanitizedContext,
            view = sanitizedView
        )

        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeErrorEvent(model: ErrorEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedAccount = model.account?.copy(
            additionalProperties = validateAccountAttributes(model.account.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            account = sanitizedAccount,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeResourceEvent(model: ResourceEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedAccount = model.account?.copy(
            additionalProperties = validateAccountAttributes(model.account.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            account = sanitizedAccount,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeActionEvent(model: ActionEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedAccount = model.account?.copy(
            additionalProperties = validateAccountAttributes(model.account.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            account = sanitizedAccount,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeLongTaskEvent(model: LongTaskEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedAccount = model.account?.copy(
            additionalProperties = validateAccountAttributes(model.account.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            account = sanitizedAccount,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeVitalOperationStepEvent(model: RumVitalOperationStepEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedAccount = model.account?.copy(
            additionalProperties = validateAccountAttributes(model.account.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            account = sanitizedAccount,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun serializeVitalAppLaunchEvent(model: RumVitalAppLaunchEvent): String {
        val sanitizedUser = model.usr?.copy(
            additionalProperties = validateUserAttributes(model.usr.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedAccount = model.account?.copy(
            additionalProperties = validateAccountAttributes(model.account.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedContext = model.context?.copy(
            additionalProperties = validateContextAttributes(model.context.additionalProperties)
                .safeMapValuesToJson(internalLogger)
                .toMutableMap()
        )
        val sanitizedModel = model.copy(
            usr = sanitizedUser,
            account = sanitizedAccount,
            context = sanitizedContext
        )
        return extractKnownAttributes(sanitizedModel.toJson().asJsonObject).toString()
    }

    private fun validateContextAttributes(attributes: Map<String, Any?>): MutableMap<String, Any?> {
        return dataConstraints.validateAttributes(
            attributes.filterKeys {
                it !in crossPlatformTransitAttributes
            },
            keyPrefix = GLOBAL_ATTRIBUTE_PREFIX,
            reservedKeys = ignoredAttributes
        )
    }

    private fun validateAccountAttributes(attributes: Map<String, Any?>): MutableMap<String, Any?> {
        return dataConstraints.validateAttributes(
            attributes,
            keyPrefix = ACCOUNT_ATTRIBUTE_PREFIX,
            attributesGroupName = ACCOUNT_EXTRA_GROUP_VERBOSE_NAME,
            reservedKeys = ignoredAttributes
        )
    }

    private fun validateUserAttributes(attributes: Map<String, Any?>): MutableMap<String, Any?> {
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
            RumAttributes.ACTION_GESTURE_FROM_STATE,
            RumAttributes.ACTION_GESTURE_TO_STATE,
            RumAttributes.ACTION_TARGET_PARENT_RESOURCE_ID,
            RumAttributes.ACTION_TARGET_PARENT_CLASSNAME,
            RumAttributes.ACTION_TARGET_PARENT_INDEX,
            RumAttributes.ACTION_TARGET_CLASS_NAME,
            RumAttributes.ACTION_TARGET_RESOURCE_ID,
            RumAttributes.ACTION_TARGET_TITLE,
            RumAttributes.ACTION_TARGET_SELECTED,
            RumAttributes.ACTION_TARGET_ROLE,
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

        // this are attributes which may come after the calls made by cross-platform SDKs (they are
        // needed only for the SDK internals) and we may silently drop them
        internal val crossPlatformTransitAttributes = setOf(
            RumAttributes.INTERNAL_TIMESTAMP,
            RumAttributes.INTERNAL_ERROR_TYPE,
            RumAttributes.INTERNAL_ERROR_SOURCE_TYPE,
            RumAttributes.INTERNAL_ERROR_IS_CRASH
        )

        internal const val GLOBAL_ATTRIBUTE_PREFIX: String = "context"
        internal const val USER_ATTRIBUTE_PREFIX: String = "usr"
        internal const val ACCOUNT_ATTRIBUTE_PREFIX: String = "account"
        internal const val USER_EXTRA_GROUP_VERBOSE_NAME = "user extra information"
        internal const val ACCOUNT_EXTRA_GROUP_VERBOSE_NAME = "account extra information"
    }
}
