/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.flags.integration.tests.utils.httpResponse
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/** Signs canonical response bytes with the public fixture key, independently of the SDK verifier. */
internal object CanonicalResponseSigner {
    private const val PREFIX = "x-datadog-feature-flags-"
    private val corpus = JsonParser.parseString(
        checkNotNull(javaClass.getResource("/response-signing-v1/corpus.json")).readText()
    ).asJsonObject
    private val vector = corpus.getAsJsonArray("cases")[0].asJsonObject
    val now = vector["now"].asLong
    val root = corpus["anchor_der_base64"].asString

    fun response(request: Request, body: String): Response = httpResponse(request, body = body).newBuilder()
        .headers(signedHeaders(request, body.toByteArray(Charsets.UTF_8))).build()

    private fun signedHeaders(request: Request, body: ByteArray): Headers {
        if (request.header(PREFIX + "request-nonce") == null) return Headers.Builder().build()
        val credential = vector.getAsJsonObject("credential")
        val leaf = bytes(credential["certificate_der_base64"].asString)
        val intermediate = bytes(credential["intermediate_der_base64"].asString)
        val output = ByteArrayOutputStream()
        val transcript = DataOutputStream(output)
        transcript.write("datadog.feature-flags.precomputed-assignments.v1\u0000".toByteArray())
        fun lp(bytes: ByteArray) { transcript.writeInt(bytes.size); transcript.write(bytes) }
        val url = request.url
        listOf(request.method, url.scheme, url.host, url.encodedPath, url.encodedQuery?.let { "?$it" } ?: "")
            .forEach { lp(it.toByteArray()) }
        lp(checkNotNull(request.header(PREFIX + "request-nonce")).decodeHex().toByteArray())
        transcript.write(sha(Buffer().also { request.body?.writeTo(it) }.readByteArray()))
        transcript.write(sha(checkNotNull(request.header("dd-client-token")).toByteArray()))
        listOf(
            "content-type",
            "dd-application-id",
            "x-rkyv",
            "x-use-cache",
            "x-dd-feature-flags-capabilities"
        ).forEach {
            val header = request.header(it)
            transcript.writeByte(if (header == null) 0 else 1)
            if (header != null) lp(header.toByteArray())
        }
        transcript.writeByte(0)
        transcript.writeShort(200)
        transcript.writeLong(now)
        transcript.writeLong(now + 300)
        transcript.writeLong(body.size.toLong())
        transcript.write(sha(body)); transcript.write(sha(leaf)); transcript.write(sha(intermediate))
        val key = KeyFactory.getInstance(
            "EC"
        ).generatePrivate(PKCS8EncodedKeySpec(bytes(credential["private_key_pkcs8_der_base64"].asString)))
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key); update(output.toByteArray())
        }.sign()
        return Headers.Builder()
            .add(PREFIX + "signature-version", "1")
            .add(PREFIX + "signing-certificate", credential["certificate_der_base64"].asString)
            .add(
                PREFIX + "signing-intermediate",
                credential["intermediate_der_base64"].asString
            )
            .add(PREFIX + "certificate-id", sha(leaf).toByteString().hex())
            .add(PREFIX + "issued-at", now.toString())
            .add(PREFIX + "expires-at", (now + 300).toString())
            .add(PREFIX + "signature", signature.toByteString().base64()).build()
    }

    private fun bytes(value: String): ByteArray = checkNotNull(value.decodeBase64()).toByteArray()
    private fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
