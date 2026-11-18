/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.PublicKey
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
        signingCheck(single(headers, PREFIX + "signature-version") == SIGNATURE_VERSION)
        val leafBytes = base64(single(headers, PREFIX + "signing-certificate"), 4096)
        val chainBytes = chain(headers)
        signingCheck(single(headers, PREFIX + "certificate-id") == leafBytes.signingBytes().sha256().hex())
        val issued = seconds(single(headers, PREFIX + "issued-at"))
        val expires = seconds(single(headers, PREFIX + "expires-at"))
        signingCheck(now >= 0 && issued <= now + 30 && expires > now && expires > issued && expires - issued <= 300)
        val leaf = certificate(leafBytes)
        // Index 0 issued the leaf; each next intermediate issued the previous one.
        val intermediates = chainBytes.map { certificate(it) }
        leaf.restrictLeaf()
        intermediates.forEachIndexed { index, intermediate -> intermediate.restrictIntermediate(index) }
        val last = intermediates[intermediates.size - 1]
        val anchor = ResponseSigningTrustRoots.forEndpoint(request.url, trustedRoots).asSequence().mapNotNull { root ->
            try {
                certificate(base64(root, 4096)).takeIf { parent ->
                    parent.covers(now, issued, expires) && parent.admitsChain(intermediates.size) &&
                        last.issuedBy(parent)
                }
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.firstOrNull()
        signingCheck(anchor != null && leaf.issuedBy(intermediates[0]))
        signingCheck(intermediates.zipWithNext().all { (child, parent) -> child.issuedBy(parent) })
        signingCheck(leaf.covers(now, issued, expires) && intermediates.all { it.covers(now, issued, expires) })
        val signature = base64(single(headers, PREFIX + "signature"), 72)
        strictSignature(signature)
        signingCheck(verifySignature(leaf.publicKey, transcript(request, status, headers, body), signature))
        return VerifiedResponse(scope(request), now, expires)
    }

    private class Certificate(
        val tbs: SigningDer,
        val signature: ByteArray,
        val publicKey: PublicKey,
        val notBefore: Long,
        val notAfter: Long,
        val extensions: Map<String, Pair<Boolean, ByteArray>>
    ) {
        fun covers(now: Long, issued: Long, expires: Long): Boolean {
            return now in notBefore..notAfter && issued >= notBefore && expires <= notAfter
        }

        @Suppress("ReturnCount") // Reject each issuer linkage before invoking native cryptography.
        fun issuedBy(parent: Certificate): Boolean {
            if (!tbs.children[3].encoded.contentEquals(parent.tbs.children[5].encoded) ||
                notBefore < parent.notBefore || notAfter > parent.notAfter
            ) {
                return false
            }
            val aki = extensions[AUTHORITY_KEY_ID]?.second?.let { identifier(it, true) }
            val ski = parent.extensions[SUBJECT_KEY_ID]?.second?.let { identifier(it, false) }
            if (aki != null && ski != null && !aki.contentEquals(ski)) return false
            return verifySignature(parent.publicKey, tbs.encoded, signature)
        }

        fun restrictLeaf() {
            val basic = extensions[BASIC_CONSTRAINTS] ?: signingFailure()
            val usage = extensions[KEY_USAGE] ?: signingFailure()
            signingCheck(basic.first && usage.first)
            signingCheck(basic.second.contentEquals(hex("3000")))
            signingCheck(usage.second.contentEquals(hex("03020780")))
            val eku = extendedKeyUsages(extensions[EXTENDED_KEY_USAGE]?.second ?: signingFailure())
            signingCheck(FFE_ASSIGNMENT_SIGNING_EKU in eku && ANY_EXTENDED_KEY_USAGE !in eku)
        }

        fun restrictIntermediate(index: Int) {
            val basic = extensions[BASIC_CONSTRAINTS] ?: signingFailure()
            val usage = extensions[KEY_USAGE] ?: signingFailure()
            signingCheck(basic.first && usage.first)
            val fields = SigningDer.read(basic.second).sequence()
            signingCheck(fields.size == 2 && fields[0].encoded.contentEquals(hex("0101ff")))
            signingCheck(pathLength(fields[1]) >= index)
            signingCheck(usage.second.contentEquals(hex("03020106")))
            val eku = extensions[EXTENDED_KEY_USAGE]?.second?.let { extendedKeyUsages(it) }
            signingCheck(eku == null || FFE_ASSIGNMENT_SIGNING_EKU in eku || ANY_EXTENDED_KEY_USAGE in eku)
        }

        /** A pinned root may omit EKU and path length; when present, they must admit this chain. */
        fun admitsChain(count: Int): Boolean {
            val length = extensions[BASIC_CONSTRAINTS]?.second
                ?.let { basic -> SigningDer.read(basic).sequence().firstOrNull { it.tag == 2 } }
            val eku = extensions[EXTENDED_KEY_USAGE]?.second?.let { extendedKeyUsages(it) }
            return (length == null || pathLength(length) >= count) &&
                (eku == null || FFE_ASSIGNMENT_SIGNING_EKU in eku || ANY_EXTENDED_KEY_USAGE in eku)
        }
    }

    // Explicit checks preserve the restricted certificate profile.
    @Suppress("CyclomaticComplexMethod", "StringLiteralDuplication")
    private fun certificate(bytes: ByteArray): Certificate {
        val cert = SigningDer.read(bytes).sequence()
        signingCheck(cert.size == 3)
        val tbs = cert[0].sequence()
        signingCheck(tbs.size == 8 && tbs[0].encoded.contentEquals(hex("a003020102")))
        signingCheck(
            tbs[1].tag == 2 && tbs[1].value.size in 1..20 && tbs[1].value[0].toInt() and 128 == 0 &&
                tbs[1].value.any { it != 0.toByte() }
        )
        validateName(tbs[3])
        validateName(tbs[5])
        val validity = tbs[4].sequence()
        signingCheck(validity.size == 2)
        val notBefore = signingTime(validity[0])
        val notAfter = signingTime(validity[1])
        signingCheck(notBefore < notAfter)
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
            signingCheck(oid !in setOf("551d1e", "551d21", "551d24", "551d36"))
            val value = fields[fields.size - 1].value
            SigningDer.read(value)
            if (oid == "551d0e") identifier(value, false)
            if (oid == "551d23") identifier(value, true)
            extensions[oid] = critical to value
        }
        return Certificate(
            cert[0],
            signingSlice(cert[2].value, 1, cert[2].value.size),
            signingPublicKey(tbs[6].encoded),
            notBefore,
            notAfter,
            extensions
        )
    }

    private fun validateName(name: SigningDer) {
        name.sequence().forEach { rdn ->
            signingCheck(rdn.tag == 0x31 && rdn.children.isNotEmpty())
            rdn.children.forEach { attribute ->
                val pair = attribute.sequence()
                signingCheck(pair.size == 2 && pair[0].tag == 6)
            }
        }
    }

    @Suppress("TooManyFunctions") // Protocol encoding and cache identity helpers stay together.
    companion object {
        const val PREFIX = "x-datadog-feature-flags-"
        private const val NONCE_HEADER = PREFIX + "request-nonce"
        const val CAPABILITY = "response-signing-v1"
        const val SIGNATURE_VERSION = "1"
        private const val DOMAIN = "datadog.feature-flags.precomputed-assignments.v1"
        private const val MAX_INTERMEDIATES = 3
        private const val MAX_CERTIFICATE_BYTES = 4096
        private const val BASIC_CONSTRAINTS = "551d13"
        private const val KEY_USAGE = "551d0f"
        private const val EXTENDED_KEY_USAGE = "551d25"

        // PLACEHOLDER: identity-core must assign the production FFE assignment-signing EKU OID before merge.
        // OID contents (without tag and length) of the placeholder 1.3.6.1.4.1.99999.1.1.
        private const val FFE_ASSIGNMENT_SIGNING_EKU = "2b06010401868d1f0101"

        // OID contents (without tag and length) of anyExtendedKeyUsage 2.5.29.37.0.
        private const val ANY_EXTENDED_KEY_USAGE = "551d2500"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val AUTHORITY_KEY_ID = "551d23"
        private const val SUBJECT_KEY_ID = "551d0e"
        private const val MAX_REQUEST_BYTES = 1024 * 1024
        private const val HTTPS_PORT = 443
        private const val HTTP_PORT = 80
        private val SEMANTIC_HEADERS =
            listOf("content-type", "dd-application-id", "x-rkyv", "x-use-cache", "x-dd-feature-flags-capabilities")

        fun transcript(request: Request, status: Int, headers: Headers, body: ByteArray): ByteArray {
            val issued = seconds(single(headers, PREFIX + "issued-at"))
            val expires = seconds(single(headers, PREFIX + "expires-at"))
            val leaf = base64(single(headers, PREFIX + "signing-certificate"), MAX_CERTIFICATE_BYTES)
            val intermediates = chain(headers)
            return requestTranscript(request).apply {
                writeShort(status).writeLong(issued).writeLong(expires).writeLong(body.size.toLong())
                write(body.signingBytes().sha256())
                write(leaf.signingBytes().sha256())
                writeByte(intermediates.size)
                intermediates.forEach { write(it.signingBytes().sha256()) }
            }.signingBytes()
        }

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
                signingCheck(single(request.headers, PREFIX + "signature-version") == SIGNATURE_VERSION)
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
            val result = Buffer().writeUtf8(DOMAIN).writeByte(0)
            listOf(
                request.method.uppercase(Locale.US),
                url.scheme,
                authority(url),
                url.encodedPath,
                url.encodedQuery?.let { "?$it" } ?: ""
            )
                .forEach { result.lp(it.toByteArray(Charsets.UTF_8)) }
            if (includeNonce) result.lp(hex(nonce))
            val requestBytes = body.signingBytes()
            val scopedBytes = if (includeNonce) requestBytes else withoutDiagnostics(requestBytes)
            result.write(scopedBytes.signingBytes().sha256())
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

        /** Lowercase host, IPv6 in brackets, and the port only when it is not the default port of the scheme. */
        fun authority(url: HttpUrl): String {
            val host = if (url.host.contains(':')) "[${url.host}]" else url.host
            val defaultPort = if (url.isHttps) HTTPS_PORT else HTTP_PORT
            return host + if (url.port == defaultPort) "" else ":${url.port}"
        }

        // Diagnostics identify a fetch, not an assignment scope. Signature verification still hashes every wire byte.
        private fun withoutDiagnostics(body: ByteArray): ByteArray = try {
            val json = JsonParser.parseString(body.toString(Charsets.UTF_8))
            if (json.isJsonObject && json.asJsonObject.has("diagnostics")) {
                json.asJsonObject.remove("diagnostics")
                json.toString().toByteArray(Charsets.UTF_8)
            } else {
                body
            }
        } catch (_: JsonParseException) {
            body
        }

        private fun Buffer.lp(bytes: ByteArray) {
            writeInt(bytes.size)
            write(bytes)
        }

        /** Intermediates in header order: 1 to 3 strict base64 DER entries joined by commas without spaces. */
        private fun chain(headers: Headers): List<ByteArray> {
            val value = single(headers, PREFIX + "signing-chain")
            val maxEntry = ((MAX_CERTIFICATE_BYTES + 2) / 3) * 4
            signingCheck(value.length <= MAX_INTERMEDIATES * (maxEntry + 1))
            val entries = value.split(',')
            signingCheck(entries.size in 1..MAX_INTERMEDIATES)
            return entries.map { base64(it, MAX_CERTIFICATE_BYTES) }
        }

        // RFC 5280 requires at least one purpose; other purposes stay allowed.
        private fun extendedKeyUsages(bytes: ByteArray): Set<String> {
            val purposes = SigningDer.read(bytes).sequence()
            signingCheck(purposes.isNotEmpty())
            return purposes.map {
                signingCheck(it.tag == 6)
                it.value.signingBytes().hex()
            }.toSet()
        }

        // Decode a non-negative DER INTEGER; values beyond Long saturate because they admit any chain depth.
        private fun pathLength(node: SigningDer): Long {
            signingCheck(node.tag == 2 && node.value[0].toInt() and 128 == 0)
            if (node.value.size > 8) return Long.MAX_VALUE
            var value = 0L
            for (byte in node.value) value = (value shl 8) or (byte.toInt() and 255).toLong()
            return value
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
            signingCheck(fields.size == 1)
            return fields.firstOrNull()?.also { signingCheck(it.tag == 0x80 && it.value.isNotEmpty()) }?.value
        }

        private fun hex(value: String): ByteArray = signingHex(value)
    }
}
