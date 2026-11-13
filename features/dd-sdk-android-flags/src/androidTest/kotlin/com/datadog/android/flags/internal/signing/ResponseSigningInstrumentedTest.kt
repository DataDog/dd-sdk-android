/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise the shared contract with Android's actual EC key and signature providers. */
internal class ResponseSigningInstrumentedTest {
    @Test
    fun mustMatchCentralManifest_whenVerifyingOnAndroid() {
        // Given
        val manifest = json(asset("response-signing-v1/manifest.json"))
        assertEquals(1, manifest["schema_version"].asInt)
        val files = manifest.getAsJsonObject("files")
        assertTrue(files.size() > 0)
        files.keySet().sorted().forEach { name ->
            val metadata = files.getAsJsonObject(name)
            val raw = asset("response-signing-v1/$name")
            assertEquals(name, metadata["sha256"].asString, raw.toByteString().sha256().hex())
            val corpus = json(raw)
            val cases = corpus.getAsJsonArray("cases")
            assertEquals(name, metadata["response_cases"].asInt, cases.size())
            assertEquals(name, metadata["accepted"].asInt, cases.count { it.asJsonObject["expected_accept"].asBoolean })
            assertCorpus(corpus)
        }
    }

    @Test
    fun mustRejectNonminimalKeyUsage_whenVerifyingOnAndroid() {
        assertCorpus(json(asset("response-signing-key-usage.json")))
    }

    private fun assertCorpus(corpus: JsonObject) {
        val root = (corpus["anchor_der_base64"] ?: corpus["anchor"]).asString
        val verifier = ResponseSigningVerifier(listOf(root))
        corpus.getAsJsonArray("cases").forEach { entry ->
            // Given
            val vector = entry.asJsonObject
            val input = vector.getAsJsonObject("request")
            val output = vector.getAsJsonObject("response")
            val request = Request.Builder().url(input["url"].asString)
                .headers(headers(input.getAsJsonArray("headers")))
                .method(input["method"].asString, bytes(input["body_base64"].asString).toRequestBody()).build()
            val responseHeaders = headers(output.getAsJsonArray("headers"))
            val body = bytes(output["body_base64"].asString)
            // When
            val proof = verifier.verify(request, output["status"].asInt, responseHeaders, body, vector["now"].asLong)
            // Then
            assertEquals(vector["id"].asString, vector["expected_accept"].asBoolean, proof != null)
            if (vector["expected_accept"].asBoolean) {
                val transcript = ResponseSigningVerifier.transcript(
                    request,
                    output["status"].asInt,
                    responseHeaders,
                    body
                )
                assertEquals(
                    vector["id"].asString,
                    vector["transcript_base64"].asString,
                    transcript.toByteString().base64()
                )
            }
        }
    }

    private fun asset(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    private fun json(raw: ByteArray): JsonObject = JsonParser.parseString(raw.toString(Charsets.UTF_8)).asJsonObject

    private fun bytes(value: String): ByteArray = checkNotNull(value.decodeBase64()).toByteArray()

    private fun headers(values: JsonArray): Headers = Headers.Builder().apply {
        values.forEach { add(it.asJsonArray[0].asString, it.asJsonArray[1].asString) }
    }.build()
}
