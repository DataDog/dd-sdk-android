/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class PrecomputedAssignmentsVerifierTest {

    private val verifier = PrecomputedAssignmentsVerifier(currentTimeSeconds = { ISSUED_AT })

    @Test
    fun `M accept payload W verify { origin signed exact response }`() {
        assertThatCode { verifier.verify(request(), response(), RESPONSE_BODY.toByteArray()) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `M reject payload W verify { response body was changed }`() {
        val tampered = RESPONSE_BODY.toByteArray().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThatThrownBy { verifier.verify(request(), response(), tampered) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Signed assignment signature is invalid")
    }

    private fun request(): Request = Request.Builder()
        .url("http://127.0.0.1:17676/precompute-assignments")
        .header("dd-client-token", "poc-client-token")
        .header("x-dd-ffe-request-nonce", "000102030405060708090a0b0c0d0e0f")
        .post(REQUEST_BODY.toRequestBody())
        .build()

    private fun response(): Response = Response.Builder()
        .request(request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header("x-dd-ffe-signature-version", "1")
        .header("x-dd-ffe-issued-at", ISSUED_AT.toString())
        .header("x-dd-ffe-expires-at", "1788926049")
        .header("x-dd-ffe-certificate-id", "d0745e67df306ef25a7a1b8d2f4e3dddace676bfc87da42f8604188fc71e5ec8")
        .header("x-dd-ffe-signing-certificate", LEAF_CERTIFICATE)
        .header("x-dd-ffe-signature", SIGNATURE)
        .build()

    private companion object {
        const val ISSUED_AT = 1_788_925_749L
        const val REQUEST_BODY =
            "{\"data\":{\"type\":\"precompute-assignments-request\",\"attributes\":{" +
                "\"env\":{\"dd_env\":\"test-env\"}," +
                "\"source\":{\"sdk_name\":\"poc-curl\",\"sdk_version\":\"1.0\"}," +
                "\"subject\":{\"targeting_key\":\"user123\"," +
                "\"targeting_attributes\":{\"country\":\"US\"}}}}}"
        const val RESPONSE_BODY =
            "{\"data\":{\"id\":\"user123\",\"type\":\"precomputed-assignments\"," +
                "\"attributes\":{\"obfuscated\":false," +
                "\"createdAt\":\"2026-09-09T03:49:09.973203Z\",\"format\":\"PRECOMPUTED\"," +
                "\"environment\":{\"name\":\"test-env\"},\"flags\":{\"country-message\":{" +
                "\"variationType\":\"string\",\"variationValue\":\"hello-us\",\"doLog\":false," +
                "\"allocationKey\":\"country-allocation\",\"variationKey\":\"us\"," +
                "\"reason\":\"TARGETING_MATCH\",\"serialId\":11," +
                "\"extraLogging\":{}}}}}}"
        const val LEAF_CERTIFICATE =
            "MIIBlzCCAT2gAwIBAgIBAjAKBggqhkjOPQQDAjAyMTAwLgYDVQQDEydEYXRhZG9nIEZGRSBTaWduZWQg" +
                "QXNzaWdubWVudHMgUE9DIFJvb3QwHhcNMjYwMTAxMDAwMDAwWhcNMzUwMTAxMDAwMDAwWjAyMTAw" +
                "LgYDVQQDEydEYXRhZG9nIEZGRSBTaWduZWQgQXNzaWdubWVudHMgUE9DIExlYWYwWTATBgcqhkjOPQIB" +
                "BggqhkjOPQMBBwNCAAQToOIWEw3kjXu2+fWY5Qzes0xvt/vWLq4m6L7utZOGoBcsqWvIhPdwv9ms2kd4" +
                "skGRmMAbTa2QhGOVNkJN6HQYo0QwQjAOBgNVHQ8BAf8EBAMCB4AwDwYDVR0lBAgwBgYEVR0lADAfBgNV" +
                "HSMEGDAWgBSGiz2ZJtQjcGZo/yO2jn3V/OFX4jAKBggqhkjOPQQDAgNIADBFAiBTCS85cf9drSucl7rl" +
                "qQqBY5ni3H4gs6ThLDc7dDBB9gIhAJL/8Sy2/VdqQCEP0np+nHY/UYAVZqreYhEse+PbtRnb"
        const val SIGNATURE =
            "MEUCIDL8Yuot2McXdFHtyEHmPCl+pXRKp8c7lgseHAGylIkyAiEA9yGxQoaJpM3ID79qrQ5urfL5sdR96tkm5IMpQ1LthmA="
    }
}
