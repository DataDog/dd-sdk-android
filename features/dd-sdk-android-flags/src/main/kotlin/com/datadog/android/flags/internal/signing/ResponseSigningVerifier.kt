/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.signing

import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningFailureReason
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.ProviderException
import java.security.PublicKey
import java.util.Locale

internal data class VerifiedResponse(val scope: String, val verifiedAt: Long, val expiresAt: Long)

/**
 * Native JCA verifies signatures; the pinned anchor never enters a system trust/path builder.
 * The verifier keeps no leaf or intermediate identity between responses, so every valid chain under an anchor passes.
 *
 * @param anchors pinned roots as padded base64 DER; production uses only [ResponseSigningTrustRoots.STAGING_ROOT]
 * @param minimumNotBefore draft revocation floor per anchor, raised only by fully verified responses
 * @param report receives [REVOKED_CERTIFICATE] and [MINIMUM_NOT_BEFORE_IGNORED]
 */
@Suppress("MagicNumber") // Time windows, DER OIDs and integer widths are frozen protocol constants.
internal class ResponseSigningVerifier(
    anchors: List<String>,
    private val minimumNotBefore: MinimumNotBeforeStore = InMemoryMinimumNotBeforeStore(),
    private val report: (String) -> Unit = {}
) {
    private val anchors = anchors.toList()

    /** The proof, or the reason that verification failed; exactly one is set. */
    class Verification(val proof: VerifiedResponse?, val failure: ResponseSigningFailureReason?)

    fun verify(request: Request, status: Int, headers: Headers, body: ByteArray, now: Long): VerifiedResponse? =
        verification(request, status, headers, body, now).proof

    fun verification(request: Request, status: Int, headers: Headers, body: ByteArray, now: Long): Verification =
        try {
            Verification(verifyProof(request, status, headers, body, now), null)
        } catch (exception: SigningCheckException) {
            Verification(null, exception.reason)
        } catch (_: NoSuchAlgorithmException) {
            Verification(null, ResponseSigningFailureReason.UNAVAILABLE_CRYPTO)
        } catch (_: ProviderException) {
            Verification(null, ResponseSigningFailureReason.UNAVAILABLE_CRYPTO)
        } catch (_: GeneralSecurityException) {
            Verification(null, ResponseSigningFailureReason.INVALID_CERTIFICATE)
        } catch (_: IllegalArgumentException) {
            Verification(null, ResponseSigningFailureReason.INVALID_PROOF)
        } catch (_: IOException) {
            Verification(null, ResponseSigningFailureReason.INVALID_PROOF)
        }

    private fun verifyProof(
        request: Request,
        status: Int,
        headers: Headers,
        body: ByteArray,
        now: Long
    ): VerifiedResponse {
        signingCheck(anchors.isNotEmpty(), ResponseSigningFailureReason.MISSING_ANCHOR)
        signingCheck(
            headers.toMultimap().keys.any { it.startsWith(PREFIX) },
            ResponseSigningFailureReason.MISSING_PROOF
        )
        signingCheck(body.size <= MAX_RESPONSE_BYTES, ResponseSigningFailureReason.RESPONSE_TOO_LARGE)
        signingCheck(status == 200)
        signingCheck(single(headers, PREFIX + "signature-version") == SIGNATURE_VERSION)
        val leafBytes = base64(single(headers, PREFIX + "signing-certificate"), 4096)
        val chainBytes = chain(headers)
        signingCheck(single(headers, PREFIX + "certificate-id") == leafBytes.signingBytes().sha256().hex())
        val issued = seconds(single(headers, PREFIX + "issued-at"))
        val expires = seconds(single(headers, PREFIX + "expires-at"))
        val announcedMinimum = minimumNotBefore(headers)
        signingCheck(expires > issued && expires - issued <= 300)
        signingCheck(now >= 0 && issued <= now + 30 && expires > now, ResponseSigningFailureReason.EXPIRED_PROOF)
        val chain = chainCheck { verifiedChain(leafBytes, chainBytes, now, issued, expires) }
        val signature = base64(single(headers, PREFIX + "signature"), 72)
        strictSignature(signature)
        signingCheck(
            verifySignature(chain.leaf.publicKey, transcript(request, status, headers, body), signature),
            ResponseSigningFailureReason.INVALID_SIGNATURE
        )
        val chainNotBefore = chain.intermediates.fold(chain.leaf.notBefore) { oldest, certificate ->
            minOf(oldest, certificate.notBefore)
        }
        applyMinimumNotBefore(chain.anchorId, chainNotBefore, announcedMinimum)
        return VerifiedResponse(scope(request), now, expires)
    }

    private class VerifiedChain(val leaf: Certificate, val intermediates: List<Certificate>, val anchorId: String)

    /** Any parse, profile, path or validity failure of the chain is [ResponseSigningFailureReason.INVALID_CERTIFICATE]. */
    private fun chainCheck(block: () -> VerifiedChain): VerifiedChain = try {
        block()
    } catch (exception: SigningCheckException) {
        signingFailure(
            if (exception.reason == ResponseSigningFailureReason.INVALID_PROOF) {
                ResponseSigningFailureReason.INVALID_CERTIFICATE
            } else {
                exception.reason
            },
            exception
        )
    }

    private fun verifiedChain(
        leafBytes: ByteArray,
        chainBytes: List<ByteArray>,
        now: Long,
        issued: Long,
        expires: Long
    ): VerifiedChain {
        val leaf = certificate(leafBytes)
        // Index 0 issued the leaf; each next intermediate issued the previous one.
        val intermediates = chainBytes.map { certificate(it) }
        leaf.restrictLeaf()
        intermediates.forEachIndexed { index, intermediate -> intermediate.restrictIntermediate(index) }
        val last = intermediates[intermediates.size - 1]
        val anchor = anchors.asSequence().mapNotNull { root ->
            try {
                val rootBytes = base64(root, 4096)
                certificate(rootBytes).takeIf { parent ->
                    parent.covers(now, issued, expires) && parent.admitsChain(intermediates.size) &&
                        last.issuedBy(parent)
                }?.let { rootBytes.signingBytes().sha256().hex() }
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.firstOrNull()
        signingCheck(anchor != null && leaf.issuedBy(intermediates[0]))
        signingCheck(intermediates.zipWithNext().all { (child, parent) -> child.issuedBy(parent) })
        signingCheck(leaf.covers(now, issued, expires) && intermediates.all { it.covers(now, issued, expires) })
        return VerifiedChain(leaf, intermediates, anchor ?: signingFailure())
    }

    // Draft revocation: runs only after the signature, so only an authentic chain can raise or meet the floor.
    private fun applyMinimumNotBefore(anchorId: String, chainNotBefore: Long, announced: Long?) {
        val floor = minimumNotBefore.get(anchorId)
        if (floor != null && chainNotBefore < floor) {
            report(REVOKED_CERTIFICATE)
            signingFailure(ResponseSigningFailureReason.REVOKED_CERTIFICATE)
        }
        if (announced == null) return
        // A response must never revoke a certificate of its own chain.
        if (announced > chainNotBefore) {
            report(MINIMUM_NOT_BEFORE_IGNORED)
        } else {
            minimumNotBefore.raise(anchorId, announced)
        }
    }

    private class Certificate(
        val tbs: SigningDer,
        val signature: ByteArray,
        val publicKey: PublicKey,
        val notBefore: Long,
        val notAfter: Long,
        val extensions: Map<String, Pair<Boolean, ByteArray>>
    ) {
        /** `notBefore <= now < notAfter`; the proof window must also fit inside the certificate validity. */
        fun covers(now: Long, issued: Long, expires: Long): Boolean {
            return notBefore <= now && now < notAfter && issued >= notBefore && expires <= notAfter
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
        signingCheck(notBefore < notAfter && notAfter <= MAX_SAFE_INTEGER)
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
        private const val MINIMUM_NOT_BEFORE_HEADER = PREFIX + "minimum-not-before"
        const val CAPABILITY = "response-signing-v1"
        const val SIGNATURE_VERSION = "1"
        private const val DOMAIN = "datadog.feature-flags.precomputed-assignments.v1"
        private const val MAX_INTERMEDIATES = 3
        private const val MAX_CERTIFICATE_BYTES = 4096
        private const val BASIC_CONSTRAINTS = "551d13"
        private const val KEY_USAGE = "551d0f"
        private const val EXTENDED_KEY_USAGE = "551d25"

        // PLACEHOLDER: Datadog must assign the production FFE assignment-signing EKU OID before merge.
        // OID contents (without tag and length) of the placeholder 1.3.6.1.4.1.99999.1.1.
        private const val FFE_ASSIGNMENT_SIGNING_EKU = "2b06010401868d1f0101"

        // OID contents (without tag and length) of anyExtendedKeyUsage 2.5.29.37.0.
        private const val ANY_EXTENDED_KEY_USAGE = "551d2500"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val REVOKED_CERTIFICATE = "revoked-certificate"
        const val MINIMUM_NOT_BEFORE_IGNORED = "minimum-not-before-ignored"
        private const val MAX_SAFE_INTEGER = 9007199254740991L
        private const val AUTHORITY_KEY_ID = "551d23"
        private const val SUBJECT_KEY_ID = "551d0e"
        private const val MAX_REQUEST_BYTES = 1024 * 1024
        private const val HTTPS_PORT = 443
        private const val HTTP_PORT = 80
        private const val APPLICATION_ID_HEADER = "dd-application-id"
        private val SEMANTIC_HEADERS =
            listOf("content-type", APPLICATION_ID_HEADER, "x-rkyv", "x-use-cache", "x-dd-feature-flags-capabilities")

        fun transcript(request: Request, status: Int, headers: Headers, body: ByteArray): ByteArray {
            val minimum = minimumNotBefore(headers)
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
                // Draft: the edge binds the revocation floor only when it sends one, so v1 stays unchanged.
                minimum?.let { writeByte(1).writeLong(it) }
            }.signingBytes()
        }

        fun matchesRequest(expected: Request, actual: Request): Boolean = try {
            requestTranscript(expected, includeNonce = false).signingBytes()
                .contentEquals(requestTranscript(actual, includeNonce = false).signingBytes()) &&
                expected.header(NONCE_HEADER) == actual.header(NONCE_HEADER)
        } catch (_: IllegalArgumentException) {
            false
        }

        /** Cache and admission scope; omits the RUM application ID, which each transcript still binds. */
        fun scope(request: Request): String = requestTranscript(
            request,
            includeNonce = false,
            includeApplicationId = false
        ).signingBytes().signingBytes().sha256().hex()

        fun unverifiedScope(request: Request): String? = try {
            requestTranscript(request, includeNonce = false, validateSigning = false, includeApplicationId = false)
                .signingBytes().signingBytes().sha256().hex()
        } catch (_: IllegalArgumentException) {
            null
        }

        @Suppress("CyclomaticComplexMethod") // Preserve the exact wire field order.
        private fun requestTranscript(
            request: Request,
            includeNonce: Boolean = true,
            validateSigning: Boolean = true,
            includeApplicationId: Boolean = true
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
            SEMANTIC_HEADERS.filter { includeApplicationId || it != APPLICATION_ID_HEADER }.forEach { name ->
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

        /** Draft `minimum-not-before`: absent, or exactly one canonical unix-seconds value. */
        private fun minimumNotBefore(headers: Headers): Long? {
            val values = headers.values(MINIMUM_NOT_BEFORE_HEADER)
            signingCheck(values.size <= 1)
            return values.firstOrNull()?.let { seconds(it) }
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
            return (value.toLongOrNull() ?: signingFailure()).also { signingCheck(it <= MAX_SAFE_INTEGER) }
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
