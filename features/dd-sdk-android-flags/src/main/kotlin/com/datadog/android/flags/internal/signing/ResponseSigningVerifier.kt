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

/**
 * [responseHeaders] holds each name of the signed response list, in list order, with its value or null when absent.
 * [verifiedAt] is the device wall clock at verification, [issuedAt] and [expiresAt] the signed proof window, and
 * [verifiedEffectiveAt] the later of issued-at and the verifier clock at verification, all in unix seconds.
 * [verifiedElapsedMs] is the boot-based elapsed time at verification, in milliseconds.
 */
internal data class VerifiedResponse(
    val scope: String,
    val verifiedAt: Long,
    val issuedAt: Long,
    val expiresAt: Long,
    val responseHeaders: Map<String, String?>,
    val anchorId: String,
    val verifiedEffectiveAt: Long,
    val verifiedElapsedMs: Long
)

/**
 * Native JCA verifies signatures; the pinned anchor never enters a system trust/path builder.
 * The verifier keeps no leaf or intermediate identity between responses, so every valid chain under an anchor passes.
 *
 * @param anchors pinned roots as padded base64 DER; production uses only [ResponseSigningTrustRoots.STAGING_ROOT]
 * @param minimumNotBefore draft revocation floor and issued-at high-water mark per anchor, raised only by fully
 * verified responses
 * @param buildFloor unix seconds below which the verifier never moves its clock; production passes [BUILD_FLOOR], and
 * 0 turns it off for fixtures that run at older times
 * @param report receives [REVOKED_CERTIFICATE] and [MINIMUM_NOT_BEFORE_IGNORED]
 */
@Suppress("MagicNumber") // Time windows, DER OIDs and integer widths are frozen protocol constants.
internal class ResponseSigningVerifier(
    anchors: List<String>,
    private val minimumNotBefore: MinimumNotBeforeStore = InMemoryMinimumNotBeforeStore(),
    private val buildFloor: Long = 0L,
    private val report: (String) -> Unit = {}
) {
    private val anchors = anchors.toList()

    // An anchor that is not valid base64 never verifies a chain, so it has no clock.
    private val anchorIds = this.anchors.mapNotNull { root ->
        try {
            base64(root, 4096).signingBytes().sha256().hex()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** The proof, or the reason that verification failed; exactly one is set. */
    class Verification(val proof: VerifiedResponse?, val failure: ResponseSigningFailureReason?)

    fun verify(request: Request, status: Int, headers: Headers, body: ByteArray, now: Long): VerifiedResponse? =
        verification(request, status, headers, body, now).proof

    /** [elapsedMs] is the boot-based elapsed time that the proof records for its expiry; see [VerifiedResponse]. */
    fun verification(
        request: Request,
        status: Int,
        headers: Headers,
        body: ByteArray,
        now: Long,
        elapsedMs: Long = 0L
    ): Verification =
        try {
            Verification(verifyProof(request, status, headers, body, now, elapsedMs), null)
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
        now: Long,
        elapsedMs: Long
    ): VerifiedResponse {
        signingCheck(anchors.isNotEmpty(), ResponseSigningFailureReason.MISSING_ANCHOR)
        // Any absent proof header, including the chain header, is a missing proof.
        val names = headers.toMultimap().keys
        signingCheck(PROOF_HEADERS.all { it in names }, ResponseSigningFailureReason.MISSING_PROOF)
        signingCheck(body.size <= MAX_RESPONSE_BYTES, ResponseSigningFailureReason.RESPONSE_TOO_LARGE)
        signingCheck(status == 200)
        signingCheck(single(headers, SIGNATURE_VERSION_HEADER) == SIGNATURE_VERSION)
        val leafBytes = base64(single(headers, SIGNING_CERTIFICATE_HEADER), 4096)
        val chainBytes = chain(headers)
        signingCheck(single(headers, CERTIFICATE_ID_HEADER) == leafBytes.signingBytes().sha256().hex())
        val issued = seconds(single(headers, ISSUED_AT_HEADER))
        val expires = seconds(single(headers, EXPIRES_AT_HEADER))
        val announcedMinimum = minimumNotBefore(headers)
        val responseHeaders = signedResponseHeaders(request, headers)
        // The anchor is known only after the chain, which checks the window again at that anchor's clock. Here the
        // clock is only bounded: at least the build floor, at most the highest high-water mark of any anchor.
        val lowestNow = maxOf(now, buildFloor)
        val highestNow = anchorIds.map { effectiveNow(it, now) }.maxOrNull() ?: lowestNow
        signingCheck(
            now >= 0 && expires > issued && expires - issued <= 300 && issued <= highestNow + 30 &&
                expires > lowestNow,
            ResponseSigningFailureReason.EXPIRED_PROOF
        )
        val chain = chainCheck { verifiedChain(leafBytes, chainBytes, now, issued, expires) }
        val signature = base64(single(headers, SIGNATURE_HEADER), 72)
        strictSignature(signature)
        val transcript = requestCheck { transcript(request, status, headers, body) }
        signingCheck(
            verifySignature(chain.leaf.publicKey, transcript, signature),
            ResponseSigningFailureReason.INVALID_SIGNATURE
        )
        applyMinimumLeafNotBefore(chain.anchorId, chain.leaf.notBefore, announcedMinimum)
        // The raw device clock caps the mark, so one response moves it at most 30 s past the device clock.
        minimumNotBefore.raiseHighWaterMark(chain.anchorId, minOf(issued, now + 30))
        return VerifiedResponse(
            scope = scope(request),
            verifiedAt = now,
            issuedAt = issued,
            expiresAt = expires,
            responseHeaders = responseHeaders,
            anchorId = chain.anchorId,
            verifiedEffectiveAt = maxOf(chain.verifiedAt, issued),
            verifiedElapsedMs = elapsedMs
        )
    }

    /**
     * The clock that every time check uses: the device clock, raised to [buildFloor] and to the highest issued-at of a
     * verified response under [anchorId]. A device clock set back cannot make an expired leaf or an old proof valid.
     */
    fun effectiveNow(anchorId: String, now: Long): Long =
        maxOf(maxOf(now, buildFloor), minimumNotBefore.highWaterMark(anchorId) ?: 0L)

    private fun inWindow(at: Long, issued: Long, expires: Long): Boolean = issued <= at + 30 && expires > at

    // The response headers are already valid here, so a transcript failure comes from the request shape.
    private fun requestCheck(block: () -> ByteArray): ByteArray = try {
        block()
    } catch (exception: SigningCheckException) {
        signingFailure(ResponseSigningFailureReason.UNSUPPORTED_REQUEST, exception)
    }

    /** [verifiedAt] is the anchor clock ([effectiveNow]) that the chain was checked at. */
    private class VerifiedChain(
        val leaf: Certificate,
        val intermediates: List<Certificate>,
        val anchorId: String,
        val verifiedAt: Long
    )

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
                val rootId = rootBytes.signingBytes().sha256().hex()
                certificate(rootBytes).takeIf { parent ->
                    parent.covers(effectiveNow(rootId, now), issued, expires) &&
                        parent.admitsChain(intermediates.size) && last.issuedBy(parent)
                }?.let { rootId }
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.firstOrNull()
        signingCheck(anchor != null && leaf.issuedBy(intermediates[0]))
        signingCheck(intermediates.zipWithNext().all { (child, parent) -> child.issuedBy(parent) })
        val anchorId = anchor ?: signingFailure()
        val at = effectiveNow(anchorId, now)
        signingCheck(
            inWindow(at, issued, expires) && leaf.covers(at, issued, expires) &&
                intermediates.all { it.covers(at, issued, expires) },
            ResponseSigningFailureReason.EXPIRED_PROOF
        )
        return VerifiedChain(leaf, intermediates, anchorId, at)
    }

    // Draft leaf-only revocation: runs only after the signature, so only an authentic chain can raise or meet the
    // floor. Intermediates are never compared with the floor; a long-lived intermediate must not cap or revoke.
    private fun applyMinimumLeafNotBefore(anchorId: String, leafNotBefore: Long, announced: Long?) {
        val floor = minimumNotBefore.get(anchorId)
        if (floor != null && leafNotBefore < floor) {
            report(REVOKED_CERTIFICATE)
            signingFailure(ResponseSigningFailureReason.REVOKED_CERTIFICATE)
        }
        if (announced == null) return
        // A response must never revoke its own leaf.
        if (announced > leafNotBefore) {
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

        /**
         * Release build floor (unix seconds, 2026-11-25T00:00:00Z). Each release sets it to its date or shortly before,
         * never later than the release build. A leaf that expired before the release never verifies again.
         */
        const val BUILD_FLOOR = 1795564800L
        private const val NONCE_HEADER = PREFIX + "request-nonce"
        private const val SIGNATURE_VERSION_HEADER = PREFIX + "signature-version"
        private const val SIGNING_CERTIFICATE_HEADER = PREFIX + "signing-certificate"
        private const val SIGNING_CHAIN_HEADER = PREFIX + "signing-chain"
        private const val CERTIFICATE_ID_HEADER = PREFIX + "certificate-id"
        private const val ISSUED_AT_HEADER = PREFIX + "issued-at"
        private const val EXPIRES_AT_HEADER = PREFIX + "expires-at"
        private const val SIGNATURE_HEADER = PREFIX + "signature"
        const val SIGNED_HEADERS_HEADER = PREFIX + "signed-headers"
        private const val SIGNED_RESPONSE_HEADERS_HEADER = PREFIX + "signed-response-headers"
        private val PROOF_HEADERS = listOf(
            SIGNATURE_VERSION_HEADER,
            SIGNING_CERTIFICATE_HEADER,
            SIGNING_CHAIN_HEADER,
            CERTIFICATE_ID_HEADER,
            ISSUED_AT_HEADER,
            EXPIRES_AT_HEADER,
            SIGNATURE_HEADER,
            SIGNED_RESPONSE_HEADERS_HEADER
        )
        private const val MINIMUM_LEAF_NOT_BEFORE_HEADER = PREFIX + "minimum-leaf-not-before"
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
        private const val CONTENT_TYPE_HEADER = "content-type"
        private const val CAPABILITIES_HEADER = "x-dd-feature-flags-capabilities"
        const val FLAG_SELECTION_IGNORED_HEADER = "dd-flag-selection-ignored"

        /** Request headers that can change a signed response; each one that is present must be in the signed list. */
        val BOUND_HEADERS = listOf(CONTENT_TYPE_HEADER, APPLICATION_ID_HEADER, CAPABILITIES_HEADER, "x-rkyv")

        // The staging-only x-use-cache is not bound unless listed, but it still separates cache scopes.
        private val SCOPE_HEADERS =
            listOf(CONTENT_TYPE_HEADER, APPLICATION_ID_HEADER, CAPABILITIES_HEADER, "x-rkyv", "x-use-cache")
        private val NAME_LIST = Regex("[0-9a-z!#$%&'*+.^_`|~-]+(,[0-9a-z!#$%&'*+.^_`|~-]+)*")
        private const val MAX_LISTED_NAMES = 32
        private const val MAX_LISTED_NAME_BYTES = 64

        fun transcript(request: Request, status: Int, headers: Headers, body: ByteArray): ByteArray {
            val minimum = minimumNotBefore(headers)
            val issued = seconds(single(headers, ISSUED_AT_HEADER))
            val expires = seconds(single(headers, EXPIRES_AT_HEADER))
            val leaf = base64(single(headers, SIGNING_CERTIFICATE_HEADER), MAX_CERTIFICATE_BYTES)
            val intermediates = chain(headers)
            return requestTranscript(request).apply {
                writeShort(status).writeLong(issued).writeLong(expires).writeLong(body.size.toLong())
                write(body.signingBytes().sha256())
                write(leaf.signingBytes().sha256())
                writeByte(intermediates.size)
                intermediates.forEach { write(it.signingBytes().sha256()) }
                headerList(signedResponseHeaders(request, headers))
                // Draft: the edge binds the revocation floor only when it sends one, so v1 stays unchanged.
                minimum?.let { writeByte(1).writeLong(it) }
            }.signingBytes()
        }

        fun matchesRequest(expected: Request, actual: Request): Boolean = try {
            requestTranscript(expected, includeNonce = false).signingBytes()
                .contentEquals(requestTranscript(actual, includeNonce = false).signingBytes()) &&
                expected.header(NONCE_HEADER) == actual.header(NONCE_HEADER) &&
                expected.headers.values(SIGNED_HEADERS_HEADER) == actual.headers.values(SIGNED_HEADERS_HEADER)
        } catch (_: IllegalArgumentException) {
            false
        }

        /**
         * Cache and admission scope; omits the RUM application ID, which each transcript still binds. The scope encodes
         * the [SCOPE_HEADERS] that are present, not the signed list, so a verified and an unverified request share it.
         */
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
                signingCheck(single(request.headers, SIGNATURE_VERSION_HEADER) == SIGNATURE_VERSION)
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
            if (includeNonce) {
                result.headerList(signedRequestHeaders(request))
            } else {
                // The scope keeps the list rules, so prepare() and the transport match fail as the transcript does.
                if (validateSigning) signedRequestHeaders(request)
                SCOPE_HEADERS.filter { includeApplicationId || it != APPLICATION_ID_HEADER }.forEach { name ->
                    val values = request.headers.values(name)
                    signingCheck(values.size <= 1)
                    result.writeByte(if (values.isEmpty()) 0 else 1)
                    if (values.isNotEmpty()) result.lp(values[0].toByteArray(Charsets.UTF_8))
                }
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

        /** `u8(count)`, then `lp(name) u8(1) lp(value)` for a present header or `lp(name) u8(0) lp("")` when absent. */
        private fun Buffer.headerList(entries: Map<String, String?>) {
            writeByte(entries.size)
            entries.forEach { (name, value) ->
                lp(name.toByteArray(Charsets.UTF_8))
                writeByte(if (value == null) 0 else 1)
                lp((value ?: "").toByteArray(Charsets.UTF_8))
            }
        }

        /** Lowercase tokens in ascending byte order, unique, joined by commas without spaces, 1 to 32 names. */
        private fun nameList(value: String): List<String> {
            signingCheck(value.matches(NAME_LIST))
            val names = value.split(',')
            signingCheck(names.size <= MAX_LISTED_NAMES)
            signingCheck(names.all { it.length <= MAX_LISTED_NAME_BYTES })
            signingCheck(names.zipWithNext().all { (previous, next) -> previous < next })
            return names
        }

        /** The signed request list with each value; every listed header must be present on exactly one line. */
        private fun signedRequestHeaders(request: Request): Map<String, String?> {
            val names = nameList(single(request.headers, SIGNED_HEADERS_HEADER))
            signingCheck(CONTENT_TYPE_HEADER in names && CAPABILITIES_HEADER in names)
            signingCheck(
                !names.any { it == "dd-client-token" || it == "authorization" || it.startsWith(PREFIX) }
            )
            signingCheck(BOUND_HEADERS.all { it in names || request.headers.values(it).isEmpty() })
            return names.associateWith { name ->
                val value = single(request.headers, name)
                // Only the capabilities header is a comma-separated list; a comma elsewhere means combined lines.
                signingCheck(name == CAPABILITIES_HEADER || ',' !in value)
                value
            }
        }

        /** The signed response list with each value or null; any list or name failure is an invalid proof. */
        private fun signedResponseHeaders(request: Request, headers: Headers): Map<String, String?> {
            val names = nameList(single(headers, SIGNED_RESPONSE_HEADERS_HEADER))
            signingCheck(CONTENT_TYPE_HEADER in names)
            signingCheck(!hasFlagSelection(request) || FLAG_SELECTION_IGNORED_HEADER in names)
            signingCheck(!names.any { it in PROOF_HEADERS })
            return names.associateWith { name ->
                val values = headers.values(name)
                signingCheck(values.size <= 1)
                values.firstOrNull()
            }
        }

        /**
         * A non-null `data.attributes.flag_selection` in the JSON:API body, as the edge decides. A top-level
         * `flag_selection` and the URL query are not a selection.
         */
        private fun hasFlagSelection(request: Request): Boolean {
            val body = Buffer()
            try {
                request.body?.writeTo(body)
            } catch (_: IOException) {
                signingFailure()
            }
            return try {
                val root = JsonParser.parseString(body.signingBytes().toString(Charsets.UTF_8))
                    .takeIf { it.isJsonObject }?.asJsonObject
                val attributes = root?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("attributes")?.takeIf { it.isJsonObject }?.asJsonObject
                attributes?.get("flag_selection")?.isJsonNull == false
            } catch (_: JsonParseException) {
                false
            }
        }

        /** Intermediates in header order: 1 to 3 strict base64 DER entries joined by commas without spaces. */
        private fun chain(headers: Headers): List<ByteArray> {
            val value = single(headers, SIGNING_CHAIN_HEADER)
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

        /** Draft `minimum-leaf-not-before`: absent, or exactly one canonical unix-seconds value. */
        private fun minimumNotBefore(headers: Headers): Long? {
            val values = headers.values(MINIMUM_LEAF_NOT_BEFORE_HEADER)
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
