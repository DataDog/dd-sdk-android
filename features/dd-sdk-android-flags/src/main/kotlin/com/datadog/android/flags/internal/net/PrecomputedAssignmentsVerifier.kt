/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
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

internal class PrecomputedAssignmentsVerifier(
    private val currentTimeSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) : AssignmentPayloadVerifier {

    override fun verify(request: Request, response: Response, responseBody: ByteArray) {
        require(response.header(SIGNATURE_VERSION_HEADER) == SIGNATURE_VERSION) {
            "Signed assignment response has no supported signature version"
        }
        val nonce = decodeHex(requireNotNull(request.header(REQUEST_NONCE_HEADER)))
        require(nonce.size == NONCE_SIZE_BYTES) { "Signed assignment nonce has an invalid size" }
        val issuedAt = requireNotNull(response.header(ISSUED_AT_HEADER)).toLong()
        val expiresAt = requireNotNull(response.header(EXPIRES_AT_HEADER)).toLong()
        val now = currentTimeSeconds()
        require(issuedAt <= now + CLOCK_SKEW_SECONDS) { "Signed assignment response is not valid yet" }
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
        leaf.checkValidity(Date(now * 1000))
        leaf.verify(root.publicKey)

        val requestBody = Buffer().use { buffer ->
            request.body?.writeTo(buffer)
            buffer.readByteArray()
        }
        val clientToken = requireNotNull(request.header(CLIENT_TOKEN_HEADER))
        val input = signatureInput(
            nonce = nonce,
            requestBody = requestBody,
            clientToken = clientToken,
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
    }

    private fun parseCertificate(base64: String): X509Certificate {
        val bytes = requireNotNull(base64.decodeBase64()).toByteArray()
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }

    private fun signatureInput(
        nonce: ByteArray,
        requestBody: ByteArray,
        clientToken: String,
        issuedAt: Long,
        expiresAt: Long,
        responseBody: ByteArray
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(SIGNATURE_DOMAIN.toByteArray(StandardCharsets.UTF_8))
            output.writeByte(0)
            output.writeInt(nonce.size)
            output.write(nonce)
            output.write(sha256(requestBody))
            output.write(sha256(clientToken.toByteArray(StandardCharsets.UTF_8)))
            output.writeLong(issuedAt)
            output.writeLong(expiresAt)
            output.writeLong(responseBody.size.toLong())
            output.write(responseBody)
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray): ByteArray = bytes.sha256Digest()

    private fun ByteArray.sha256Digest(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    private fun ByteArray.toHex(): String = joinToString(separator = "") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private fun decodeHex(value: String): ByteArray {
        require(value.length % 2 == 0) { "Hexadecimal value has an invalid size" }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    internal companion object {
        const val SIGNATURE_VERSION_HEADER = "x-dd-ffe-signature-version"
        const val REQUEST_NONCE_HEADER = "x-dd-ffe-request-nonce"
        const val SIGNATURE_VERSION = "1"

        private const val SIGNATURE_HEADER = "x-dd-ffe-signature"
        private const val CERTIFICATE_HEADER = "x-dd-ffe-signing-certificate"
        private const val CERTIFICATE_ID_HEADER = "x-dd-ffe-certificate-id"
        private const val ISSUED_AT_HEADER = "x-dd-ffe-issued-at"
        private const val EXPIRES_AT_HEADER = "x-dd-ffe-expires-at"
        private const val CLIENT_TOKEN_HEADER = "dd-client-token"
        private const val SIGNATURE_DOMAIN = "datadog.ffe.precomputed-assignments.v1"
        private const val NONCE_SIZE_BYTES = 16
        private const val CLOCK_SKEW_SECONDS = 30
        private const val MAX_VALIDITY_SECONDS = 600
        private const val ROOT_CERTIFICATE_BASE64 =
            "MIIBlTCCATugAwIBAgIBATAKBggqhkjOPQQDAjAyMTAwLgYDVQQDEydEYXRhZG9nIEZGRSBTaWduZWQg" +
                "QXNzaWdubWVudHMgUE9DIFJvb3QwHhcNMjYwMTAxMDAwMDAwWhcNMzYwMTAxMDAwMDAwWjAyMTAw" +
                "LgYDVQQDEydEYXRhZG9nIEZGRSBTaWduZWQgQXNzaWdubWVudHMgUE9DIFJvb3QwWTATBgcqhkjOPQIB" +
                "BggqhkjOPQMBBwNCAAQzJMvRTfKpBAxFNBvEdLNTOK/cna8MQivOtVYnJ8qeRLVrPw01tPF8F4RaShZU" +
                "qhuBa62T9uRApLe/3CZ2xkPIo0IwQDAOBgNVHQ8BAf8EBAMCAoQwDwYDVR0TAQH/BAUwAwEB/zAdBgNV" +
                "HQ4EFgQUhos9mSbUI3BmaP8jto591fzhV+IwCgYIKoZIzj0EAwIDSAAwRQIgHRu3XCCaGw1V170Cqc3J" +
                "dslBV43MzyzJctlo8cuGS8kCIQCVeyCHpzf8pmvO9Oyep/JiY633sJYRfBNzQYObaeCZEw=="
    }
}
