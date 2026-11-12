/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class RickAndMortyLocalServerTest {
    private val fakeCharacters = forgeEntities("character", 45)
    private val fakeLocations = forgeEntities("location", 3)
    private val fakeEpisodes = forgeEntities("episode", 2)
    private val fakeAvatar = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 0xff.toByte(), 0xd9.toByte())
    private val fakeFiles = mutableMapOf(
        "characters.json" to JsonArray(fakeCharacters).toString().toByteArray(),
        "locations.json" to JsonArray(fakeLocations).toString().toByteArray(),
        "episodes.json" to JsonArray(fakeEpisodes).toString().toByteArray(),
        "avatars/1.jpeg" to fakeAvatar
    )
    private val snapshotReads = AtomicInteger()
    private val stubSnapshotSource = RickAndMortySnapshotSource { path ->
        if (path.endsWith(".json")) snapshotReads.incrementAndGet()
        fakeFiles[path]?.inputStream()
    }
    private var testedServer = RickAndMortyLocalServer(stubSnapshotSource, 0, 0)
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

    @AfterEach
    fun `tear down`() {
        testedServer.stop()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
    }

    @ParameterizedTest
    @CsvSource("1,1,20,2,0", "2,21,40,3,1", "3,41,45,0,2")
    fun `M return page and upstream links W get() {first middle last}`(
        page: Int,
        firstId: Int,
        lastId: Int,
        nextPage: Int,
        previousPage: Int
    ) {
        // When
        get("/api/character?page=$page").use { response ->
            // Then
            assertThat(response.code).isEqualTo(200)
            assertThat(response.header("Content-Type")).isEqualTo("application/json; charset=utf-8")
            val body = Json.parseToJsonElement(checkNotNull(response.body).string()).jsonObject
            val info = body.getValue("info").jsonObject
            assertThat(info.getValue("count").jsonPrimitive.int).isEqualTo(45)
            assertThat(info.getValue("pages").jsonPrimitive.int).isEqualTo(3)
            assertThat(info.getValue("next")).isEqualTo(pageLink(nextPage))
            assertThat(info.getValue("prev")).isEqualTo(pageLink(previousPage))
            assertThat(body.getValue("results").jsonArray).isEqualTo(fakeCharacters.subList(firstId - 1, lastId))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "?page=", "?page=invalid", "?page=0", "?page=-1", "?page=999999999999999999999"])
    fun `M default to first page W get() {missing or invalid page}`(query: String) {
        // When
        get("/api/character$query").use { response ->
            // Then
            val body = Json.parseToJsonElement(checkNotNull(response.body).string()).jsonObject
            assertThat(response.code).isEqualTo(200)
            assertThat(body.getValue("results").jsonArray).isEqualTo(fakeCharacters.take(20))
        }
    }

    @ParameterizedTest
    @CsvSource("character,45", "location,3", "episode,2")
    fun `M serve each snapshot W get()`(resource: String, count: Int) {
        // When
        get("/api/$resource").use { response ->
            // Then
            val body = Json.parseToJsonElement(checkNotNull(response.body).string()).jsonObject
            assertThat(response.code).isEqualTo(200)
            assertThat(body.getValue("info").jsonObject.getValue("count").jsonPrimitive.int).isEqualTo(count)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["4", "2147483647"])
    fun `M return not found W get() {page out of range}`(page: String) {
        // When
        get("/api/character?page=$page").use { response ->
            // Then
            assertThat(response.code).isEqualTo(404)
            assertThat(checkNotNull(response.body).string()).isEqualTo("{\"error\":\"There is nothing here\"}")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["character", "location", "episode"])
    fun `M preserve raw entity fields W get() {single id}`(resource: String) {
        // When
        get("/api/$resource/2").use { response ->
            // Then
            assertThat(response.code).isEqualTo(200)
            val body = Json.parseToJsonElement(checkNotNull(response.body).string())
            assertThat(body).isEqualTo(forgeEntities(resource, 2).last())
        }
    }

    @ParameterizedTest
    @CsvSource("character,Character", "location,Location", "episode,Episode")
    fun `M return resource specific error W get() {unknown id}`(resource: String, displayName: String) {
        // When
        get("/api/$resource/999").use { response ->
            // Then
            assertThat(response.code).isEqualTo(404)
            assertThat(checkNotNull(response.body).string()).isEqualTo("{\"error\":\"$displayName not found\"}")
        }
    }

    @ParameterizedTest
    @CsvSource(
        "character/3,999,1,3|3,1,3",
        "location/[3,999,1]|3,1",
        "episode/[2,1]|2,1",
        "character/[5]|5",
        "character/[999]|",
        "character/999,998|",
        delimiter = '|',
        nullValues = [""]
    )
    fun `M return ordered array and omit unknown ids W get() {batch}`(path: String, expectedIds: String?) {
        // When
        get("/api/$path").use { response ->
            // Then
            assertThat(response.code).isEqualTo(200)
            val entities = Json.parseToJsonElement(checkNotNull(response.body).string()).jsonArray
            assertThat(entities.map { it.jsonObject.getValue("id").jsonPrimitive.int })
                .isEqualTo(expectedIds?.split(',')?.map { it.toInt() }.orEmpty())
        }
    }

    @Test
    fun `M return avatar bytes W get()`() {
        // When
        get("/api/character/avatar/1.jpeg").use { response ->
            // Then
            assertThat(response.code).isEqualTo(200)
            assertThat(response.header("Content-Type")).isEqualTo("image/jpeg")
            assertThat(checkNotNull(response.body).bytes()).isEqualTo(fakeAvatar)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/character/avatar/999.jpeg", "/api/character/avatar/1.png",
            "/unknown", "/api/unknown", "/api/character/1/extra"
        ]
    )
    fun `M return not found W get() {unknown avatar or route}`(path: String) {
        // When
        get(path).use { response ->
            // Then
            assertThat(response.code).isEqualTo(404)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "not json", "{}", "[{}]", "[{\"id\":\"invalid\"}]"])
    fun `M return server error W get() {unavailable snapshot}`(contents: String) {
        // Given
        fakeFiles.remove("characters.json")
        if (contents != "missing") fakeFiles["characters.json"] = contents.toByteArray()

        // When
        get("/api/character").use { response ->
            // Then
            assertThat(response.code).isEqualTo(500)
        }
    }

    @Test
    fun `M return server error W get() {snapshot read fails}`() {
        // Given
        testedServer = RickAndMortyLocalServer({ throw IOException("Fixture failure") }, 0, 0)

        // When
        get("/api/character/avatar/1.jpeg").use { response ->
            // Then
            assertThat(response.code).isEqualTo(500)
        }
    }

    @Test
    fun `M read snapshots lazily once W start() and get()`() {
        // When
        testedServer.start()

        // Then
        assertThat(snapshotReads.get()).isZero()

        // When
        get("/api/character/1").close()
        get("/api/character/2").close()

        // Then
        assertThat(snapshotReads.get()).isEqualTo(1)
    }

    @Test
    fun `M return same port W start() twice`() {
        // When
        val firstPort = testedServer.start()
        val secondPort = testedServer.start()

        // Then
        assertThat(firstPort).isBetween(1, 65535)
        assertThat(secondPort).isEqualTo(firstPort)
    }

    @Test
    fun `M return same bound port W start() {concurrent callers}`() {
        // Given
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val release = CountDownLatch(1)
        try {
            val ports = (1..8).map {
                executor.submit<Int> {
                    ready.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    testedServer.start()
                }
            }

            // When
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
            release.countDown()
            val results = ports.map { it.get(10, TimeUnit.SECONDS) }

            // Then
            assertThat(results.distinct()).hasSize(1)
            assertThat(results.first()).isBetween(1, 65535)
            get("/api/character/1").use { assertThat(it.code).isEqualTo(200) }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun get(path: String): Response {
        val port = testedServer.start()
        return client.newCall(Request.Builder().url("http://127.0.0.1:$port$path").build()).execute()
    }

    private fun forgeEntities(resource: String, count: Int): List<JsonObject> = (1..count).map { id ->
        buildJsonObject {
            put("id", id)
            put("name", "$resource $id")
            put("url", "https://rickandmortyapi.com/api/$resource/$id")
            put("image", "https://rickandmortyapi.com/api/character/avatar/$id.jpeg")
            put("nested", buildJsonObject { put("unchanged", true) })
        }
    }

    private fun pageLink(page: Int) = if (page == 0) {
        JsonNull
    } else {
        Json.parseToJsonElement("\"https://rickandmortyapi.com/api/character?page=$page\"")
    }
}
