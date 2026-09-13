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
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment signature is invalid")
    }

    @Test
    fun `M accept unsigned payload W verify { protected mode was not requested }`() {
        val request = Request.Builder()
            .url("https://example.test/precompute-assignments")
            .post(REQUEST_BODY.toRequestBody())
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        assertThatCode { verifier.verify(request, response, RESPONSE_BODY.toByteArray()) }
            .doesNotThrowAnyException()
    }

    private fun request(): Request = Request.Builder()
        .url("https://example.test/precompute-assignments")
        .header("dd-client-token", "client-token")
        .header("content-type", "application/vnd.api+json")
        .header("Authorization", "Bearer $COMPACT_JWT")
        .header("x-dd-ffe-signature-version", "2")
        .header("x-dd-ffe-request-nonce", "000102030405060708090a0b0c0d0e0f")
        .post(REQUEST_BODY.toRequestBody())
        .build()

    private fun response(
        certificateId: String = LEAF_CERTIFICATE_ID,
        certificate: String = LEAF_CERTIFICATE,
        signature: String = SIGNATURE
    ): Response = Response.Builder()
        .request(request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header("x-dd-ffe-signature-version", "2")
        .header("x-dd-ffe-authorization-policy-version", "ap_test_v2")
        .header("x-dd-ffe-issued-at", ISSUED_AT.toString())
        .header("x-dd-ffe-expires-at", "1789097100")
        .header("x-dd-ffe-certificate-id", certificateId)
        .header("x-dd-ffe-signing-certificate", certificate)
        .header("x-dd-ffe-signature", signature)
        .build()

    private companion object {
        const val ISSUED_AT = 1_789_096_800L
        const val REQUEST_BODY = "{\"data\":{\"attributes\":{\"subject\":{\"targeting_key\":\"user-1\"}}}}"
        const val RESPONSE_BODY = "{\"data\":{\"id\":\"user-1\"}}"
        const val COMPACT_JWT =
            "eyJhbGciOiJFUzI1NiIsImtpZCI6ImN1c3RvbWVyLTIwMjYtMDkiLCJ0eXAiOiJkYXRhZG9nLWZmZS1hY2Nlc3Mrand0In0." +
                "eyJzdWIiOiJ1c2VyLTEifQ.signature"
        const val LEAF_CERTIFICATE_ID =
            "a0d868097393828703e0f9864e355a79b744df556d151249ce947157f08a0ff7"
        const val LEAF_CERTIFICATE =
            "MIIBszCCAVqgAwIBAgIBZTAKBggqhkjOPQQDAjAzMTEwLwYDVQQDDChEYXRhZG9nIEZGRSBFZGdlIEFz" +
                "c2lnbm1lbnRzIFBPQyBSb290IEsxMB4XDTI2MDkxMTAzMTgzMFoXDTI2MTIxMDAzMTgzMFowMjEwMC4G" +
                "A1UEAwwnRGF0YWRvZyBGRkUgRWRnZSBBc3NpZ25tZW50cyBQT0MgTGVhZiBBMFkwEwYHKoZIzj0CAQYI" +
                "KoZIzj0DAQcDQgAEOAdt79MNq0/K82oozH9BifTCyu5pogz9VnCf69v6m5rIERSTO27L2SizPPI3ptfM" +
                "BDa+bT/0wMdPc6GQYG4QeaNgMF4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0OBBYE" +
                "FPjCuJhWtNy/Ne+/6ZCIB8cmhFuJMB8GA1UdIwQYMBaAFHGtfswbRCOOchC6yZHakhktSdq4MAoGCCqG" +
                "SM49BAMCA0cAMEQCIFvqYcK+OAaBdMRuMkSpOVscR1SMdCPt5LNdkQEZyvNCAiBq2rtg8F27nZ2mHyoA" +
                "L4OT5tCMBKvYytpnRZzwMBYJTQ=="
        const val SIGNATURE =
            "MEUCIQC1Y+HApWCEfejMtZuWrq5Z7zXHoCkeheykCh0i4AmK6gIgXtiexYZXd2mC0r66E0lNOX41Cr4jcx8pm8yrrvbQ754="
    }
}
