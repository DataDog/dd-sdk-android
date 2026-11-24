/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.signing

import com.datadog.android.DatadogSite
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningMode
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal class ResponseSigningVerifierTest {
    @TestFactory
    fun `M allow signing only on the staging site W isAvailable()`(): List<DynamicTest> =
        DatadogSite.values().map { site ->
            DynamicTest.dynamicTest(site.name) {
                // When
                val result = ResponseSigningTrustRoots.isAvailable(site)
                // Then
                assertThat(result).isEqualTo(site == DatadogSite.STAGING)
            }
        } + DynamicTest.dynamicTest("unknown site") {
            assertThat(ResponseSigningTrustRoots.isAvailable(null)).isFalse()
        }

    @Test
    fun `M pin only the staging root W anchors {production configuration}`() {
        // When
        val anchors = ResponseSigningConfiguration(ResponseSigningMode.REQUIRED).anchors
        // Then
        assertThat(anchors).containsExactly(ResponseSigningTrustRoots.STAGING_ROOT)
        val der = checkNotNull(anchors.single().decodeBase64())
        assertThat(der.sha256().hex()).isEqualTo(STAGING_ROOT_SHA256)
        assertThat(der.base64()).isEqualTo(anchors.single())
    }

    @Test
    fun `M issue the staging DC intermediate W STAGING_ROOT`() {
        // Given
        val factory = CertificateFactory.getInstance("X.509")
        val root = factory.generateCertificate(
            checkNotNull(ResponseSigningTrustRoots.STAGING_ROOT.decodeBase64()).toByteArray().inputStream()
        ) as X509Certificate
        val intermediate = factory.generateCertificate(
            checkNotNull(javaClass.getResourceAsStream("/response-signing-chain/staging-us1-dc-intermediate.pem"))
        ) as X509Certificate
        // When
        intermediate.verify(root.publicKey)
        // Then
        assertThat(intermediate.issuerX500Principal).isEqualTo(root.subjectX500Principal)
        assertThat(root.subjectX500Principal.name).isEqualTo("CN=Datadog Customer Signing Root CA - staging")
        assertThat(root.basicConstraints).isEqualTo(2)
    }

    @Test
    fun `M keep test roots out of production W anchors {test override}`() {
        // Given
        val roots = mutableListOf(resource("corpus.json")["anchor_der_base64"].asString)
        // When
        val anchors = ResponseSigningConfiguration(ResponseSigningMode.REQUIRED, roots.toList()).anchors
        roots.clear()
        // Then
        assertThat(anchors).hasSize(1).doesNotContain(ResponseSigningTrustRoots.STAGING_ROOT)
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
                    // A case anchor replaces the file anchor (root-profile cases).
                    val anchor = (vector["anchor_der_base64"] ?: corpus["anchor_der_base64"]).asString
                    val testedVerifier = ResponseSigningVerifier(listOf(anchor))
                    // When
                    val result = verify(testedVerifier, vector)
                    // Then
                    assertThat(result.proof != null).isEqualTo(vector["expected_accept"].asBoolean)
                    if (!vector["expected_accept"].asBoolean) {
                        // Every rejected vector names the shared cross-SDK reason code.
                        assertThat(result.failure?.code).isEqualTo(vector["sdk_reason"].asString)
                    }
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
    fun `M decode certificate validity W signingTime()`(): List<DynamicTest> = listOf(
        Triple(23, "500101000000Z", -631152000L),
        Triple(23, "700101000000Z", 0L),
        Triple(23, "490101000000Z", 2493072000L),
        Triple(24, "20500101000000Z", 2524608000L)
    ).map { (tag, value, expected) ->
        DynamicTest.dynamicTest(value) {
            // Given
            val der = byteArrayOf(tag.toByte(), value.length.toByte()) + value.toByteArray(Charsets.US_ASCII)
            // When
            val actual = signingTime(SigningDer.read(der))
            // Then
            assertThat(actual).isEqualTo(expected)
        }
    }

    @TestFactory
    fun `M reject invalid certificate validity W signingTime()`(): List<DynamicTest> = listOf(
        "20260229000000Z",
        "20261301000000Z",
        "20260101240000Z",
        "20260101000060Z",
        "00000101000000Z"
    ).map { value ->
        DynamicTest.dynamicTest(value) {
            // Given
            val der = byteArrayOf(24, value.length.toByte()) + value.toByteArray(Charsets.US_ASCII)
            // When + Then
            assertThatThrownBy { signingTime(SigningDer.read(der)) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    private fun request(vector: JsonObject): Request {
        val input = vector.getAsJsonObject("request")
        return Request.Builder().url(input["url"].asString)
            .headers(headers(input.getAsJsonArray("headers")))
            .method(input["method"].asString, bytes(input["body_base64"].asString).toRequestBody())
            .build()
    }

    private fun verify(verifier: ResponseSigningVerifier, vector: JsonObject): ResponseSigningVerifier.Verification {
        val response = vector.getAsJsonObject("response")
        return verifier.verification(
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

    private companion object {
        const val STAGING_ROOT_SHA256 = "0e469f3cecc9cd51fd64373879636540512d56e99eecd70361c78b98c47ea934"
    }
}
