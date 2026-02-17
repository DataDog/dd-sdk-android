/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.testserver

import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

internal fun Route.configureRedirectEndpoints() {
    get("/redirect/get") {
        call.respondRedirect("/get", permanent = false)
    }

    post("/redirect/post") {
        call.respondRedirect("/post", permanent = false)
    }

    put("/redirect/put") {
        call.respondRedirect("/put", permanent = false)
    }

    delete("/redirect/delete") {
        call.respondRedirect("/delete", permanent = false)
    }

    patch("/redirect/patch") {
        call.respondRedirect("/patch", permanent = false)
    }

    head("/redirect/head") {
        call.respondRedirect("/head", permanent = false)
    }

    options("/redirect/options") {
        call.respondRedirect("/options", permanent = false)
    }
}
