/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration.tests.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject

/**
 * A flag assignment, as returned by the precompute-assignments endpoint.
 */
data class FakeAssignment(
    val variationType: String,
    val variationValue: Any,
    val variationKey: String,
    val allocationKey: String,
    val doLog: Boolean = true,
    val reason: String = "TARGETING_MATCH",
    val serialId: Long? = null,
    val extraLogging: Map<String, Any> = emptyMap()
)

/**
 * Builds the body of a precompute-assignments response holding the given assignments.
 */
fun precomputedAssignmentsBody(assignments: Map<String, FakeAssignment>): String {
    val flags = JSONObject()
    assignments.forEach { (key, assignment) ->
        flags.put(
            key,
            JSONObject()
                .put("variationType", assignment.variationType)
                .put("variationValue", assignment.variationValue)
                .put("variationKey", assignment.variationKey)
                .put("allocationKey", assignment.allocationKey)
                .put("doLog", assignment.doLog)
                .put("reason", assignment.reason)
                .put("extraLogging", JSONObject(assignment.extraLogging))
                .apply { assignment.serialId?.let { put("serialId", it) } }
        )
    }
    return JSONObject()
        .put(
            "data",
            JSONObject()
                .put("type", "precomputed-assignments")
                .put("attributes", JSONObject().put("flags", flags))
        )
        .toString()
}

/**
 * Builds an HTTP response for the given request.
 */
fun httpResponse(request: Request, code: Int = 200, body: String = ""): Response {
    return Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 200..299) "OK" else "Error")
        .body(body.toResponseBody("application/vnd.api+json".toMediaType()))
        .build()
}

/**
 * Reads the body of this request as a JSON object.
 */
fun Request.bodyAsJson(): JSONObject {
    val buffer = Buffer()
    checkNotNull(body).writeTo(buffer)
    return JSONObject(buffer.readUtf8())
}

/**
 * Reads the targeting key sent in a precompute-assignments request.
 */
fun Request.targetingKey(): String {
    return bodyAsJson()
        .getJSONObject("data")
        .getJSONObject("attributes")
        .getJSONObject("subject")
        .getString("targeting_key")
}
