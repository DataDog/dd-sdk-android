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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class ResponseSigningVerifierTest {
    @TestFactory
    fun `M match frozen independent vectors W verify()`(): List<DynamicTest> {
        val corpus = resource("corpus.json")
        val cases = corpus.getAsJsonArray("cases")
        check(cases.size() == 43)
        check(cases.count { it.asJsonObject["expected_accept"].asBoolean } == 6)
        return cases.map { entry ->
            val vector = entry.asJsonObject
            DynamicTest.dynamicTest(vector["id"].asString) {
                // Given
                val testedVerifier = ResponseSigningVerifier(listOf(corpus["anchor_der_base64"].asString))
                // When
                val result = verify(testedVerifier, vector)
                // Then
                assertThat(result != null).isEqualTo(vector["expected_accept"].asBoolean)
            }
        }
    }

    @TestFactory
    fun `M reject nonminimal named bits W verify()`(): List<DynamicTest> {
        val corpus = resource("key-usage-supplement.json")
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

    private fun verify(verifier: ResponseSigningVerifier, vector: JsonObject): VerifiedResponse? {
        val input = vector.getAsJsonObject("request")
        val request = Request.Builder().url(input["url"].asString)
            .headers(headers(input.getAsJsonArray("headers")))
            .method(input["method"].asString, bytes(input["body_base64"].asString).toRequestBody())
            .build()
        val response = vector.getAsJsonObject("response")
        return verifier.verify(
            request,
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

    private fun resource(name: String): JsonObject = checkNotNull(javaClass.classLoader)
        .getResourceAsStream("response-signing-v3/$name").let { checkNotNull(it) }
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
}
