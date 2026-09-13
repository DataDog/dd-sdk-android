/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.flags.AssignmentProtection
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class PrecomputedAssignmentsVerifierTest {

    @Test
    fun `M accept payload W verify { signed-only cross-platform vector }`() {
        val request = request(AssignmentProtection.SIGNED)
        val envelope = verifier(AssignmentProtection.SIGNED).verify(
            request,
            response(request, AssignmentProtection.SIGNED),
            RESPONSE_BODY_BYTES
        )

        assertThat(envelope).isEqualTo(
            ProtectedAssignmentEnvelope(
                protection = AssignmentProtection.SIGNED,
                requestNonce = NONCE,
                responseStatus = 200,
                authorizationPolicyVersion = null,
                rulesRevision = "",
                issuedAt = ISSUED_AT,
                expiresAt = EXPIRES_AT,
                certificateId = LEAF_CERTIFICATE_ID,
                certificate = LEAF_CERTIFICATE,
                signature = SIGNED_ONLY_SIGNATURE
            )
        )
    }

    @Test
    fun `M accept payload W verify { signed and authorized cross-platform vector }`() {
        val request = request(AssignmentProtection.SIGNED_AND_AUTHORIZED)
        val envelope = verifier(AssignmentProtection.SIGNED_AND_AUTHORIZED).verify(
            request,
            response(request, AssignmentProtection.SIGNED_AND_AUTHORIZED),
            RESPONSE_BODY_BYTES
        )

        assertThat(envelope?.authorizationPolicyVersion).isEqualTo(POLICY_VERSION)
        assertThat(envelope?.protection).isEqualTo(AssignmentProtection.SIGNED_AND_AUTHORIZED)
    }

    @Test
    fun `M reject payload W verify { response body was changed }`() {
        val request = request(AssignmentProtection.SIGNED_AND_AUTHORIZED)
        val tampered = RESPONSE_BODY_BYTES.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED_AND_AUTHORIZED).verify(
                request,
                response(request, AssignmentProtection.SIGNED_AND_AUTHORIZED),
                tampered
            )
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment signature is invalid")
    }

    @Test
    fun `M reject payload W verify { signed response was not requested }`() {
        val request = unsignedRequest()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(
                request,
                response(request, AssignmentProtection.SIGNED),
                RESPONSE_BODY_BYTES
            )
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessageContaining("request must contain one")
    }

    @Test
    fun `M reject payload W verify { signed-only request contains authorization }`() {
        val request = request(AssignmentProtection.SIGNED_AND_AUTHORIZED)

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(
                request,
                response(request, AssignmentProtection.SIGNED),
                RESPONSE_BODY_BYTES
            )
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed-only assignment request must not contain Authorization")
    }

    @Test
    fun `M reject payload W verify { authorized request has no authorization }`() {
        val request = request(AssignmentProtection.SIGNED)

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED_AND_AUTHORIZED).verify(
                request,
                response(request, AssignmentProtection.SIGNED_AND_AUTHORIZED),
                RESPONSE_BODY_BYTES
            )
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment request must contain one Authorization header")
    }

    @Test
    fun `M reject payload W verify { signed-only response contains authorization policy }`() {
        val request = request(AssignmentProtection.SIGNED)
        val response = response(request, AssignmentProtection.SIGNED)
            .newBuilder()
            .header(PrecomputedAssignmentsVerifier.AUTHORIZATION_POLICY_VERSION_HEADER, POLICY_VERSION)
            .build()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(request, response, RESPONSE_BODY_BYTES)
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed-only assignment response must not contain an authorization policy version")
    }

    @Test
    fun `M reject payload W verify { duplicate protected response header }`() {
        val request = request(AssignmentProtection.SIGNED)
        val response = response(request, AssignmentProtection.SIGNED)
            .newBuilder()
            .addHeader(PrecomputedAssignmentsVerifier.CERTIFICATE_ID_HEADER, LEAF_CERTIFICATE_ID)
            .build()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(request, response, RESPONSE_BODY_BYTES)
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessageContaining("must contain one")
    }

    @Test
    fun `M reject payload W verify { certificate header exceeds bound }`() {
        val request = request(AssignmentProtection.SIGNED)
        val response = response(request, AssignmentProtection.SIGNED)
            .newBuilder()
            .header(PrecomputedAssignmentsVerifier.CERTIFICATE_HEADER, "A".repeat(8 * 1_024 + 1))
            .build()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(request, response, RESPONSE_BODY_BYTES)
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessageContaining("header is too large")
    }

    @Test
    fun `M reject payload W verify { rules revision changed }`() {
        val request = request(AssignmentProtection.SIGNED)
        val response = response(request, AssignmentProtection.SIGNED)
            .newBuilder()
            .header(PrecomputedAssignmentsVerifier.RULES_REVISION_HEADER, "untrusted-revision")
            .build()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(request, response, RESPONSE_BODY_BYTES)
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment signature is invalid")
    }

    @Test
    fun `M reject payload W verify { subject does not match request }`() {
        val request = request(AssignmentProtection.SIGNED)
        val body = "{\"data\":{\"id\":\"other-user\"}}".toByteArray()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(
                request,
                response(request, AssignmentProtection.SIGNED),
                body
            )
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment signature is invalid")
    }

    @Test
    fun `M reject payload W verify { protected response expired }`() {
        val request = request(AssignmentProtection.SIGNED)

        assertThatThrownBy {
            PrecomputedAssignmentsVerifier(
                AssignmentProtection.SIGNED,
                currentTimeSeconds = { EXPIRES_AT + 1 }
            ).verify(request, response(request, AssignmentProtection.SIGNED), RESPONSE_BODY_BYTES)
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment response has expired")
    }

    @Test
    fun `M reject payload W verify { protected response expires now }`() {
        val request = request(AssignmentProtection.SIGNED)

        assertThatThrownBy {
            PrecomputedAssignmentsVerifier(
                AssignmentProtection.SIGNED,
                currentTimeSeconds = { EXPIRES_AT }
            ).verify(request, response(request, AssignmentProtection.SIGNED), RESPONSE_BODY_BYTES)
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment response has expired")
    }

    @Test
    fun `M reject payload W verify { protected response validity exceeds limit }`() {
        val request = request(AssignmentProtection.SIGNED)
        val response = response(request, AssignmentProtection.SIGNED)
            .newBuilder()
            .header(PrecomputedAssignmentsVerifier.EXPIRES_AT_HEADER, (ISSUED_AT + 301).toString())
            .build()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(request, response, RESPONSE_BODY_BYTES)
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment response validity is too long")
    }

    @Test
    fun `M reject payload W verify { protected request is not HTTPS }`() {
        val request = request(AssignmentProtection.SIGNED)
            .newBuilder()
            .url("http://example.test/precompute-assignments")
            .build()

        assertThatThrownBy {
            verifier(AssignmentProtection.SIGNED).verify(
                request,
                response(request, AssignmentProtection.SIGNED),
                RESPONSE_BODY_BYTES
            )
        }
            .isInstanceOf(AssignmentPayloadVerificationException::class.java)
            .hasMessage("Signed assignment request must use HTTPS")
    }

    @Test
    fun `M accept unsigned payload W verify { protection disabled }`() {
        val request = unsignedRequest()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        assertThatCode {
            verifier(AssignmentProtection.DISABLED).verify(request, response, RESPONSE_BODY_BYTES)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `M reject protected request W verify { protection disabled }`() {
        val request = request(AssignmentProtection.SIGNED)

        assertThatThrownBy {
            verifier(AssignmentProtection.DISABLED).verify(
                request,
                response(request, AssignmentProtection.SIGNED),
                RESPONSE_BODY_BYTES
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Unsigned assignment mode must not request response signing")
    }

    private fun verifier(protection: AssignmentProtection) = PrecomputedAssignmentsVerifier(
        assignmentProtection = protection,
        currentTimeSeconds = { ISSUED_AT }
    )

    private fun unsignedRequest(): Request = Request.Builder()
        .url("https://example.test/precompute-assignments")
        .header("dd-client-token", "client-token")
        .header("content-type", "application/vnd.api+json")
        .post(REQUEST_BODY.toRequestBody())
        .build()

    private fun request(protection: AssignmentProtection): Request = unsignedRequest()
        .newBuilder()
        .apply {
            header(PrecomputedAssignmentsVerifier.SIGNATURE_VERSION_HEADER, "2")
            header(PrecomputedAssignmentsVerifier.REQUEST_NONCE_HEADER, NONCE)
            if (protection == AssignmentProtection.SIGNED_AND_AUTHORIZED) {
                header("Authorization", "Bearer $COMPACT_JWT")
            }
        }
        .build()

    private fun response(
        request: Request,
        protection: AssignmentProtection
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header(PrecomputedAssignmentsVerifier.SIGNATURE_VERSION_HEADER, "2")
        .apply {
            if (protection == AssignmentProtection.SIGNED_AND_AUTHORIZED) {
                header(PrecomputedAssignmentsVerifier.AUTHORIZATION_POLICY_VERSION_HEADER, POLICY_VERSION)
            }
        }
        .header(PrecomputedAssignmentsVerifier.RULES_REVISION_HEADER, "")
        .header(PrecomputedAssignmentsVerifier.ISSUED_AT_HEADER, ISSUED_AT.toString())
        .header(PrecomputedAssignmentsVerifier.EXPIRES_AT_HEADER, EXPIRES_AT.toString())
        .header(PrecomputedAssignmentsVerifier.CERTIFICATE_ID_HEADER, LEAF_CERTIFICATE_ID)
        .header(PrecomputedAssignmentsVerifier.CERTIFICATE_HEADER, LEAF_CERTIFICATE)
        .header(
            PrecomputedAssignmentsVerifier.SIGNATURE_HEADER,
            if (protection == AssignmentProtection.SIGNED_AND_AUTHORIZED) {
                SIGNED_AND_AUTHORIZED_SIGNATURE
            } else {
                SIGNED_ONLY_SIGNATURE
            }
        )
        .build()

    private companion object {
        const val ISSUED_AT = 1_789_096_800L
        const val EXPIRES_AT = 1_789_097_100L
        const val NONCE = "000102030405060708090a0b0c0d0e0f"
        const val POLICY_VERSION = "ap_test_v2"
        const val REQUEST_BODY = "{\"data\":{\"attributes\":{\"subject\":{\"targeting_key\":\"user-1\"}}}}"
        const val RESPONSE_BODY = "{\"data\":{\"id\":\"user-1\"}}"
        val RESPONSE_BODY_BYTES = RESPONSE_BODY.toByteArray()
        const val COMPACT_JWT =
            "eyJhbGciOiJFUzI1NiIsImtpZCI6ImN1c3RvbWVyLTIwMjYtMDkiLCJ0eXAiOiJkYXRhZG9nLWZlYXR1cmUt" +
                "ZmxhZ3MtYWNjZXNzK2p3dCJ9." +
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
        const val SIGNED_ONLY_SIGNATURE =
            "MEUCIBim8DaKFvPtvhHV5yjwyPWZgZVnpz6tkiAUzAOk3bTyAiEA3OsaJ3VjUP+o6iojyD5Fv7D4+9FDNUODiDDA3XUvze4="
        const val SIGNED_AND_AUTHORIZED_SIGNATURE =
            "MEYCIQDAG5d1Edg2NhaMGfJiMTbjSmhYSsUS65ZbImV/3IeFwQIhAJjHs87OUC6Ni08YguV72YI8rAaT1ehn6da9gWLw7w+R"
    }
}
