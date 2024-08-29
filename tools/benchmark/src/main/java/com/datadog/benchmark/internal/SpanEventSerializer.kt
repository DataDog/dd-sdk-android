/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal

import com.datadog.benchmark.internal.model.SpanEvent
import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal class SpanEventSerializer {
    // region Serializer

    fun serialize(env: String, spanEvents: List<SpanEvent>): String {
        val stringBuilder = StringBuilder()
        spanEvents.forEach { spanEvent ->
            val spans = JsonArray(1)
            spans.add(spanEvent.toJson())
            val jsonObject = JsonObject()
            jsonObject.add(TAG_SPANS, spans)
            jsonObject.addProperty(TAG_ENV, env)
            stringBuilder.append(jsonObject.toString())
            stringBuilder.append("\n")
        }
        return stringBuilder.toString()
    }

    // end region

    companion object {

        // PAYLOAD TAGS
        internal const val TAG_SPANS = "spans"
        internal const val TAG_ENV = "env"
    }
}
