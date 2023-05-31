/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain.event

import com.datadog.android.core.constraints.DataConstraints
import com.datadog.android.core.constraints.DatadogDataConstraints
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.api.InternalLogger

internal class LogEventSerializer(
    internalLogger: InternalLogger,
    private val dataConstraints: DataConstraints = DatadogDataConstraints(internalLogger)
) : Serializer<LogEvent> {

    override fun serialize(model: LogEvent): String {
        return sanitizeTagsAndAttributes(model).toJson().toString()
    }

    private fun sanitizeTagsAndAttributes(log: LogEvent): LogEvent {
        val sanitizedTags = dataConstraints
            .validateTags(log.ddtags.split(","))
            .joinToString(",")
        val sanitizedAttributes = dataConstraints
            .validateAttributes(log.additionalProperties)
            .filterKeys { it.isNotBlank() }
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
            additionalProperties = sanitizedAttributes.toMutableMap(),
            usr = usr
        )
    }

    companion object {
        internal const val USER_EXTRA_GROUP_VERBOSE_NAME = "user extra information"
    }
}
