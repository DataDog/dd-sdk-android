/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.fail
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
internal class LiveSignedAssignmentTest {

    @Test
    fun acceptsLiveStagingSignatureAndRejectsTampering() {
        val clientToken = InstrumentationRegistry.getArguments().getString("clientToken")
        val assignmentJwt = InstrumentationRegistry.getArguments().getString("assignmentJwt")
        assumeNotNull(clientToken)
        assumeNotNull(assignmentJwt)
        requireNotNull(clientToken)
        requireNotNull(assignmentJwt)

        val requestBody = """
            {"data":{"type":"precompute-assignments-request","attributes":{"env":{"dd_env":"staging"},"subject":{"targeting_key":"signed-assignment-poc","targeting_attributes":{"user_id":"signed-assignment-poc","country":"US"}}}}}
        """.trimIndent().toByteArray()
        val nonce = ByteArray(16).also(SecureRandom()::nextBytes).toHex()
        val request = Request.Builder()
            .url("https://preview.ff-cdn.datad0g.com/precompute-assignments")
            .header("Accept-Encoding", "identity")
            .header("Authorization", "Bearer $assignmentJwt")
            .header("dd-client-token", clientToken)
            .header(PrecomputedAssignmentsVerifier.SIGNATURE_VERSION_HEADER, "2")
            .header(PrecomputedAssignmentsVerifier.REQUEST_NONCE_HEADER, nonce)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            val responseBody = requireNotNull(response.body).bytes()
            check(response.code == 200) { "Staging returned HTTP ${response.code}" }
            val verifier = PrecomputedAssignmentsVerifier()
            verifier.verify(request, response, responseBody)

            val tamperedBody = responseBody.copyOf()
            tamperedBody[0] = (tamperedBody[0].toInt() xor 1).toByte()
            try {
                verifier.verify(request, response, tamperedBody)
                fail("The verifier accepted a modified response body")
            } catch (_: AssignmentPayloadVerificationException) {
                // Expected.
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
