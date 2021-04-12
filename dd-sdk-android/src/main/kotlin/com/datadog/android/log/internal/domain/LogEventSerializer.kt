/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.model.LogEvent

internal class LogEventSerializer(
    private val dataConstraints: DataConstraints = DatadogDataConstraints()
) :
    Serializer<LogEvent> {

    override fun serialize(model: LogEvent): String {
        return sanitizeTagsAndAttributes(model).toJson().toString()
    }

    private fun sanitizeTagsAndAttributes(log: LogEvent): LogEvent {
        val sanitizedTags = dataConstraints
            .validateTags(log.ddtags.split(","))
            .joinToString(",")
        // TODO: 13/04/2021 RUMM-1298 Handle the RESERVED_ATTRIBUTES in the `toJson` method
        val sanitizedAttributes = dataConstraints
            .validateAttributes(log.additionalProperties)
            .filterKeys { it.isNotBlank() && it !in RESERVED_PROPERTIES }
        val usr = log.usr?.let {
            val sanitizedUserAttributes = dataConstraints.validateAttributes(
                it.additionalProperties,
                keyPrefix = LogAttributes.USR_ATTRIBUTES_GROUP,
                attributesGroupName = USER_EXTRA_GROUP_VERBOSE_NAME
            )
            it.copy(additionalProperties = sanitizedUserAttributes)
        }
        return log.copy(
            ddtags = sanitizedTags,
            additionalProperties = sanitizedAttributes,
            usr = usr
        )
    }

    companion object {
        internal const val USER_EXTRA_GROUP_VERBOSE_NAME = "user extra information"

        internal val RESERVED_PROPERTIES: List<String> = listOf(
            "status", "service", "message",
            "date", "logger", "usr", "network", "error", "ddtags"
        )
    }
}
