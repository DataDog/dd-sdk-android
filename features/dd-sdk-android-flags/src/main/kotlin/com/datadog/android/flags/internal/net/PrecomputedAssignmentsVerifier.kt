/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.flags.AssignmentProtection
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
import java.security.interfaces.ECPublicKey
import java.util.Date

internal data class ProtectedAssignmentEnvelope(
    val protection: AssignmentProtection,
    val requestNonce: String,
    val responseStatus: Int,
    val authorizationPolicyVersion: String?,
    val rulesRevision: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val certificateId: String,
    val certificate: String,
    val signature: String
)

internal fun interface AssignmentPayloadVerifier {
    fun verify(request: Request, response: Response, responseBody: ByteArray): ProtectedAssignmentEnvelope?
}

internal class AssignmentPayloadVerificationException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

@Suppress(
    "RequireInternal",
    "ThrowingInternalException",
    "TooManyFunctions",
    "UnsafeThirdPartyFunctionCall"
) // Verification catches failures at its entry point and rejects the response.
internal class PrecomputedAssignmentsVerifier(
    private val assignmentProtection: AssignmentProtection,
    private val currentTimeSeconds: () -> Long = {
        DefaultTimeProvider().getServerTimestampMillis() / MILLIS_PER_SECOND
    }
) : AssignmentPayloadVerifier {

    @Suppress("TooGenericExceptionCaught")
    override fun verify(
        request: Request,
        response: Response,
        responseBody: ByteArray
    ): ProtectedAssignmentEnvelope? {
        if (assignmentProtection == AssignmentProtection.DISABLED) {
            require(request.headers.values(SIGNATURE_VERSION_HEADER).isEmpty()) {
                "Unsigned assignment mode must not request response signing"
            }
            require(request.headers.values(REQUEST_NONCE_HEADER).isEmpty()) {
                "Unsigned assignment mode must not send a signing nonce"
            }
            require(request.headers.values(AUTHORIZATION_HEADER).isEmpty()) {
                "Unsigned assignment mode must not send assignment authorization"
            }
            return null
        }

        return try {
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
    private fun verifySignedPayload(
        request: Request,
        response: Response,
        responseBody: ByteArray
    ): ProtectedAssignmentEnvelope {
        require(responseBody.size <= MAX_RESPONSE_BODY_BYTES) {
            "Signed assignment response body is too large"
        }
        require(request.url.isHttps) { "Signed assignment request must use HTTPS" }
        require(request.requiredSingleHeader(SIGNATURE_VERSION_HEADER, MAX_SHORT_HEADER_LENGTH) == SIGNATURE_VERSION) {
            "Signed assignment request has no supported signature version"
        }
        require(response.requiredSingleHeader(SIGNATURE_VERSION_HEADER, MAX_SHORT_HEADER_LENGTH) == SIGNATURE_VERSION) {
            "Signed assignment response has no supported signature version"
        }

        val nonceValue = request.requiredSingleHeader(REQUEST_NONCE_HEADER, NONCE_SIZE_BYTES * HEX_BYTE_WIDTH)
        val nonce = decodeHex(nonceValue)
        require(nonce.size == NONCE_SIZE_BYTES) { "Signed assignment nonce has an invalid size" }

        val authorizationHeaders = request.headers.values(AUTHORIZATION_HEADER)
        val compactJwt = when (assignmentProtection) {
            AssignmentProtection.DISABLED -> error("Disabled protection cannot verify a signed response")
            AssignmentProtection.SIGNED -> {
                require(authorizationHeaders.isEmpty()) {
                    "Signed-only assignment request must not contain Authorization"
                }
                null
            }
            AssignmentProtection.SIGNED_AND_AUTHORIZED -> {
                require(authorizationHeaders.size == 1) {
                    "Signed assignment request must contain one Authorization header"
                }
                val authorization = authorizationHeaders.single()
                require(authorization.length <= MAX_AUTHORIZATION_HEADER_LENGTH) {
                    "Signed assignment Authorization is too large"
                }
                require(authorization.startsWith(BEARER_PREFIX)) {
                    "Signed assignment Authorization must use Bearer"
                }
                authorization.removePrefix(BEARER_PREFIX).also {
                    require(it.isNotEmpty() && it.none(Char::isWhitespace)) {
                        "Signed assignment bearer token is invalid"
                    }
                }
            }
        }

        val policyVersionValues = response.headers.values(AUTHORIZATION_POLICY_VERSION_HEADER)
        val policyVersion = when (assignmentProtection) {
            AssignmentProtection.SIGNED -> {
                require(policyVersionValues.isEmpty()) {
                    "Signed-only assignment response must not contain an authorization policy version"
                }
                null
            }
            AssignmentProtection.SIGNED_AND_AUTHORIZED -> response.requiredSingleHeader(
                AUTHORIZATION_POLICY_VERSION_HEADER,
                MAX_POLICY_VERSION_LENGTH
            ).also {
                require(it.isNotEmpty()) { "Signed assignment authorization policy version is empty" }
            }
            AssignmentProtection.DISABLED -> null
        }

        val issuedAt = response.requiredSingleHeader(ISSUED_AT_HEADER, MAX_TIMESTAMP_LENGTH).toLong()
        val expiresAt = response.requiredSingleHeader(EXPIRES_AT_HEADER, MAX_TIMESTAMP_LENGTH).toLong()
        val rulesRevision = response.requiredSingleHeader(RULES_REVISION_HEADER, MAX_RULES_REVISION_LENGTH)
        val now = currentTimeSeconds()
        require(issuedAt >= 0) { "Signed assignment response issue time is invalid" }
        require(issuedAt <= now + CLOCK_SKEW_SECONDS) { "Signed assignment response is not valid yet" }
        require(expiresAt >= issuedAt) { "Signed assignment response validity is invalid" }
        require(expiresAt > now) { "Signed assignment response has expired" }
        require(expiresAt - issuedAt <= MAX_VALIDITY_SECONDS) {
            "Signed assignment response validity is too long"
        }

        val certificateValue = response.requiredSingleHeader(CERTIFICATE_HEADER, MAX_CERTIFICATE_HEADER_LENGTH)
        val leaf = parseCertificate(certificateValue)
        val certificateId = response.requiredSingleHeader(CERTIFICATE_ID_HEADER, SHA256_HEX_LENGTH)
        require(certificateId.length == SHA256_HEX_LENGTH && certificateId.all(::isLowercaseHexDigit)) {
            "Signed assignment certificate ID is invalid"
        }
        require(certificateId == leaf.encoded.sha256Digest().toHex()) {
            "Signed assignment certificate ID is invalid"
        }
        verifyCertificate(leaf, now)

        val requestBody = Buffer().use { buffer ->
            request.body?.writeTo(buffer)
            require(buffer.size <= MAX_REQUEST_BODY_BYTES) { "Signed assignment request body is too large" }
            buffer.readByteArray()
        }
        val clientToken = request.requiredSingleHeader(CLIENT_TOKEN_HEADER, MAX_CLIENT_TOKEN_LENGTH)
        val input = signatureInput(
            nonce = nonce,
            request = request,
            requestBody = requestBody,
            compactJwt = compactJwt,
            clientToken = clientToken,
            policyVersion = policyVersion,
            rulesRevision = rulesRevision,
            responseStatus = response.code,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            responseBody = responseBody
        )
        val signatureValue = response.requiredSingleHeader(SIGNATURE_HEADER, MAX_SIGNATURE_HEADER_LENGTH)
        val signatureBytes = requireNotNull(signatureValue.decodeBase64()).toByteArray()
        require(signatureBytes.size <= MAX_DER_SIGNATURE_BYTES) { "Signed assignment signature is too large" }
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(leaf.publicKey)
        signature.update(input)
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

        return ProtectedAssignmentEnvelope(
            protection = assignmentProtection,
            requestNonce = nonceValue,
            responseStatus = response.code,
            authorizationPolicyVersion = policyVersion,
            rulesRevision = rulesRevision,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            certificateId = certificateId,
            certificate = certificateValue,
            signature = signatureValue
        )
    }

    private fun verifyCertificate(leaf: X509Certificate, now: Long) {
        val root = parseCertificate(ROOT_CERTIFICATE_BASE64)
        require(root.basicConstraints >= 0) { "Signed assignment root is not a CA certificate" }
        require(leaf.basicConstraints < 0) { "Signed assignment leaf is not an end certificate" }
        val publicKey = leaf.publicKey as? ECPublicKey
            ?: throw IllegalArgumentException("Signed assignment leaf key is not EC")
        require(
            publicKey.params.curve.field.fieldSize == P256_FIELD_SIZE_BITS &&
                publicKey.params.order.bitLength() == P256_FIELD_SIZE_BITS
        ) { "Signed assignment leaf key is not P-256" }
        require(leaf.keyUsage?.getOrNull(DIGITAL_SIGNATURE_KEY_USAGE_INDEX) == true) {
            "Signed assignment leaf cannot sign payloads"
        }
        require(leaf.issuerX500Principal == root.subjectX500Principal) {
            "Signed assignment certificate has an invalid issuer"
        }
        root.checkValidity(Date(now * MILLIS_PER_SECOND))
        leaf.checkValidity(Date(now * MILLIS_PER_SECOND))
        leaf.verify(root.publicKey)
    }

    private fun parseCertificate(base64: String): X509Certificate {
        val bytes = requireNotNull(base64.decodeBase64()).toByteArray()
        require(bytes.size <= MAX_CERTIFICATE_DER_BYTES) { "Signed assignment certificate is too large" }
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }

    @Suppress("LongParameterList")
    private fun signatureInput(
        nonce: ByteArray,
        request: Request,
        requestBody: ByteArray,
        compactJwt: String?,
        clientToken: String,
        policyVersion: String?,
        rulesRevision: String,
        responseStatus: Int,
        issuedAt: Long,
        expiresAt: Long,
        responseBody: ByteArray
    ): ByteArray {
        require(responseStatus in 0..USHORT_MAX) { "Signed assignment response status is invalid" }
        require(request.url.query == null) { "Signed assignment request must not contain a query" }
        val scheme = request.url.scheme.lowercase()
        val defaultPort = if (request.url.isHttps) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
        val authority = if (request.url.port == defaultPort) {
            request.url.host.lowercase()
        } else {
            "${request.url.host.lowercase()}:${request.url.port}"
        }
        require(scheme.length <= MAX_URL_COMPONENT_LENGTH) { "Signed assignment scheme is too large" }
        require(authority.length <= MAX_URL_COMPONENT_LENGTH) { "Signed assignment authority is too large" }
        require(request.url.encodedPath.length <= MAX_URL_COMPONENT_LENGTH) {
            "Signed assignment path is too large"
        }
        require((compactJwt == null) == (policyVersion == null)) {
            "Signed assignment authorization scope is inconsistent"
        }

        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(SIGNATURE_DOMAIN.toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(request.method.uppercase().toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(scheme.toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(authority.toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(request.url.encodedPath.toByteArray(StandardCharsets.UTF_8))
            output.writeLengthPrefixed(nonce)
            output.write(sha256(requestBody))
            if (compactJwt == null) {
                output.writeByte(0)
            } else {
                output.writeByte(1)
                output.write(sha256(compactJwt.toByteArray(StandardCharsets.UTF_8)))
            }
            output.write(sha256(clientToken.toByteArray(StandardCharsets.UTF_8)))
            if (policyVersion != null) {
                output.writeLengthPrefixed(policyVersion.toByteArray(StandardCharsets.UTF_8))
            }
            output.writeLengthPrefixed(rulesRevision.toByteArray(StandardCharsets.UTF_8))
            SEMANTIC_REQUEST_HEADERS.forEach { name ->
                val values = request.headers.values(name)
                require(values.size <= 1) { "Signed assignment request contains duplicate $name headers" }
                output.writeOptionalHeader(values.singleOrNull())
            }
            output.writeShort(responseStatus)
            output.writeLong(issuedAt)
            output.writeLong(expiresAt)
            output.writeLong(responseBody.size.toLong())
            output.write(sha256(responseBody))
        }
        return bytes.toByteArray()
    }

    private fun Request.requiredSingleHeader(name: String, maxLength: Int): String {
        val values = headers.values(name)
        require(values.size == 1) { "Signed assignment request must contain one $name header" }
        return values.single().also {
            require(it.length <= maxLength) { "Signed assignment $name header is too large" }
        }
    }

    private fun Response.requiredSingleHeader(name: String, maxLength: Int): String {
        val values = headers.values(name)
        require(values.size == 1) { "Signed assignment response must contain one $name header" }
        return values.single().also {
            require(it.length <= maxLength) { "Signed assignment $name header is too large" }
        }
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

    private fun isLowercaseHexDigit(character: Char): Boolean = character in '0'..'9' || character in 'a'..'f'

    internal companion object {
        const val SIGNATURE_VERSION_HEADER = "x-datadog-feature-flags-signature-version"
        const val REQUEST_NONCE_HEADER = "x-datadog-feature-flags-request-nonce"
        const val SIGNATURE_HEADER = "x-datadog-feature-flags-signature"
        const val CERTIFICATE_HEADER = "x-datadog-feature-flags-signing-certificate"
        const val CERTIFICATE_ID_HEADER = "x-datadog-feature-flags-certificate-id"
        const val ISSUED_AT_HEADER = "x-datadog-feature-flags-issued-at"
        const val EXPIRES_AT_HEADER = "x-datadog-feature-flags-expires-at"
        const val AUTHORIZATION_POLICY_VERSION_HEADER =
            "x-datadog-feature-flags-authorization-policy-version"
        const val RULES_REVISION_HEADER = "x-datadog-feature-flags-rules-revision"
        const val SIGNATURE_VERSION = "2"
        const val MAX_RESPONSE_BODY_BYTES = 2 * 1_024 * 1_024

        private const val CLIENT_TOKEN_HEADER = "dd-client-token"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val SIGNATURE_DOMAIN = "datadog.feature-flags.precomputed-assignments.v2\u0000"
        private val SEMANTIC_REQUEST_HEADERS = listOf(
            "content-type",
            "dd-application-id",
            "x-rkyv",
            "x-use-cache"
        )
        private const val NONCE_SIZE_BYTES = 16
        private const val MILLIS_PER_SECOND = 1_000L
        private const val HTTP_DEFAULT_PORT = 80
        private const val HTTPS_DEFAULT_PORT = 443
        private const val USHORT_MAX = 65_535
        private const val HEX_BYTE_MASK = 0xff
        private const val HEX_RADIX = 16
        private const val HEX_BYTE_WIDTH = 2
        private const val SHA256_HEX_LENGTH = 64
        private const val CLOCK_SKEW_SECONDS = 30
        private const val MAX_VALIDITY_SECONDS = 300
        const val MAX_REQUEST_BODY_BYTES = 1_024 * 1_024
        private const val MAX_AUTHORIZATION_HEADER_LENGTH = 4_103
        private const val MAX_CLIENT_TOKEN_LENGTH = 512
        private const val MAX_SHORT_HEADER_LENGTH = 16
        private const val MAX_POLICY_VERSION_LENGTH = 256
        private const val MAX_RULES_REVISION_LENGTH = 256
        private const val MAX_TIMESTAMP_LENGTH = 20
        private const val MAX_CERTIFICATE_HEADER_LENGTH = 8 * 1_024
        private const val MAX_CERTIFICATE_DER_BYTES = 4 * 1_024
        private const val MAX_SIGNATURE_HEADER_LENGTH = 256
        private const val MAX_DER_SIGNATURE_BYTES = 80
        private const val MAX_URL_COMPONENT_LENGTH = 2_048
        private const val DIGITAL_SIGNATURE_KEY_USAGE_INDEX = 0
        private const val P256_FIELD_SIZE_BITS = 256
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
