/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Serves the bundled snapshot on loopback. Entity fields and upstream URLs remain unchanged.
 * Call [start] off the main thread; it blocks until the ephemeral port is bound.
 * Missing or invalid JSON snapshots return 500, while absent avatars return 404.
 */
internal class RickAndMortyLocalServer(
    private val snapshotSource: RickAndMortySnapshotSource,
    private val apiDelayMs: Long = 100,
    private val avatarDelayMs: Long = 200
) {
    private val snapshots = Resource.entries.associateWith { resource ->
        lazy { readSnapshot(resource.fileName) }
    }
    private var runningServer: RunningServer? = null

    /** Starts once, including concurrent callers, and returns the bound port. */
    @Synchronized
    fun start(): Int {
        runningServer?.let { return it.port }
        val engine = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                route("/{path...}") {
                    handle {
                        serve(call, call.parameters.getAll("path").orEmpty())
                    }
                }
            }
        }
        try {
            engine.start(wait = false)
            val port = runBlocking { engine.resolvedConnectors().first().port }
            runningServer = RunningServer(engine, port)
            return port
        } finally {
            if (runningServer == null) {
                engine.stop(0, 1_000)
            }
        }
    }

    @Synchronized
    fun stop() {
        runningServer?.engine?.stop(0, 1_000)
        runningServer = null
    }

    private suspend fun serve(call: ApplicationCall, path: List<String>) {
        val isAvatar = path.size == 4 && path.take(3) == listOf("api", "character", "avatar")
        delay(if (isAvatar) avatarDelayMs else apiDelayMs)
        try {
            when {
                call.request.local.method != HttpMethod.Get -> call.respondError(HttpStatusCode.NotFound)
                isAvatar -> serveAvatar(call, path.last())
                path.size in 2..3 && path.first() == "api" -> {
                    val resource = Resource.entries.firstOrNull { it.path == path[1] }
                    if (resource == null) {
                        call.respondError(HttpStatusCode.NotFound)
                    } else {
                        serveResource(call, resource, path.getOrNull(2))
                    }
                }
                else -> call.respondError(HttpStatusCode.NotFound)
            }
        } catch (_: IOException) {
            call.respondError(HttpStatusCode.InternalServerError)
        }
    }

    private suspend fun serveAvatar(call: ApplicationCall, name: String) {
        val id = name.removeSuffix(".jpeg").toIntOrNull()
        val bytes = if (name.endsWith(".jpeg") && id != null && id > 0) {
            snapshotSource.open("avatars/$id.jpeg")?.use { it.readBytes() }
        } else {
            null
        }
        if (bytes == null) {
            call.respondError(HttpStatusCode.NotFound)
        } else {
            call.respondBytes(bytes, ContentType.Image.JPEG)
        }
    }

    private suspend fun serveResource(call: ApplicationCall, resource: Resource, ids: String?) {
        val snapshot = snapshots.getValue(resource).value
        if (snapshot == null) {
            call.respondError(HttpStatusCode.InternalServerError)
        } else if (ids == null) {
            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val pages = (snapshot.entities.size + PAGE_SIZE - 1) / PAGE_SIZE
            if (page !in 1..pages) {
                call.respondError(HttpStatusCode.NotFound)
                return
            }
            val response = buildJsonObject {
                put(
                    "info",
                    buildJsonObject {
                        put("count", snapshot.entities.size)
                        put("pages", pages)
                        put("next", pageLink(resource, page + 1, pages))
                        put("prev", pageLink(resource, page - 1, pages))
                    }
                )
                put("results", JsonArray(snapshot.entities.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)))
            }
            call.respondText(response.toString(), JSON_CONTENT_TYPE)
        } else if (',' in ids || (ids.startsWith('[') && ids.endsWith(']'))) {
            val entities = ids.removeSurrounding("[", "]").split(',').mapNotNull {
                snapshot.byId[it.trim().toIntOrNull()]
            }
            call.respondText(JsonArray(entities).toString(), JSON_CONTENT_TYPE)
        } else {
            val entity = snapshot.byId[ids.toIntOrNull()]
            if (entity == null) {
                call.respondError(HttpStatusCode.NotFound, "${resource.displayName} not found")
            } else {
                call.respondText(entity.toString(), JSON_CONTENT_TYPE)
            }
        }
    }

    private fun readSnapshot(fileName: String): Snapshot? = try {
        snapshotSource.open(fileName)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val entities = Json.parseToJsonElement(reader.readText()).jsonArray.map { it.jsonObject }
            Snapshot(entities, entities.associateBy { it.getValue("id").jsonPrimitive.int })
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: NoSuchElementException) {
        null
    }

    private fun pageLink(resource: Resource, page: Int, pages: Int) = if (page in 1..pages) {
        JsonPrimitive("https://rickandmortyapi.com/api/${resource.path}?page=$page")
    } else {
        JsonNull
    }

    private suspend fun ApplicationCall.respondError(
        status: HttpStatusCode,
        message: String = "There is nothing here"
    ) {
        val body = buildJsonObject { put("error", message) }
        respondText(body.toString(), JSON_CONTENT_TYPE, status)
    }

    private class Snapshot(val entities: List<JsonObject>, val byId: Map<Int, JsonObject>)

    private class RunningServer(val engine: ApplicationEngine, val port: Int)

    private enum class Resource(val path: String, val fileName: String, val displayName: String) {
        CHARACTER("character", "characters.json", "Character"),
        LOCATION("location", "locations.json", "Location"),
        EPISODE("episode", "episodes.json", "Episode")
    }

    private companion object {
        const val PAGE_SIZE = 20
        val JSON_CONTENT_TYPE = ContentType.Application.Json.withParameter("charset", "utf-8")
    }
}
