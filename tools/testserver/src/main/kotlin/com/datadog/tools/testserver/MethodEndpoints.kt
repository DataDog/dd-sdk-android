/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.testserver

import io.ktor.http.HttpHeaders
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

internal fun Route.configureMethodEndpoints() {
    configureMethodEndpointsWithBody()
    configureMethodEndpointsWithoutBody()
}

private fun Route.configureMethodEndpointsWithBody() {
    get("/get") {
        val response = MethodResponse(
            method = "GET",
            path = "/get",
            headers = call.request.headers.entries()
                .associate { it.key to it.value.joinToString(", ") }
        )
        call.respond(response)
    }

    post("/post") {
        val body = call.receiveText()
        val response = MethodResponse(
            method = "POST",
            path = "/post",
            body = body,
            headers = call.request.headers.entries()
                .associate { it.key to it.value.joinToString(", ") }
        )
        call.respond(response)
    }

    put("/put") {
        val body = call.receiveText()
        val response = MethodResponse(
            method = "PUT",
            path = "/put",
            body = body,
            headers = call.request.headers.entries()
                .associate { it.key to it.value.joinToString(", ") }
        )
        call.respond(response)
    }

    delete("/delete") {
        val response = MethodResponse(
            method = "DELETE",
            path = "/delete",
            headers = call.request.headers.entries()
                .associate { it.key to it.value.joinToString(", ") }
        )
        call.respond(response)
    }

    patch("/patch") {
        val body = call.receiveText()
        val response = MethodResponse(
            method = "PATCH",
            path = "/patch",
            body = body,
            headers = call.request.headers.entries()
                .associate { it.key to it.value.joinToString(", ") }
        )
        call.respond(response)
    }
}

private fun Route.configureMethodEndpointsWithoutBody() {
    head("/head") {
        call.response.header(HttpHeaders.ContentType, "application/json")
        call.response.header("X-Custom-Header", "head-response")
        call.respond("")
    }

    options("/options") {
        call.response.header(HttpHeaders.Allow, "GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS")
        call.respond("")
    }
}
