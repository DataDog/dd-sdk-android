/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.server

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.concurrent.TimeUnit

class LocalServer {

    var engine: ApplicationEngine? = null

    fun start(redirection: String) {
        engine = embeddedServer(Netty, PORT) {
            install(ContentNegotiation) {
                gson {}
            }
            routing {
                get(PATH) {
                    call.respondRedirect(redirection, true)
                }
            }
        }
        engine?.start(wait = false)
    }

    fun stop() {
        engine?.stop(SHUTDOWN_MS, STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    fun getUrl(): String {
        return "http://$HOST:$PORT/"
    }

    companion object {
        const val HOST = "127.0.0.1"

        const val PORT = 8080
        const val PATH = "/"

        const val SHUTDOWN_MS = 500L
        const val STOP_TIMEOUT_MS = 1500L
    }
}
