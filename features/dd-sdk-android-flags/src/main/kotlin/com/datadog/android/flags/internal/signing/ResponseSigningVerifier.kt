/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import okhttp3.Headers
import okhttp3.Request
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.cert.X509Certificate
import java.util.Locale

internal data class VerifiedResponse(val scope: String, val verifiedAt: Long, val expiresAt: Long)

/** Native JCA verifies signatures; the explicit anchor never enters a system trust/path builder. */
@Suppress("MagicNumber") // Time windows, DER OIDs and integer widths are frozen protocol constants.
internal class ResponseSigningVerifier(private val trustedRoots: List<String>) {
    fun verify(request: Request, status: Int, headers: Headers, body: ByteArray, now: Long): VerifiedResponse? = try {
        verifyProof(request, status, headers, body, now)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun verifyProof(
        request: Request,
        status: Int,
        headers: Headers,
        body: ByteArray,
        now: Long
    ): VerifiedResponse {
        signingCheck(status == 200 && body.size <= MAX_RESPONSE_BYTES)
        signingCheck(single(headers, PREFIX + "signature-version") == "3")
        val leafBytes = base64(single(headers, PREFIX + "signing-certificate"), 4096)
        val intermediateBytes = base64(single(headers, PREFIX + "signing-intermediate"), 4096)
        signingCheck(single(headers, PREFIX + "certificate-id") == leafBytes.signingBytes().sha256().hex())
        val issued = seconds(single(headers, PREFIX + "issued-at"))
        val expires = seconds(single(headers, PREFIX + "expires-at"))
        signingCheck(now >= 0 && issued <= now + 30 && expires > now && expires > issued && expires - issued <= 300)
        val leaf = certificate(leafBytes)
        val intermediate = certificate(intermediateBytes)
        leaf.restrict(false)
        intermediate.restrict(true)
        val anchor = trustedRoots.asSequence().mapNotNull { root ->
            try {
                certificate(base64(root, 4096)).takeIf { parent ->
                    parent.covers(now, issued, expires) && intermediate.issuedBy(parent)
                }
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.firstOrNull()
        signingCheck(anchor != null && leaf.issuedBy(intermediate))
        signingCheck(leaf.covers(now, issued, expires) && intermediate.covers(now, issued, expires))
        val signature = base64(single(headers, PREFIX + "signature"), 72)
        strictSignature(signature)
        val transcript = requestTranscript(request)
        transcript.writeShort(status).writeLong(issued).writeLong(expires).writeLong(body.size.toLong())
        transcript.write(body.signingBytes().sha256())
        transcript.write(leafBytes.signingBytes().sha256()).write(intermediateBytes.signingBytes().sha256())
        signingCheck(verifySignature(leaf.native.publicKey, transcript.signingBytes(), signature))
        return VerifiedResponse(scope(request), now, expires)
    }

    private class Certificate(
        val native: X509Certificate,
        val issuer: ByteArray,
        val subject: ByteArray,
        val extensions: Map<String, Pair<Boolean, ByteArray>>
    ) {
        fun covers(now: Long, issued: Long, expires: Long): Boolean {
            val start = native.notBefore.time / 1000
            val end = native.notAfter.time / 1000
            return now in start..end && issued >= start && expires <= end
        }

        @Suppress("ReturnCount") // Reject each issuer linkage before invoking native cryptography.
        fun issuedBy(parent: Certificate): Boolean {
            if (!issuer.contentEquals(parent.subject) || native.notBefore.time < parent.native.notBefore.time ||
                native.notAfter.time > parent.native.notAfter.time
            ) {
                return false
            }
            val aki = extensions[AUTHORITY_KEY_ID]?.second?.let { identifier(it, true) }
            val ski = parent.extensions[SUBJECT_KEY_ID]?.second?.let { identifier(it, false) }
            if (aki != null && ski != null && !aki.contentEquals(ski)) return false
            return verifyCertificate(native, parent.native.publicKey)
        }

        fun restrict(isIntermediate: Boolean) {
            val basic = extensions["551d13"] ?: signingFailure()
            val usage = extensions["551d0f"] ?: signingFailure()
            signingCheck(basic.first && usage.first)
            signingCheck(basic.second.contentEquals(hex(if (isIntermediate) "30060101ff020100" else "3000")))
            signingCheck(usage.second.contentEquals(hex(if (isIntermediate) "03020106" else "03020780")))
            val eku = extensions["551d25"]?.second
            signingCheck((isIntermediate && eku == null) || eku?.contentEquals(hex("300a06082b06010505070303")) == true)
        }
    }

    // Explicit checks preserve the restricted certificate profile.
    @Suppress("CyclomaticComplexMethod", "StringLiteralDuplication")
    private fun certificate(bytes: ByteArray): Certificate {
        val cert = SigningDer.read(bytes).sequence()
        signingCheck(cert.size == 3)
        val tbs = cert[0].sequence()
        signingCheck(tbs.size == 8 && tbs[0].encoded.contentEquals(hex("a003020102")))
        signingCheck(tbs[1].tag == 2 && tbs[1].value[0].toInt() and 128 == 0)
        signingCheck(cert[1].encoded.contentEquals(hex("300a06082a8648ce3d040302")))
        signingCheck(tbs[2].encoded.contentEquals(cert[1].encoded))
        signingCheck(cert[2].tag == 3 && cert[2].value[0] == 0.toByte())
        strictSignature(signingSlice(cert[2].value, 1, cert[2].value.size))
        val spki = tbs[6].sequence()
        signingCheck(spki.size == 2 && spki[0].encoded.contentEquals(hex("301306072a8648ce3d020106082a8648ce3d030107")))
        signingCheck(
            spki[1].tag == 3 && spki[1].value.size == 66 && spki[1].value[0] == 0.toByte() &&
                spki[1].value[1] == 4.toByte()
        )
        signingCheck(tbs[7].tag == 0xa3 && tbs[7].children.size == 1)
        val extensions = linkedMapOf<String, Pair<Boolean, ByteArray>>()
        tbs[7].children[0].sequence().forEach { extension ->
            val fields = extension.sequence()
            signingCheck(fields.size in 2..3 && fields[0].tag == 6 && fields[fields.size - 1].tag == 4)
            val oid = fields[0].value.signingBytes().hex()
            signingCheck(!extensions.containsKey(oid))
            val critical = fields.size == 3
            if (critical) signingCheck(fields[1].encoded.contentEquals(hex("0101ff")))
            signingCheck(!critical || oid in setOf("551d13", "551d0f", "551d25", "551d0e", "551d23"))
            signingCheck(oid !in setOf("551d1e", "551d24", "551d36"))
            val value = fields[fields.size - 1].value
            SigningDer.read(value)
            if (oid == "551d0e") identifier(value, false)
            if (oid == "551d23") identifier(value, true)
            extensions[oid] = critical to value
        }
        val native = signingCertificate(bytes)
        return Certificate(native, tbs[3].encoded, tbs[5].encoded, extensions)
    }

    companion object {
        const val PREFIX = "x-datadog-feature-flags-"
        private const val NONCE_HEADER = PREFIX + "request-nonce"
        const val CAPABILITY = "response-signing-v3"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val AUTHORITY_KEY_ID = "551d23"
        private const val SUBJECT_KEY_ID = "551d0e"
        private const val MAX_REQUEST_BYTES = 1024 * 1024
        private val SEMANTIC_HEADERS =
            listOf("content-type", "dd-application-id", "x-rkyv", "x-use-cache", "x-dd-feature-flags-capabilities")

        fun matchesRequest(expected: Request, actual: Request): Boolean = try {
            scope(expected) == scope(actual) &&
                expected.header(NONCE_HEADER) == actual.header(NONCE_HEADER)
        } catch (_: IllegalArgumentException) {
            false
        }

        fun scope(request: Request): String = requestTranscript(
            request,
            includeNonce = false
        ).signingBytes().signingBytes().sha256().hex()

        fun unverifiedScope(request: Request): String? = try {
            requestTranscript(request, includeNonce = false, validateSigning = false)
                .signingBytes().signingBytes().sha256().hex()
        } catch (_: IllegalArgumentException) {
            null
        }

        @Suppress("CyclomaticComplexMethod") // Preserve the exact wire field order.
        private fun requestTranscript(
            request: Request,
            includeNonce: Boolean = true,
            validateSigning: Boolean = true
        ): Buffer {
            val url = request.url
            val nonce = if (validateSigning) single(request.headers, NONCE_HEADER) else ""
            if (validateSigning) {
                signingCheck(
                    request.method == "POST" && url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
                        url.fragment == null
                )
                signingCheck(single(request.headers, PREFIX + "signature-version") == "3")
                signingCheck(nonce.matches(Regex("[0-9a-f]{32}")))
                val capabilities = single(request.headers, "x-dd-feature-flags-capabilities")
                signingCheck(CAPABILITY in capabilities.split(',').map { it.trim() })
            }
            val body = Buffer()
            try {
                request.body?.writeTo(body)
            } catch (_: IOException) {
                signingFailure()
            }
            signingCheck(!validateSigning || body.size <= MAX_REQUEST_BYTES)
            val result = Buffer().writeUtf8("datadog.feature-flags.precomputed-assignments.v3").writeByte(0)
            val host = if (url.host.contains(':')) "[${url.host}]" else url.host
            val authority = host + if (url.port == 443) "" else ":${url.port}"
            listOf(
                request.method.uppercase(Locale.US),
                url.scheme,
                authority,
                url.encodedPath,
                url.encodedQuery?.let { "?$it" } ?: ""
            )
                .forEach { result.lp(it.toByteArray(Charsets.UTF_8)) }
            if (includeNonce) result.lp(hex(nonce))
            result.write(body.signingBytes().signingBytes().sha256())
            result.write(single(request.headers, "dd-client-token").toByteArray(Charsets.UTF_8).signingBytes().sha256())
            SEMANTIC_HEADERS.forEach { name ->
                val values = request.headers.values(name)
                signingCheck(values.size <= 1)
                result.writeByte(if (values.isEmpty()) 0 else 1)
                if (values.isNotEmpty()) result.lp(values[0].toByteArray(Charsets.UTF_8))
            }
            val authorization = request.headers.values("authorization")
            signingCheck(authorization.size <= 1)
            result.writeByte(if (authorization.isEmpty()) 0 else 1)
            if (authorization.isNotEmpty()) {
                result.write(
                    authorization[0].toByteArray(Charsets.UTF_8).signingBytes().sha256()
                )
            }
            return result
        }

        private fun Buffer.lp(bytes: ByteArray) {
            writeInt(bytes.size)
            write(bytes)
        }

        private fun single(headers: Headers, name: String): String {
            val values = headers.values(name)
            signingCheck(values.size == 1)
            return values[0]
        }

        private fun base64(value: String, max: Int): ByteArray {
            signingCheck(value.isNotEmpty() && value.length <= ((max + 2) / 3) * 4)
            val decoded = value.decodeBase64() ?: signingFailure()
            signingCheck(decoded.size <= max && decoded.base64() == value)
            return decoded.toByteArray()
        }

        private fun seconds(value: String): Long {
            signingCheck(value.matches(Regex("0|[1-9][0-9]{0,15}")))
            return (value.toLongOrNull() ?: signingFailure()).also { signingCheck(it <= 9007199254740991L) }
        }

        private fun strictSignature(bytes: ByteArray) {
            val values = SigningDer.read(bytes).sequence()
            signingCheck(values.size == 2)
            values.forEach {
                signingCheck(it.tag == 2 && it.value.isNotEmpty() && it.value[0].toInt() and 128 == 0)
                val start = if (it.value[0] == 0.toByte()) 1 else 0
                signingCheck(
                    it.value.size - start in 1..32 &&
                        it.value.any { byte -> byte != 0.toByte() }
                )
            }
        }

        private fun identifier(bytes: ByteArray, authority: Boolean): ByteArray? {
            val node = SigningDer.read(bytes)
            if (!authority) {
                signingCheck(node.tag == 4 && node.value.isNotEmpty())
                return node.value
            }
            val fields = node.sequence()
            signingCheck(fields.size <= 1)
            return fields.firstOrNull()?.also { signingCheck(it.tag == 0x80 && it.value.isNotEmpty()) }?.value
        }

        private fun hex(value: String): ByteArray = signingHex(value)
    }
}
