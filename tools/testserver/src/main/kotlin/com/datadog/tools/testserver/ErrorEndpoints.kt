/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.testserver

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

private const val DEFAULT_ERROR_CODE = 500

internal fun Route.configureErrorEndpoints() {
    configureErrorEndpointsWithBody()
    configureErrorEndpointsWithoutBody()
}

private fun Route.configureErrorEndpointsWithBody() {
    get("/error/{code}/get") {
        val code = call.parameters["code"]?.toIntOrNull() ?: DEFAULT_ERROR_CODE
        val statusCode = HttpStatusCode.fromValue(code)
        val response = ErrorResponse(
            statusCode = code,
            method = "GET",
            message = statusCode.description
        )
        call.respond(statusCode, response)
    }

    post("/error/{code}/post") {
        val code = call.parameters["code"]?.toIntOrNull() ?: DEFAULT_ERROR_CODE
        val statusCode = HttpStatusCode.fromValue(code)
        val response = ErrorResponse(
            statusCode = code,
            method = "POST",
            message = statusCode.description
        )
        call.respond(statusCode, response)
    }

    put("/error/{code}/put") {
        val code = call.parameters["code"]?.toIntOrNull() ?: DEFAULT_ERROR_CODE
        val statusCode = HttpStatusCode.fromValue(code)
        val response = ErrorResponse(
            statusCode = code,
            method = "PUT",
            message = statusCode.description
        )
        call.respond(statusCode, response)
    }

    delete("/error/{code}/delete") {
        val code = call.parameters["code"]?.toIntOrNull() ?: DEFAULT_ERROR_CODE
        val statusCode = HttpStatusCode.fromValue(code)
        val response = ErrorResponse(
            statusCode = code,
            method = "DELETE",
            message = statusCode.description
        )
        call.respond(statusCode, response)
    }

    patch("/error/{code}/patch") {
        val code = call.parameters["code"]?.toIntOrNull() ?: DEFAULT_ERROR_CODE
        val statusCode = HttpStatusCode.fromValue(code)
        val response = ErrorResponse(
            statusCode = code,
            method = "PATCH",
            message = statusCode.description
        )
        call.respond(statusCode, response)
    }
}

private fun Route.configureErrorEndpointsWithoutBody() {
    head("/error/{code}/head") {
        val code = call.parameters["code"]?.toIntOrNull() ?: DEFAULT_ERROR_CODE
        val statusCode = HttpStatusCode.fromValue(code)
        call.respond(statusCode, "")
    }

    options("/error/{code}/options") {
        val code = call.parameters["code"]?.toIntOrNull() ?: DEFAULT_ERROR_CODE
        val statusCode = HttpStatusCode.fromValue(code)
        call.respond(statusCode, "")
    }
}
