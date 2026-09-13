/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("RequireInternal", "ThrowingInternalException", "UnsafeThirdPartyFunctionCall")

package com.datadog.android.flags.internal.net

import com.datadog.android.internal.time.DefaultTimeProvider
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

internal fun interface AssignmentPayloadVerifier {
    fun verify(request: Request, response: Response, responseBody: ByteArray)
}

internal class AssignmentPayloadVerificationException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

internal class PrecomputedAssignmentsVerifier(
    private val currentTimeSeconds: () -> Long = {
        DefaultTimeProvider().getServerTimestampMillis() / MILLIS_PER_SECOND
    }
) : AssignmentPayloadVerifier {

    @Suppress("TooGenericExceptionCaught")
    override fun verify(request: Request, response: Response, responseBody: ByteArray) {
        val requestedVersion = request.header(SIGNATURE_VERSION_HEADER)
        val authorizationHeaders = request.headers.values(AUTHORIZATION_HEADER)
        if (requestedVersion == null && authorizationHeaders.isEmpty()) return

        try {
            verifySignedPayload(request, response, responseBody)
        } catch (e: AssignmentPayloadVerificationException) {
            throw e
        } catch (e: Exception) {
            throw AssignmentPayloadVerificationException(
                e.message ?: "Signed assignment response verification failed",
                e
            )
        }
    }

    @Suppress("LongMethod")
    private fun verifySignedPayload(request: Request, response: Response, responseBody: ByteArray) {
        require(request.header(SIGNATURE_VERSION_HEADER) == SIGNATURE_VERSION) {
            "Signed assignment request has no supported signature version"
        }
        require(response.header(SIGNATURE_VERSION_HEADER) == SIGNATURE_VERSION) {
            "Signed assignment response has no supported signature version"
        }
        val nonce = decodeHex(requireNotNull(request.header(REQUEST_NONCE_HEADER)))
        require(nonce.size == NONCE_SIZE_BYTES) { "Signed assignment nonce has an invalid size" }
        val authorizationHeaders = request.headers.values(AUTHORIZATION_HEADER)
        require(authorizationHeaders.size == 1) { "Signed assignment request must contain one Authorization header" }
        val authorization = authorizationHeaders.single()
        require(authorization.startsWith(BEARER_PREFIX)) { "Signed assignment Authorization must use Bearer" }
        val compactJwt = authorization.removePrefix(BEARER_PREFIX)
        require(compactJwt.isNotEmpty() && compactJwt.none(Char::isWhitespace)) {
            "Signed assignment bearer token is invalid"
        }
        val policyVersion = requireNotNull(response.header(AUTHORIZATION_POLICY_VERSION_HEADER))
        require(policyVersion.isNotEmpty()) { "Signed assignment authorization policy version is empty" }
        val issuedAt = requireNotNull(response.header(ISSUED_AT_HEADER)).toLong()
        val expiresAt = requireNotNull(response.header(EXPIRES_AT_HEADER)).toLong()
        val now = currentTimeSeconds()
        require(issuedAt >= 0) { "Signed assignment response issue time is invalid" }
        require(issuedAt <= now + CLOCK_SKEW_SECONDS) { "Signed assignment response is not valid yet" }
        require(expiresAt >= issuedAt) { "Signed assignment response validity is invalid" }
        require(expiresAt >= now) { "Signed assignment response has expired" }
        require(expiresAt - issuedAt <= MAX_VALIDITY_SECONDS) {
            "Signed assignment response validity is too long"
        }

        val leaf = parseCertificate(requireNotNull(response.header(CERTIFICATE_HEADER)))
        val certificateId = requireNotNull(response.header(CERTIFICATE_ID_HEADER))
        require(certificateId == leaf.encoded.sha256Digest().toHex()) {
            "Signed assignment certificate ID is invalid"
        }
        val root = parseCertificate(ROOT_CERTIFICATE_BASE64)
        require(root.basicConstraints >= 0) { "Signed assignment root is not a CA certificate" }
        require(leaf.basicConstraints < 0) { "Signed assignment leaf is not an end certificate" }
        leaf.checkValidity(Date(now * MILLIS_PER_SECOND))
        leaf.verify(root.publicKey)

        val requestBody = Buffer().use { buffer ->
            request.body?.writeTo(buffer)
            buffer.readByteArray()
        }
        val clientToken = requireNotNull(request.header(CLIENT_TOKEN_HEADER))
        val input = signatureInput(
            nonce = nonce,
            request = request,
            requestBody = requestBody,
            compactJwt = compactJwt,
            clientToken = clientToken,
            policyVersion = policyVersion,
            responseStatus = response.code,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            responseBody = responseBody
        )
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(leaf.publicKey)
        signature.update(input)
        val signatureBytes = requireNotNull(
            requireNotNull(response.header(SIGNATURE_HEADER)).decodeBase64()
        ).toByteArray()
        require(signature.verify(signatureBytes)) { "Signed assignment signature is invalid" }

        val requestSubject = JSONObject(requestBody.toString(StandardCharsets.UTF_8))
            .getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("subject")
            .getString("targeting_key")
        val responseSubject = JSONObject(responseBody.toString(StandardCharsets.UTF_8))
            .getJSONObject("data")
            .getString("id")
        require(responseSubject == requestSubject) {
            "Signed assignment response subject does not match the request"
        }
    }

    private fun parseCertificate(base64: String): X509Certificate {
        val bytes = requireNotNull(base64.decodeBase64()).toByteArray()
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }

    private fun signatureInput(
        nonce: ByteArray,
        request: Request,
        requestBody: ByteArray,
        compactJwt: String,
        clientToken: String,
        policyVersion: String,
        responseStatus: Int,
        issuedAt: Long,
        expiresAt: Long,
        responseBody: ByteArray
    ): ByteArray {
        require(responseStatus in 0..USHORT_MAX) { "Signed assignment response status is invalid" }
        require(request.url.query == null) { "Signed assignment request must not contain a query" }
        val defaultPort = if (request.url.isHttps) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
        val authority = if (request.url.port == defaultPort) {
            request.url.host.lowercase()
        } else {
            "${request.url.host.lowercase()}:${request.url.port}"
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(SIGNATURE_DOMAIN.toByteArray(StandardCharsets.UTF_8))
            output.writeByte(0)
            output.writeLengthPrefixed(request.method.uppercase().toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(authority.toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(request.url.encodedPath.toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(nonce)
            output.write(sha256(requestBody))
            output.write(sha256(compactJwt.toByteArray(StandardCharsets.UTF_8)))
            output.write(sha256(clientToken.toByteArray(StandardCharsets.UTF_8)))
            output.writeLengthPrefixed(policyVersion.toByteArray(StandardCharsets.UTF_8))
            SEMANTIC_REQUEST_HEADERS.forEach { name ->
                output.writeOptionalHeader(request.header(name))
            }
            output.writeShort(responseStatus)
            output.writeLong(issuedAt)
            output.writeLong(expiresAt)
            output.writeLong(responseBody.size.toLong())
            output.write(sha256(responseBody))
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.writeLengthPrefixed(value: ByteArray) {
        writeInt(value.size)
        write(value)
    }

    private fun DataOutputStream.writeOptionalHeader(value: String?) {
        if (value == null) {
            writeByte(0)
        } else {
            writeByte(1)
            writeLengthPrefixed(value.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray = bytes.sha256Digest()

    private fun ByteArray.sha256Digest(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    private fun ByteArray.toHex(): String = joinToString(separator = "") {
        (it.toInt() and HEX_BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
    }

    private fun decodeHex(value: String): ByteArray {
        require(value.length % HEX_BYTE_WIDTH == 0) { "Hexadecimal value has an invalid size" }
        return ByteArray(value.length / HEX_BYTE_WIDTH) { index ->
            val start = index * HEX_BYTE_WIDTH
            value.substring(start, start + HEX_BYTE_WIDTH).toInt(HEX_RADIX).toByte()
        }
    }

    internal companion object {
        const val SIGNATURE_VERSION_HEADER = "x-dd-ffe-signature-version"
        const val REQUEST_NONCE_HEADER = "x-dd-ffe-request-nonce"
        const val SIGNATURE_VERSION = "2"

        private const val SIGNATURE_HEADER = "x-dd-ffe-signature"
        private const val CERTIFICATE_HEADER = "x-dd-ffe-signing-certificate"
        private const val CERTIFICATE_ID_HEADER = "x-dd-ffe-certificate-id"
        private const val ISSUED_AT_HEADER = "x-dd-ffe-issued-at"
        private const val EXPIRES_AT_HEADER = "x-dd-ffe-expires-at"
        private const val AUTHORIZATION_POLICY_VERSION_HEADER = "x-dd-ffe-authorization-policy-version"
        private const val CLIENT_TOKEN_HEADER = "dd-client-token"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val SIGNATURE_DOMAIN = "datadog.ffe.precomputed-assignments.v2"
        private val SEMANTIC_REQUEST_HEADERS = listOf(
            "content-type",
            "dd-application-id",
            "x-rkyv",
            "x-use-cache",
            "x-dd-ffe-test-drive"
        )
        private const val NONCE_SIZE_BYTES = 16
        private const val MILLIS_PER_SECOND = 1_000L
        private const val HTTP_DEFAULT_PORT = 80
        private const val HTTPS_DEFAULT_PORT = 443
        private const val USHORT_MAX = 65_535
        private const val HEX_BYTE_MASK = 0xff
        private const val HEX_RADIX = 16
        private const val HEX_BYTE_WIDTH = 2
        private const val CLOCK_SKEW_SECONDS = 30
        private const val MAX_VALIDITY_SECONDS = 600
        private const val ROOT_CERTIFICATE_BASE64 =
            "MIIBzTCCAXSgAwIBAgIUMaYCojzGfYGo+3+sbHh9qbEf0N0wCgYIKoZIzj0EAwIwMzExMC8GA1UEAwwo" +
                "RGF0YWRvZyBGRkUgRWRnZSBBc3NpZ25tZW50cyBQT0MgUm9vdCBLMTAeFw0yNjA5MTEwMzE4MDhaFw0z" +
                "NjA5MDgwMzE4MDhaMDMxMTAvBgNVBAMMKERhdGFkb2cgRkZFIEVkZ2UgQXNzaWdubWVudHMgUE9DIFJv" +
                "b3QgSzEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASd8AStJsI0bU1Cwnl3bjgrdXAsFAkZdyX1/LSR" +
                "bBrnP4aodCpiHsWP0kspNx7/Q0U0Cyk/k7FGjCPe5ViukGnno2YwZDAdBgNVHQ4EFgQUca1+zBtEI45y" +
                "ELrJkdqSGS1J2rgwHwYDVR0jBBgwFoAUca1+zBtEI45yELrJkdqSGS1J2rgwEgYDVR0TAQH/BAgwBgEB" +
                "/wIBADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDRwAwRAIgA3JkZVvLCmyUu3r9yyEAYufb12dI" +
                "tZfiA4f7KuUnqekCIFX3MOkLKGosREDoBKmdVPr0rbMh8qgF3t1aKlciltOG"
    }
}
