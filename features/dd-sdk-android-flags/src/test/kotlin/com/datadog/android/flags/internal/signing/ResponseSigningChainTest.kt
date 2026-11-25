/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.signing

import android.content.SharedPreferences
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningFailureReason
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Response signing v1 with a certificate chain (1 to 3 intermediates in `x-datadog-feature-flags-signing-chain`).
 * The test signs synthetic responses with an independent transcript encoder, so it does not reuse SDK encoding.
 *
 * `response-signing-chain/` holds test-only credentials from a local test CA. They are fetched from
 * ffe-system-test-data `response-signing/chain/` at the pin in `ci/feature-flags-precomputed-fixtures.json`.
 * Their keys sign nothing outside tests. The test clock follows each leaf, so the 72-hour leaf validity does not expire the test.
 */
internal class ResponseSigningChainTest {

    // region Test CA fixtures

    @TestFactory
    fun `M match expected results W verify() {test-CA-issued fixtures}`(): List<DynamicTest> = listOf(
        Triple("two-intermediates", listOf("root-a"), true),
        Triple("backup-root", listOf("root-b"), true),
        Triple("backup-root", listOf("root-a", "root-b"), true),
        Triple("backup-root", listOf("root-a"), false),
        Triple("rotated-lower-intermediate", listOf("root-a"), true),
        Triple("other-product", listOf("root-a"), false),
        Triple("missing-upper-intermediate", listOf("root-a"), false),
        Triple("one-intermediate", listOf("root-a"), true),
        Triple("codesigning-leaf", listOf("root-a"), false),
        Triple("unrelated-root", listOf("root-a", "root-b"), false)
    ).map { (name, roots, expected) ->
        DynamicTest.dynamicTest("$name with $roots -> ${if (expected) "accept" else "reject"}") {
            // Given
            val credential = credential(name)
            val now = chainNow()
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
    fun `M accept rotated leaves and intermediates W verify() {one verifier, one root}`() {
        // Given
        val testedVerifier = ResponseSigningVerifier(listOf(root("root-a")))
        val generations =
            listOf("two-intermediates", "rotated-lower-intermediate", "one-intermediate", "two-intermediates")
        val leaves = generations.map { credential(it).leaf.toList() }
        val now = chainNow()

        // When
        val results = generations.map { name ->
            val request = request()
            testedVerifier.verify(request, 200, credential(name).sign(request, BODY, now), BODY, now)
        }

        // Then
        assertThat(leaves.distinct()).hasSize(3)
        assertThat(results).allSatisfy { assertThat(it).isNotNull() }
    }

    @Test
    fun `M encode v1 domain and every intermediate hash W transcript()`() {
        // Given
        val credential = credential("two-intermediates")
        val now = chainNow()
        val request = request()
        val headers = credential.sign(request, BODY, now)

        // When
        val transcript = ResponseSigningVerifier.transcript(request, 200, headers, BODY)

        // Then
        assertThat(transcript).isEqualTo(expectedTranscript(request, BODY, credential.leaf, credential.chain, now))
        assertThat(transcript.copyOfRange(0, V1_DOMAIN.size)).isEqualTo(V1_DOMAIN)
        // The chain hashes, then the signed response list: one name, present, with its value.
        val tail = Buffer().write(sha(credential.leaf)).writeByte(2)
            .write(sha(credential.chain[0])).write(sha(credential.chain[1]))
            .writeByte(
                1
            ).writeInt(12).writeUtf8("content-type").writeByte(1).writeInt(16).writeUtf8(RESPONSE_CONTENT_TYPE)
            .readByteArray()
        assertThat(transcript.copyOfRange(transcript.size - tail.size, transcript.size)).isEqualTo(tail)
    }

    @Test
    fun `M reject reordered intermediates W verify()`() {
        // Given
        val credential = credential("two-intermediates")
        val reordered = credential.copy(chain = credential.chain.reversed())
        val now = chainNow()
        val request = request()
        val headers = reordered.sign(request, BODY, now)

        // When
        val result = ResponseSigningVerifier(listOf(root("root-a"))).verify(request, 200, headers, BODY, now)

        // Then
        assertThat(result).isNull()
    }

    @TestFactory
    fun `M reject malformed proof headers W verify()`(): List<DynamicTest> {
        val credential = credential("two-intermediates")
        val now = chainNow()
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
        val credential = credential("two-intermediates")
        val now = chainNow()
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
        val credential = credential("two-intermediates")
        val now = chainNow()
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
        val credential = credential("two-intermediates")
        val now = chainNow()
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

    // region Reason codes

    @TestFactory
    fun `M report the reason code W verification() {rejected response}`(): List<DynamicTest> {
        val chain = ProfileCase("reason control", true).mint()
        val other = ProfileCase("other root", true).mint()
        val request = request()
        val signed = chain.sign(request, BODY, MINTED_NOW)
        val large = ByteArray(ResponseSigningVerifier.MAX_RESPONSE_BYTES + 1)
        return listOf(
            Triple("no proof headers", listOf(chain.root), Headers.Builder().build()) to
                ResponseSigningFailureReason.MISSING_PROOF,
            Triple(
                "malformed certificate-id",
                listOf(chain.root),
                signed.newBuilder().set(CERTIFICATE_ID, "00").build()
            ) to
                ResponseSigningFailureReason.INVALID_PROOF,
            Triple("other root", listOf(other.root), signed) to ResponseSigningFailureReason.INVALID_CERTIFICATE,
            Triple("no anchor", emptyList<String>(), signed) to ResponseSigningFailureReason.MISSING_ANCHOR
        ).map { (case, expected) ->
            DynamicTest.dynamicTest(case.first) {
                // When
                val result = ResponseSigningVerifier(
                    case.second
                ).verification(request, 200, case.third, BODY, MINTED_NOW)
                // Then
                assertThat(result.proof).isNull()
                assertThat(result.failure).isEqualTo(expected)
            }
        } + listOf(
            DynamicTest.dynamicTest("changed body") {
                val result = ResponseSigningVerifier(listOf(chain.root))
                    .verification(request, 200, signed, BODY + ' '.code.toByte(), MINTED_NOW)
                assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.INVALID_SIGNATURE)
            },
            DynamicTest.dynamicTest("expired proof") {
                val result = ResponseSigningVerifier(listOf(chain.root))
                    .verification(request, 200, signed, BODY, MINTED_NOW + 400)
                assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
            },
            DynamicTest.dynamicTest("body above the signed limit") {
                val result = ResponseSigningVerifier(
                    listOf(chain.root)
                ).verification(request, 200, signed, large, MINTED_NOW)
                assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.RESPONSE_TOO_LARGE)
            },
            DynamicTest.dynamicTest("verified") {
                val result = ResponseSigningVerifier(
                    listOf(chain.root)
                ).verification(request, 200, signed, BODY, MINTED_NOW)
                assertThat(result.proof).isNotNull()
                assertThat(result.failure).isNull()
            }
        )
    }

    @TestFactory
    fun `M allow issued-at at most 30 s ahead W verify()`(): List<DynamicTest> {
        val chain = ProfileCase("clock allowance", true).mint()
        val request = request()
        val signed = chain.sign(request, BODY, MINTED_NOW)
        return listOf(30L to true, 31L to false).map { (ahead, expected) ->
            DynamicTest.dynamicTest("issued-at $ahead s ahead") {
                // When
                val result = ResponseSigningVerifier(listOf(chain.root))
                    .verification(request, 200, signed, BODY, MINTED_NOW - ahead)
                // Then
                assertThat(result.proof != null).isEqualTo(expected)
                if (!expected) assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
            }
        }
    }

    @TestFactory
    fun `M reject constructed universal types other than SEQUENCE and SET W SigningDer read()`(): List<DynamicTest> =
        listOf("300424020400", "300430022400", "3004a0022c00").map { value ->
            DynamicTest.dynamicTest(value) {
                // When + Then
                assertThatThrownBy { SigningDer.read(signingHex(value)) }
                    .isInstanceOf(IllegalArgumentException::class.java)
            }
        } + DynamicTest.dynamicTest("SEQUENCE and SET stay accepted") {
            assertThat(SigningDer.read(signingHex("3005310302010a")).children).hasSize(1)
        }

    @Test
    fun `M format the user message W reason code`() {
        assertThat(ResponseSigningFailureReason.values().map { it.code }).containsExactly(
            "unsupported-request", "unavailable-crypto", "missing-anchor", "missing-proof", "invalid-proof",
            "response-too-large", "invalid-certificate", "invalid-signature", "expired-proof", "unsupported-site",
            "revoked-certificate"
        )
        assertThat(ResponseSigningFailureReason.MISSING_PROOF.message).isEqualTo(
            "Flag response signing failed (missing-proof): The response has no signature headers." +
                " A proxy, CDN or custom endpoint removed the signature headers, or the organization is not enrolled."
        )
    }

    @Test
    fun `M report revoked-certificate W verification() {stored minimum not-before}`() {
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store)
        val request = request()
        verifier.verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_19), BODY, NOV_21_0200)
        val result = verifier.verification(
            request,
            200,
            generations.oldLeaf.sign(request, BODY, NOV_21_0200),
            BODY,
            NOV_21_0200
        )
        assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.REVOKED_CERTIFICATE)
    }

    // endregion

    // region Minimum leaf not-before (draft wire)

    @Test
    fun `M reject an older chain W verify() {stored minimum not-before}`() {
        // Given
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val reasons = mutableListOf<String>()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store) { reasons += it }
        val request = request()
        // When
        val raising = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_19),
            BODY,
            NOV_21_0200
        )
        val old = verifier.verify(request, 200, generations.oldLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        val current = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200),
            BODY,
            NOV_21_0200
        )
        // Then
        assertThat(raising).isNotNull()
        assertThat(store.get(generations.anchorId)).isEqualTo(NOV_19)
        assertThat(old).isNull()
        assertThat(reasons).containsExactly(ResponseSigningVerifier.REVOKED_CERTIFICATE)
        assertThat(current).isNotNull()
    }

    @Test
    fun `M ignore a lower minimum not-before W verify()`() {
        // Given
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store)
        val request = request()
        verifier.verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_19), BODY, NOV_21_0200)
        // When
        val lower = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_01),
            BODY,
            NOV_21_0200
        )
        // Then
        assertThat(lower).isNotNull()
        assertThat(store.get(generations.anchorId)).isEqualTo(NOV_19)
        assertThat(
            verifier.verify(request, 200, generations.oldLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        )
            .isNull()
    }

    @Test
    fun `M reject an old leaf under the same intermediates W verify() {stored leaf floor}`() {
        // Given: intermediates from Nov 19, leaves from Nov 20 06:00 (previous) and Nov 21 01:00 (new).
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val reasons = mutableListOf<String>()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store) { reasons += it }
        val request = request()
        // When
        val raising = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_20_1200),
            BODY,
            NOV_21_0200
        )
        val previous = verifier.verify(
            request,
            200,
            generations.previousLeaf.sign(request, BODY, NOV_21_0200),
            BODY,
            NOV_21_0200
        )
        // Then
        assertThat(raising).isNotNull()
        assertThat(store.get(generations.anchorId)).isEqualTo(NOV_20_1200)
        assertThat(previous).isNull()
        assertThat(reasons).containsExactly(ResponseSigningVerifier.REVOKED_CERTIFICATE)
    }

    @Test
    fun `M accept a new leaf with older intermediates W verify() {stored leaf floor}`() {
        // Given: the floor (Nov 20 12:00) is later than both intermediates (Nov 19), not later than the new leaf.
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val reasons = mutableListOf<String>()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store) { reasons += it }
        val request = request()
        verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_20_1200),
            BODY,
            NOV_21_0200
        )
        // When
        val current = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200),
            BODY,
            NOV_21_0200
        )
        // Then
        assertThat(current).isNotNull()
        assertThat(reasons).isEmpty()
    }

    @Test
    fun `M ignore a value above the leaf W verify() {value later than the leaf notBefore}`() {
        // Given
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val reasons = mutableListOf<String>()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store) { reasons += it }
        val request = request()
        // When
        val announcing = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_21_0200),
            BODY,
            NOV_21_0200
        )
        // Then
        assertThat(announcing).isNotNull()
        assertThat(reasons).containsExactly(ResponseSigningVerifier.MINIMUM_NOT_BEFORE_IGNORED)
        assertThat(store.get(generations.anchorId)).isNull()
        assertThat(
            verifier.verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        )
            .isNotNull()
    }

    @TestFactory
    fun `M bind minimum not-before into the signature W verify()`(): List<DynamicTest> {
        val generations = Generations.mint()
        val request = request()
        val signed = generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_19)
        val unsigned = generations.newLeaf.sign(request, BODY, NOV_21_0200)
        return listOf(
            "changed value" to signed.newBuilder().set(MINIMUM_NOT_BEFORE, (NOV_19 - 1).toString()).build(),
            "removed header" to signed.newBuilder().removeAll(MINIMUM_NOT_BEFORE).build(),
            "added header" to unsigned.newBuilder().add(MINIMUM_NOT_BEFORE, NOV_19.toString()).build(),
            "duplicated header" to signed.newBuilder().add(MINIMUM_NOT_BEFORE, NOV_19.toString()).build(),
            "leading zero" to signed.newBuilder().set(MINIMUM_NOT_BEFORE, "0$NOV_19").build()
        ).map { (name, headers) ->
            DynamicTest.dynamicTest(name) {
                // Given
                val store = InMemoryMinimumNotBeforeStore()
                // When
                val result = ResponseSigningVerifier(listOf(generations.root), store)
                    .verify(request, 200, headers, BODY, NOV_21_0200)
                // Then
                assertThat(result).isNull()
                assertThat(store.get(generations.anchorId)).isNull()
            }
        }
    }

    @Test
    fun `M keep the stored value W restart {shared preferences store}`() {
        // Given
        val generations = Generations.mint()
        val preferences = FakePreferences()
        val request = request()
        ResponseSigningVerifier(listOf(generations.root), SharedPreferencesMinimumNotBeforeStore(preferences.mock))
            .verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200, NOV_19), BODY, NOV_21_0200)
        // When
        val restarted =
            ResponseSigningVerifier(listOf(generations.root), SharedPreferencesMinimumNotBeforeStore(preferences.mock))
        // Then
        assertThat(
            restarted.verify(request, 200, generations.oldLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        )
            .isNull()
        assertThat(
            restarted.verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        )
            .isNotNull()
    }

    // endregion

    // region Clock floor

    @Test
    fun `M reject a leaf that expired before the floor W verification() {device clock before build floor}`() {
        // Given
        val generations = Generations.mint()
        val request = request()
        val headers = generations.newLeaf.sign(request, BODY, NOV_21_0200)
        // When
        val result = ResponseSigningVerifier(listOf(generations.root), buildFloor = MINTED_NOT_AFTER + DAY)
            .verification(request, 200, headers, BODY, NOV_21_0200)
        // Then
        assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
        assertThat(ResponseSigningVerifier(listOf(generations.root)).verify(request, 200, headers, BODY, NOV_21_0200))
            .isNotNull()
    }

    @Test
    fun `M keep the build floor at or before the release W BUILD_FLOOR`() {
        assertThat(ResponseSigningVerifier.BUILD_FLOOR).isLessThanOrEqualTo(System.currentTimeMillis() / 1000)
        assertThat(ResponseSigningConfiguration().buildFloor).isEqualTo(ResponseSigningVerifier.BUILD_FLOOR)
    }

    @Test
    fun `M reject an older proof W verification() {clock set back below the high-water mark}`() {
        // Given
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store)
        val request = request()
        val older = generations.newLeaf.sign(request, BODY, NOV_21_0200 - HOUR)
        assertThat(
            ResponseSigningVerifier(listOf(generations.root)).verify(
                request,
                200,
                older,
                BODY,
                NOV_21_0200 - HOUR
            )
        )
            .isNotNull()
        verifier.verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        // When
        val result = verifier.verification(request, 200, older, BODY, NOV_21_0200 - HOUR)
        // Then
        assertThat(store.highWaterMark(generations.anchorId)).isEqualTo(NOV_21_0200)
        assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
    }

    @Test
    fun `M cap the high-water mark at the device clock plus 30 s W verify() {issued-at ahead}`() {
        // Given
        val generations = Generations.mint()
        val store = InMemoryMinimumNotBeforeStore()
        val verifier = ResponseSigningVerifier(listOf(generations.root), store)
        val request = request()
        // When
        val first = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200 + 30),
            BODY,
            NOV_21_0200
        )
        // The mark lets a later issued-at pass, but the device clock still caps the mark.
        val repeated = verifier.verify(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200 + 60),
            BODY,
            NOV_21_0200
        )
        // Then
        assertThat(first).isNotNull()
        assertThat(repeated).isNotNull()
        assertThat(store.highWaterMark(generations.anchorId)).isEqualTo(NOV_21_0200 + 30)
    }

    @Test
    fun `M never lower the high-water mark W raiseHighWaterMark()`() {
        // Given
        val store = SharedPreferencesMinimumNotBeforeStore(FakePreferences().mock)
        store.raiseHighWaterMark("anchor", NOV_21_0200)
        // When
        store.raiseHighWaterMark("anchor", NOV_21_0200 - 1)
        store.raiseHighWaterMark("anchor", -1)
        // Then
        assertThat(store.highWaterMark("anchor")).isEqualTo(NOV_21_0200)
        assertThat(store.get("anchor")).isNull()
    }

    @Test
    fun `M keep the high-water mark W restart {shared preferences store}`() {
        // Given
        val generations = Generations.mint()
        val preferences = FakePreferences()
        val request = request()
        ResponseSigningVerifier(listOf(generations.root), SharedPreferencesMinimumNotBeforeStore(preferences.mock))
            .verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        // When
        val store = SharedPreferencesMinimumNotBeforeStore(preferences.mock)
        val result = ResponseSigningVerifier(listOf(generations.root), store).verification(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200 - HOUR),
            BODY,
            NOV_21_0200 - HOUR
        )
        // Then
        assertThat(store.highWaterMark(generations.anchorId)).isEqualTo(NOV_21_0200)
        assertThat(result.failure).isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
    }

    @Test
    fun `M accept the response and use the memory mark W verify() {preferences write lost}`() {
        // Given
        val generations = Generations.mint()
        val preferences = FakePreferences(persist = false)
        val store = SharedPreferencesMinimumNotBeforeStore(preferences.mock)
        val verifier = ResponseSigningVerifier(listOf(generations.root), store)
        val request = request()
        // When
        val accepted =
            verifier.verify(request, 200, generations.newLeaf.sign(request, BODY, NOV_21_0200), BODY, NOV_21_0200)
        val older = verifier.verification(
            request,
            200,
            generations.newLeaf.sign(request, BODY, NOV_21_0200 - HOUR),
            BODY,
            NOV_21_0200 - HOUR
        )
        // Then
        assertThat(accepted).isNotNull()
        assertThat(preferences.values).isEmpty()
        assertThat(store.highWaterMark(generations.anchorId)).isEqualTo(NOV_21_0200)
        assertThat(older.failure).isEqualTo(ResponseSigningFailureReason.EXPIRED_PROOF)
    }

    // endregion

    // region Helpers

    /** One root; an old intermediate generation from Jan 1 and a staging-like one from Nov 19-20 with two leaves. */
    private class Generations(
        val root: String,
        val anchorId: String,
        val oldLeaf: Credential,
        val previousLeaf: Credential,
        val newLeaf: Credential
    ) {
        companion object {
            fun mint(): Generations {
                val root = Minted.mint("Revocation Root", keyPair(), null, caExtensions(2, true, "03020106", null))
                fun intermediates(name: String, notBefore: String): Minted {
                    val upper = Minted.mint(
                        "$name Upper",
                        keyPair(),
                        root,
                        caExtensions(1, true, "03020106", null),
                        notBefore
                    )
                    return Minted.mint(
                        "$name Lower",
                        keyPair(),
                        upper,
                        caExtensions(0, true, "03020106", null),
                        notBefore
                    )
                        .also { it.parent = upper }
                }
                fun leaf(name: String, issuer: Minted, notBefore: String): Credential {
                    val key = keyPair()
                    val leaf = Minted.mint(
                        name,
                        key,
                        issuer,
                        listOf(
                            extension(BASIC_CONSTRAINTS, true, "3000".decodeHex().toByteArray()),
                            extension(KEY_USAGE, true, "03020780".decodeHex().toByteArray()),
                            extension(EKU, false, eku(listOf(FFE)))
                        ),
                        notBefore
                    )
                    return Credential(key.private, leaf.der, listOf(issuer.der, checkNotNull(issuer.parent).der))
                }
                val old = intermediates("Old", "260101000000Z")
                val current = intermediates("Current", "261119000000Z")
                return Generations(
                    root = root.der.toByteString().base64(),
                    anchorId = sha(root.der).toByteString().hex(),
                    oldLeaf = leaf("ffe-old", old, "261101000000Z"),
                    previousLeaf = leaf("ffe-previous", current, "261120060000Z"),
                    newLeaf = leaf("ffe-new", current, "261121010000Z")
                )
            }
        }
    }

    /** SharedPreferences backed by a map, so a new store instance simulates a restart; [persist] false loses writes. */
    private class FakePreferences(persist: Boolean = true) {
        val values = mutableMapOf<String, Long>()
        private val editor: SharedPreferences.Editor = mock {
            on { putLong(any(), any()) } doAnswer { invocation ->
                if (persist) values[invocation.getArgument(0)] = invocation.getArgument(1)
                invocation.mock as SharedPreferences.Editor
            }
        }
        val mock: SharedPreferences = mock {
            on { getLong(any(), any()) } doAnswer { invocation ->
                values[invocation.getArgument<String>(0)] ?: invocation.getArgument<Long>(1)
            }
            on { edit() } doReturn editor
        }
    }

    private data class Credential(val key: PrivateKey, val leaf: ByteArray, val chain: List<ByteArray>) {
        fun sign(request: Request, body: ByteArray, now: Long, minimumNotBefore: Long? = null): Headers {
            val transcript = expectedTranscript(request, body, leaf, chain, now, minimumNotBefore)
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
                .add(SIGNED_RESPONSE_HEADERS, "content-type")
                .add("content-type", RESPONSE_CONTENT_TYPE)
                .apply { minimumNotBefore?.let { add(MINIMUM_NOT_BEFORE, it.toString()) } }
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
        var parent: Minted? = null

        companion object {
            fun mint(
                subject: String,
                keyPair: KeyPair,
                issuer: Minted?,
                extensions: List<ByteArray>,
                notBefore: String = "260101000000Z"
            ): Minted {
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
                    der(0x30, der(23, notBefore.toByteArray()), der(23, "270101000000Z".toByteArray())),
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
        val json = resource("$name.json")
        val key = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(bytes(json["private_key_pkcs8_der_base64"].asString)))
        return Credential(
            key,
            bytes(json["certificate_der_base64"].asString),
            json.getAsJsonArray("intermediates_der_base64").map { bytes(it.asString) }
        )
    }

    private fun root(name: String): String = resourceBytes("$name.pem").toString(Charsets.US_ASCII)
        .lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")

    // The shared chain manifest fixes the clock at which every chain credential is valid.
    private fun chainNow(): Long = resource("manifest.json")["now"].asLong

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
            .header(SIGNED_HEADERS, "content-type,x-dd-feature-flags-capabilities")
            .post("""{"data":{"type":"precompute-assignments-request"}}""".toRequestBody())
            .build()

    companion object {
        private const val PREFIX = "x-datadog-feature-flags-"
        private const val CHAIN = PREFIX + "signing-chain"
        private const val MINIMUM_NOT_BEFORE = PREFIX + "minimum-leaf-not-before"
        private const val CERTIFICATE_ID = PREFIX + "certificate-id"
        private const val SIGNED_HEADERS = PREFIX + "signed-headers"
        private const val SIGNED_RESPONSE_HEADERS = PREFIX + "signed-response-headers"
        private const val RESPONSE_CONTENT_TYPE = "application/json"

        // Minimum leaf not-before scenario (unix seconds, UTC): staging-like intermediates are older than the leaves.
        private const val NOV_01 = 1793491200L
        private const val NOV_19 = 1795046400L
        private const val NOV_20_1200 = 1795176000L
        private const val NOV_21_0200 = 1795226400L
        private val BODY = """{"data":{"attributes":{"flags":{}}}}""".toByteArray()
        private val V1_DOMAIN = "datadog.feature-flags.precomputed-assignments.v1\u0000".toByteArray()

        // 2026-06-01T00:00:00Z, inside the minted 2026-01-01..2027-01-01 validity.
        private const val MINTED_NOW = 1780272000L
        private const val MINTED_NOT_AFTER = 1798761600L
        private const val HOUR = 3600L
        private const val DAY = 86400L

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
            now: Long,
            minimumNotBefore: Long? = null
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
            fun list(entries: List<Pair<String, String?>>) {
                out.writeByte(entries.size)
                entries.forEach { (name, value) ->
                    lp(name.toByteArray())
                    out.writeByte(if (value == null) 0 else 1)
                    lp((value ?: "").toByteArray())
                }
            }
            list(checkNotNull(request.header(SIGNED_HEADERS)).split(',').map { it to request.header(it) })
            out.writeByte(0)
            out.writeShort(200)
            out.writeLong(now)
            out.writeLong(now + 300)
            out.writeLong(body.size.toLong())
            out.write(sha(body))
            out.write(sha(leaf))
            out.writeByte(chain.size)
            chain.forEach { out.write(sha(it)) }
            list(listOf("content-type" to RESPONSE_CONTENT_TYPE))
            minimumNotBefore?.let {
                out.writeByte(1)
                out.writeLong(it)
            }
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
