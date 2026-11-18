/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Response signing v1 with a certificate chain (1 to 3 intermediates in `x-datadog-feature-flags-signing-chain`).
 * The test signs synthetic responses with an independent transcript encoder, so it does not reuse SDK encoding.
 *
 * `response-signing-chain/` holds test-only credentials from a local Vault dev server. Their keys sign nothing
 * outside tests. The test clock follows each leaf, so the 72-hour leaf validity does not expire the test.
 */
internal class ResponseSigningChainTest {

    // region Vault fixtures

    @TestFactory
    fun `M match expected results W verify() {Vault-issued fixtures}`(): List<DynamicTest> = listOf(
        Triple("lukas-valid", listOf("root-a"), true),
        Triple("lukas-backup-root", listOf("root-b"), true),
        Triple("lukas-backup-root", listOf("root-a", "root-b"), true),
        Triple("lukas-backup-root", listOf("root-a"), false),
        Triple("lukas-rotated", listOf("root-a"), true),
        Triple("other-product", listOf("root-a"), false),
        Triple("missing-dc-int", listOf("root-a"), false),
        Triple("single-int-ffe", listOf("root-a"), true),
        Triple("v1-codesigning", listOf("root-a"), false),
        Triple("unrelated-root", listOf("root-a", "root-b"), false)
    ).map { (name, roots, expected) ->
        DynamicTest.dynamicTest("$name with $roots -> ${if (expected) "accept" else "reject"}") {
            // Given
            val credential = credential(name)
            val now = credential.leafNotBefore + 60
            val request = request()
            val headers = credential.sign(request, BODY, now)
            val testedVerifier = ResponseSigningVerifier(roots.map { root(it) })

            // When
            val result = testedVerifier.verify(request, 200, headers, BODY, now)

            // Then
            assertThat(result != null).isEqualTo(expected)
            if (expected) {
                assertThat(checkNotNull(result).expiresAt).isEqualTo(now + 300)
            }
        }
    }

    @Test
    fun `M encode v1 domain and every intermediate hash W transcript()`() {
        // Given
        val credential = credential("lukas-valid")
        val now = credential.leafNotBefore + 60
        val request = request()
        val headers = credential.sign(request, BODY, now)

        // When
        val transcript = ResponseSigningVerifier.transcript(request, 200, headers, BODY)

        // Then
        assertThat(transcript).isEqualTo(expectedTranscript(request, BODY, credential.leaf, credential.chain, now))
        assertThat(transcript.copyOfRange(0, V1_DOMAIN.size)).isEqualTo(V1_DOMAIN)
        val tail = Buffer().write(sha(credential.leaf)).writeByte(2)
            .write(sha(credential.chain[0])).write(sha(credential.chain[1])).readByteArray()
        assertThat(transcript.copyOfRange(transcript.size - tail.size, transcript.size)).isEqualTo(tail)
    }

    @Test
    fun `M reject reordered intermediates W verify()`() {
        // Given
        val credential = credential("lukas-valid")
        val reordered = credential.copy(chain = credential.chain.reversed())
        val now = credential.leafNotBefore + 60
        val request = request()
        val headers = reordered.sign(request, BODY, now)

        // When
        val result = ResponseSigningVerifier(listOf(root("root-a"))).verify(request, 200, headers, BODY, now)

        // Then
        assertThat(result).isNull()
    }

    @TestFactory
    fun `M reject malformed proof headers W verify()`(): List<DynamicTest> {
        val credential = credential("lukas-valid")
        val now = credential.leafNotBefore + 60
        val chain = credential.chain.map { it.toByteString().base64() }
        val cases = mapOf<String, (Headers.Builder) -> Unit>(
            "control" to {},
            "missing chain" to { it.removeAll(CHAIN) },
            "repeated chain" to { it.add(CHAIN, chain.joinToString(",")) },
            "space after comma" to { it.set(CHAIN, chain.joinToString(", ")) },
            "empty entry" to { it.set(CHAIN, chain.joinToString(",,")) },
            "trailing comma" to { it.set(CHAIN, chain.joinToString(",") + ",") },
            "old intermediate header only" to {
                it.removeAll(CHAIN)
                it.add(PREFIX + "signing-intermediate", chain[0])
            },
            "signature version 2" to { it.set(PREFIX + "signature-version", "2") },
            "signature version 3" to { it.set(PREFIX + "signature-version", "3") }
        )
        return cases.map { (name, mutate) ->
            DynamicTest.dynamicTest(name) {
                // Given
                val request = request()
                val headers = credential.sign(request, BODY, now).newBuilder().apply(mutate).build()

                // When
                val result = ResponseSigningVerifier(listOf(root("root-a"))).verify(request, 200, headers, BODY, now)

                // Then
                assertThat(result != null).isEqualTo(name == "control")
            }
        }
    }

    @Test
    fun `M reject prototype request W verify() {signature version 2 request}`() {
        // Given
        val credential = credential("lukas-valid")
        val now = credential.leafNotBefore + 60
        val request = request().newBuilder().header(PREFIX + "signature-version", "2").build()
        val headers = credential.sign(request, BODY, now)

        // When
        val result = ResponseSigningVerifier(listOf(root("root-a"))).verify(request, 200, headers, BODY, now)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region Transcript authority

    @TestFactory
    fun `M drop only the default port of the scheme W authority()`(): List<DynamicTest> = listOf(
        "https://preview.ff-cdn.datad0g.com/precompute-assignments" to "preview.ff-cdn.datad0g.com",
        "https://preview.ff-cdn.datad0g.com:443/precompute-assignments" to "preview.ff-cdn.datad0g.com",
        "https://PREVIEW.ff-cdn.datad0g.com:8443/precompute-assignments" to "preview.ff-cdn.datad0g.com:8443",
        "https://preview.ff-cdn.datad0g.com:80/precompute-assignments" to "preview.ff-cdn.datad0g.com:80",
        "http://localhost/precompute-assignments" to "localhost",
        "http://localhost:80/precompute-assignments" to "localhost",
        "http://localhost:443/precompute-assignments" to "localhost:443",
        "http://localhost:7676/precompute-assignments" to "localhost:7676",
        "https://[::1]/precompute-assignments" to "[::1]",
        "https://[::1]:8443/precompute-assignments" to "[::1]:8443"
    ).map { (url, expected) ->
        DynamicTest.dynamicTest("$url -> $expected") {
            // When
            val result = ResponseSigningVerifier.authority(url.toHttpUrl())

            // Then
            assertThat(result).isEqualTo(expected)
        }
    }

    @Test
    fun `M verify W verify() {explicit default port 443}`() {
        // Given
        val credential = credential("lukas-valid")
        val now = credential.leafNotBefore + 60
        val request = request("https://preview.ff-cdn.datad0g.com:443/precompute-assignments")
        val headers = credential.sign(request, BODY, now)

        // When
        val result = ResponseSigningVerifier(listOf(root("root-a"))).verify(request, 200, headers, BODY, now)

        // Then
        assertThat(result).isNotNull
    }

    @Test
    fun `M reject W verify() {plain HTTP}`() {
        // Given
        val credential = credential("lukas-valid")
        val now = credential.leafNotBefore + 60
        val request = request("http://localhost:7676/precompute-assignments")
        val headers = credential.sign(request, BODY, now)

        // When
        val result = ResponseSigningVerifier(listOf(root("root-a"))).verify(request, 200, headers, BODY, now)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region Certificate profile (minted chains)

    @TestFactory
    fun `M enforce chain certificate profile W verify() {minted chains}`(): List<DynamicTest> {
        val cases = listOf(
            ProfileCase("control: two intermediates", true),
            ProfileCase("control: three intermediates", true, pathLengths = listOf(0, 1, 2), rootPathLength = 3),
            ProfileCase("control: one intermediate", true, pathLengths = listOf(0), rootPathLength = 1),
            ProfileCase("leaf EKU contains FFE and other purposes", true, leafEku = listOf(SERVER_AUTH, FFE, CODE)),
            ProfileCase("leaf EKU critical", true, leafEkuCritical = true),
            ProfileCase("leaf EKU codeSigning only", false, leafEku = listOf(CODE)),
            ProfileCase("leaf EKU other product only", false, leafEku = listOf(OTHER_PRODUCT)),
            ProfileCase("leaf EKU contains anyExtendedKeyUsage", false, leafEku = listOf(FFE, ANY)),
            ProfileCase("leaf EKU absent", false, leafEku = null),
            ProfileCase("leaf EKU empty", false, leafEku = emptyList()),
            ProfileCase("intermediate EKU with FFE", true, intermediateEku = listOf(FFE)),
            ProfileCase("intermediate EKU with anyExtendedKeyUsage", true, intermediateEku = listOf(ANY)),
            ProfileCase("intermediate EKU serverAuth only", false, intermediateEku = listOf(SERVER_AUTH)),
            ProfileCase("intermediate pathLen above index", true, pathLengths = listOf(5, 7)),
            ProfileCase("intermediate pathLen 300 (two-byte INTEGER)", true, pathLengths = listOf(300, 300)),
            ProfileCase("intermediate 1 pathLen 0", false, pathLengths = listOf(0, 0)),
            ProfileCase("intermediate 2 pathLen 1", false, pathLengths = listOf(0, 1, 1), rootPathLength = 3),
            ProfileCase("intermediate pathLen absent", false, pathLengths = listOf(0, null)),
            ProfileCase("intermediate basicConstraints not critical", false, intermediateBasicCritical = false),
            ProfileCase("intermediate keyUsage adds digitalSignature", false, intermediateKeyUsage = "03020186"),
            ProfileCase("intermediate keyUsage nonminimal named bits", false, intermediateKeyUsage = "0303000600"),
            ProfileCase("leaf keyUsage nonminimal named bits", false, leafKeyUsage = "0303008000"),
            ProfileCase("root pathLen equals intermediate count", true, rootPathLength = 2),
            ProfileCase("root pathLen absent", true, rootPathLength = null),
            ProfileCase("root pathLen below intermediate count", false, rootPathLength = 1),
            ProfileCase("root EKU with FFE", true, rootEku = listOf(FFE)),
            ProfileCase("root EKU with anyExtendedKeyUsage", true, rootEku = listOf(ANY)),
            ProfileCase("root EKU with FFE and other purposes", true, rootEku = listOf(CODE, FFE)),
            ProfileCase("root EKU codeSigning only", false, rootEku = listOf(CODE)),
            ProfileCase("root EKU other product only", false, rootEku = listOf(OTHER_PRODUCT)),
            ProfileCase("four intermediates", false, pathLengths = listOf(0, 1, 2, 3), rootPathLength = 4)
        )
        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                // Given
                val chain = case.mint()
                val request = request()
                val headers = chain.sign(request, BODY, MINTED_NOW)

                // When
                val result = ResponseSigningVerifier(listOf(chain.root)).verify(request, 200, headers, BODY, MINTED_NOW)

                // Then
                assertThat(result != null).isEqualTo(case.expected)
            }
        }
    }

    // endregion

    // region Helpers

    private data class Credential(val key: PrivateKey, val leaf: ByteArray, val chain: List<ByteArray>) {
        val leafNotBefore: Long
            get() = (
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(leaf.inputStream()) as X509Certificate
                ).notBefore.time / 1000

        fun sign(request: Request, body: ByteArray, now: Long): Headers {
            val transcript = expectedTranscript(request, body, leaf, chain, now)
            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(key)
                update(transcript)
            }.sign()
            return Headers.Builder()
                .add(PREFIX + "signature-version", "1")
                .add(PREFIX + "signing-certificate", leaf.toByteString().base64())
                .add(CHAIN, chain.joinToString(",") { it.toByteString().base64() })
                .add(PREFIX + "certificate-id", sha(leaf).toByteString().hex())
                .add(PREFIX + "issued-at", now.toString())
                .add(PREFIX + "expires-at", (now + 300).toString())
                .add(PREFIX + "signature", signature.toByteString().base64())
                .build()
        }
    }

    private data class MintedChain(val root: String, val credential: Credential) {
        fun sign(request: Request, body: ByteArray, now: Long): Headers = credential.sign(request, body, now)
    }

    private data class ProfileCase(
        val name: String,
        val expected: Boolean,
        val pathLengths: List<Int?> = listOf(0, 1),
        val rootPathLength: Int? = 2,
        val leafEku: List<String>? = listOf(FFE),
        val leafEkuCritical: Boolean = false,
        val intermediateEku: List<String>? = null,
        val intermediateBasicCritical: Boolean = true,
        val intermediateKeyUsage: String = "03020106",
        val rootEku: List<String>? = null,
        val leafKeyUsage: String = "03020780"
    ) {
        fun mint(): MintedChain {
            val rootKey = keyPair()
            val root = Minted.mint("Root", rootKey, null, caExtensions(rootPathLength, true, "03020106", rootEku))
            // Mint from the root down; header order is the reverse (index 0 issued the leaf).
            var issuer = root
            val intermediates = pathLengths.indices.reversed().map { index ->
                Minted.mint(
                    "Intermediate $index",
                    keyPair(),
                    issuer,
                    caExtensions(pathLengths[index], intermediateBasicCritical, intermediateKeyUsage, intermediateEku)
                ).also { issuer = it }
            }.reversed()
            val leafKey = keyPair()
            val leafExtensions = mutableListOf(
                extension(BASIC_CONSTRAINTS, true, "3000".decodeHex().toByteArray()),
                extension(KEY_USAGE, true, leafKeyUsage.decodeHex().toByteArray())
            )
            leafEku?.let { leafExtensions.add(extension(EKU, leafEkuCritical, eku(it))) }
            val leaf = Minted.mint("ffe-signer", leafKey, issuer, leafExtensions)
            return MintedChain(
                root.der.toByteString().base64(),
                Credential(leafKey.private, leaf.der, intermediates.map { it.der })
            )
        }
    }

    private class Minted(val der: ByteArray, val name: ByteArray, val keyPair: KeyPair, val keyId: ByteArray) {
        companion object {
            fun mint(subject: String, keyPair: KeyPair, issuer: Minted?, extensions: List<ByteArray>): Minted {
                val name = der(0x30, der(0x31, der(0x30, der(6, "550403".hex()), der(12, subject.toByteArray()))))
                val keyId = sha(keyPair.public.encoded).copyOf(20)
                val signer = issuer ?: Minted(ByteArray(0), name, keyPair, keyId)
                val all = extensions + listOfNotNull(
                    extension(SUBJECT_KEY_ID, false, der(4, keyId)),
                    issuer?.let { extension(AUTHORITY_KEY_ID, false, der(0x30, der(0x80, it.keyId))) }
                )
                val tbs = der(
                    0x30,
                    "a003020102".hex(),
                    der(2, byteArrayOf(0x01) + sha(subject.toByteArray()).copyOf(8)),
                    ECDSA_SHA256,
                    signer.name,
                    der(0x30, der(23, "260101000000Z".toByteArray()), der(23, "270101000000Z".toByteArray())),
                    name,
                    keyPair.public.encoded,
                    der(0xa3, der(0x30, *all.toTypedArray()))
                )
                val signature = Signature.getInstance("SHA256withECDSA").apply {
                    initSign(signer.keyPair.private)
                    update(tbs)
                }.sign()
                return Minted(der(0x30, tbs, ECDSA_SHA256, der(3, byteArrayOf(0) + signature)), name, keyPair, keyId)
            }
        }
    }

    private fun credential(name: String): Credential {
        val json = resource("credentials/$name.json")
        val key = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(bytes(json["private_key_pkcs8_der_base64"].asString)))
        return Credential(
            key,
            bytes(json["certificate_der_base64"].asString),
            json.getAsJsonArray("intermediates_der_base64").map { bytes(it.asString) }
        )
    }

    private fun root(name: String): String = resourceBytes("roots/$name.pem").toString(Charsets.US_ASCII)
        .lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")

    private fun resource(name: String): JsonObject =
        JsonParser.parseString(resourceBytes(name).toString(Charsets.UTF_8)).asJsonObject

    private fun resourceBytes(name: String): ByteArray = checkNotNull(javaClass.classLoader)
        .getResourceAsStream("response-signing-chain/$name").let { checkNotNull(it) }.use { it.readBytes() }

    private fun request(url: String = "https://preview.ff-cdn.datad0g.com/precompute-assignments"): Request =
        Request.Builder().url(url)
            .header("dd-client-token", "fixture-token")
            .header("content-type", "application/vnd.api+json")
            .header("x-dd-feature-flags-capabilities", "response-signing-v1")
            .header(PREFIX + "signature-version", "1")
            .header(PREFIX + "request-nonce", "00112233445566778899aabbccddeeff")
            .post("""{"data":{"type":"precompute-assignments-request"}}""".toRequestBody())
            .build()

    companion object {
        private const val PREFIX = "x-datadog-feature-flags-"
        private const val CHAIN = PREFIX + "signing-chain"
        private val BODY = """{"data":{"attributes":{"flags":{}}}}""".toByteArray()
        private val V1_DOMAIN = "datadog.feature-flags.precomputed-assignments.v1\u0000".toByteArray()

        // 2026-06-01T00:00:00Z, inside the minted 2026-01-01..2027-01-01 validity.
        private const val MINTED_NOW = 1780272000L

        private const val BASIC_CONSTRAINTS = "551d13"
        private const val KEY_USAGE = "551d0f"
        private const val EKU = "551d25"
        private const val SUBJECT_KEY_ID = "551d0e"
        private const val AUTHORITY_KEY_ID = "551d23"
        private const val FFE = "2b06010401868d1f0101"
        private const val OTHER_PRODUCT = "2b06010401868d1f0102"
        private const val ANY = "551d2500"
        private const val CODE = "2b06010505070303"
        private const val SERVER_AUTH = "2b06010505070301"
        private val ECDSA_SHA256 = "300a06082a8648ce3d040302".hex()

        /** Independent v1 chain transcript encoder. */
        fun expectedTranscript(
            request: Request,
            body: ByteArray,
            leaf: ByteArray,
            chain: List<ByteArray>,
            now: Long
        ): ByteArray {
            val output = ByteArrayOutputStream()
            val out = DataOutputStream(output)
            fun lp(bytes: ByteArray) {
                out.writeInt(bytes.size)
                out.write(bytes)
            }
            out.write(V1_DOMAIN)
            val url = request.url
            val defaultPort = mapOf("https" to 443, "http" to 80).getValue(url.scheme)
            val host = if (":" in url.host) "[${url.host}]" else url.host
            val authority = host + if (url.port == defaultPort) "" else ":${url.port}"
            listOf(request.method, url.scheme, authority, url.encodedPath, url.encodedQuery?.let { "?$it" } ?: "")
                .forEach { lp(it.toByteArray()) }
            lp(checkNotNull(request.header(PREFIX + "request-nonce")).decodeHex().toByteArray())
            out.write(sha(Buffer().also { request.body?.writeTo(it) }.readByteArray()))
            out.write(sha(checkNotNull(request.header("dd-client-token")).toByteArray()))
            listOf("content-type", "dd-application-id", "x-rkyv", "x-use-cache", "x-dd-feature-flags-capabilities")
                .forEach { name ->
                    val value = request.header(name)
                    out.writeByte(if (value == null) 0 else 1)
                    if (value != null) lp(value.toByteArray())
                }
            out.writeByte(0)
            out.writeShort(200)
            out.writeLong(now)
            out.writeLong(now + 300)
            out.writeLong(body.size.toLong())
            out.write(sha(body))
            out.write(sha(leaf))
            out.writeByte(chain.size)
            chain.forEach { out.write(sha(it)) }
            return output.toByteArray()
        }

        fun caExtensions(pathLength: Int?, critical: Boolean, keyUsage: String, eku: List<String>?): List<ByteArray> {
            val basic = der(
                0x30,
                "0101ff".hex(),
                *listOfNotNull(pathLength?.let { der(2, BigInteger.valueOf(it.toLong()).toByteArray()) })
                    .toTypedArray()
            )
            return listOfNotNull(
                extension(BASIC_CONSTRAINTS, critical, basic),
                extension(KEY_USAGE, true, keyUsage.hex()),
                eku?.let { extension(EKU, false, eku(it)) }
            )
        }

        fun eku(oids: List<String>): ByteArray = der(0x30, *oids.map { der(6, it.hex()) }.toTypedArray())

        fun extension(oid: String, critical: Boolean, value: ByteArray): ByteArray = der(
            0x30,
            der(6, oid.hex()),
            *(if (critical) arrayOf("0101ff".hex()) else emptyArray()),
            der(4, value)
        )

        fun der(tag: Int, vararg contents: ByteArray): ByteArray {
            val value = contents.fold(ByteArray(0)) { acc, part -> acc + part }
            val length = when {
                value.size < 128 -> byteArrayOf(value.size.toByte())
                value.size < 256 -> byteArrayOf(0x81.toByte(), value.size.toByte())
                else -> byteArrayOf(0x82.toByte(), (value.size shr 8).toByte(), value.size.toByte())
            }
            return byteArrayOf(tag.toByte()) + length + value
        }

        fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        fun bytes(value: String): ByteArray = checkNotNull(value.decodeBase64()).toByteArray()

        private fun String.hex(): ByteArray = decodeHex().toByteArray()
    }

    // endregion
}
