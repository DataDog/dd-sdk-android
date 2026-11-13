/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

internal class ResponseSigningVerifierTest {
    @TestFactory
    fun `M select staging trust only for exact HTTPS origin W forEndpoint()`(): List<DynamicTest> {
        val cases = mapOf(
            "https://preview.ff-cdn.datad0g.com/precompute-assignments" to true,
            "https://PREVIEW.FF-CDN.DATAD0G.COM:443/precompute-assignments?dd_env=production" to true,
            "https://preview.ff-cdn.datadoghq.com/precompute-assignments?dd_env=staging" to false,
            "https://preview.ff-cdn.us3.datadoghq.com/precompute-assignments" to false,
            "https://preview.ff-cdn.datad0g.com.attacker.test/precompute-assignments" to false,
            "https://attacker-preview.ff-cdn.datad0g.com/precompute-assignments" to false,
            "https://preview.ff-cdn.datad0g.com:444/precompute-assignments" to false,
            "http://preview.ff-cdn.datad0g.com/precompute-assignments" to false,
            "https://user@preview.ff-cdn.datad0g.com/precompute-assignments" to false
        )
        return cases.map { (endpoint, expected) ->
            DynamicTest.dynamicTest(endpoint) {
                // When
                val roots = ResponseSigningTrustRoots.forEndpoint(endpoint.toHttpUrl(), emptyList())
                // Then
                assertThat(roots).hasSize(if (expected) 1 else 0)
                if (expected) {
                    assertThat(checkNotNull(roots.single().decodeBase64()).sha256().hex())
                        .isEqualTo("6e83e91905e966b37a4eaf30961143cba6027ead0affc207ae6edc012390171e")
                }
            }
        }
    }

    @Test
    fun `M replace bundled staging trust W forEndpoint() {explicit roots}`() {
        // Given
        val roots = listOf(resource("corpus.json")["anchor_der_base64"].asString)
        // When
        val result = ResponseSigningTrustRoots.forEndpoint(
            "https://preview.ff-cdn.datad0g.com/precompute-assignments".toHttpUrl(),
            roots
        )
        // Then
        assertThat(result).isEqualTo(roots)
    }

    @TestFactory
    fun `M match every central manifest vector W verify()`(): List<DynamicTest> {
        // Given
        val manifest = resource("manifest.json")
        check(manifest["schema_version"].asInt == 1)
        val suites = manifest.getAsJsonObject("files")
        check(suites.size() > 0)
        return suites.keySet().sorted().flatMap { name ->
            val metadata = suites.getAsJsonObject(name)
            val raw = resourceBytes("response-signing-v1/$name")
            check(raw.toByteString().sha256().hex() == metadata["sha256"].asString)
            val corpus = JsonParser.parseString(raw.toString(Charsets.UTF_8)).asJsonObject
            val cases = corpus.getAsJsonArray("cases")
            check(cases.size() == metadata["response_cases"].asInt)
            check(cases.count { it.asJsonObject["expected_accept"].asBoolean } == metadata["accepted"].asInt)
            cases.map { entry ->
                val vector = entry.asJsonObject
                DynamicTest.dynamicTest("$name: ${vector["id"].asString}") {
                    val testedVerifier = ResponseSigningVerifier(listOf(corpus["anchor_der_base64"].asString))
                    // When
                    val result = verify(testedVerifier, vector)
                    // Then
                    assertThat(result != null).isEqualTo(vector["expected_accept"].asBoolean)
                    if (vector["expected_accept"].asBoolean) {
                        val response = vector.getAsJsonObject("response")
                        val transcript = ResponseSigningVerifier.transcript(
                            request(vector),
                            response["status"].asInt,
                            headers(response.getAsJsonArray("headers")),
                            bytes(response["body_base64"].asString)
                        )
                        assertThat(transcript.toByteString().base64()).isEqualTo(vector["transcript_base64"].asString)
                    }
                }
            }
        }
    }

    @TestFactory
    fun `M reject nonminimal named bits W verify()`(): List<DynamicTest> {
        val corpus = JsonParser.parseString(
            resourceBytes("response-signing-key-usage.json").toString(Charsets.UTF_8)
        ).asJsonObject
        return corpus.getAsJsonArray("cases").map { entry ->
            val vector = entry.asJsonObject
            DynamicTest.dynamicTest(vector["id"].asString) {
                // Given
                val testedVerifier = ResponseSigningVerifier(listOf(corpus["anchor"].asString))
                // When
                val result = verify(testedVerifier, vector)
                // Then
                assertThat(result != null).isEqualTo(vector["expected_accept"].asBoolean)
            }
        }
    }

    private fun request(vector: JsonObject): Request {
        val input = vector.getAsJsonObject("request")
        return Request.Builder().url(input["url"].asString)
            .headers(headers(input.getAsJsonArray("headers")))
            .method(input["method"].asString, bytes(input["body_base64"].asString).toRequestBody())
            .build()
    }

    private fun verify(verifier: ResponseSigningVerifier, vector: JsonObject): VerifiedResponse? {
        val response = vector.getAsJsonObject("response")
        return verifier.verify(
            request(vector),
            response["status"].asInt,
            headers(response.getAsJsonArray("headers")),
            bytes(response["body_base64"].asString),
            vector["now"].asLong
        )
    }

    private fun headers(values: JsonArray): Headers = Headers.Builder().apply {
        values.forEach { add(it.asJsonArray[0].asString, it.asJsonArray[1].asString) }
    }.build()

    private fun bytes(value: String): ByteArray = checkNotNull(value.decodeBase64()).toByteArray()

    private fun resource(name: String): JsonObject =
        JsonParser.parseString(resourceBytes("response-signing-v1/$name").toString(Charsets.UTF_8)).asJsonObject

    private fun resourceBytes(path: String): ByteArray = checkNotNull(javaClass.classLoader)
        .getResourceAsStream(path).let { checkNotNull(it) }.use { it.readBytes() }
}
