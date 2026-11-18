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
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/** Signs canonical v1 chain response bytes with the test CA fixture key, independently of the SDK verifier. */
internal object CanonicalResponseSigner {
    private const val PREFIX = "x-datadog-feature-flags-"
    private val credential = JsonParser.parseString(
        checkNotNull(javaClass.getResource("/response-signing-chain/credentials/two-intermediates.json")).readText()
    ).asJsonObject
    private val leaf = bytes(credential["certificate_der_base64"].asString)
    private val intermediates = credential.getAsJsonArray("intermediates_der_base64").map { it.asString }

    // Test leaves live 72 hours; derive the clock from the fixture so refreshed credentials keep working.
    val now = (
        CertificateFactory.getInstance("X.509").generateCertificate(leaf.inputStream()) as X509Certificate
        ).notBefore.time / 1000 + 60
    val root = checkNotNull(javaClass.getResource("/response-signing-chain/roots/root-a.pem")).readText()
        .lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")

    fun response(request: Request, body: String): Response = httpResponse(request, body = body).newBuilder()
        .headers(signedHeaders(request, body.toByteArray(Charsets.UTF_8))).build()

    private fun signedHeaders(request: Request, body: ByteArray): Headers {
        if (request.header(PREFIX + "request-nonce") == null) return Headers.Builder().build()
        val output = ByteArrayOutputStream()
        val transcript = DataOutputStream(output)
        transcript.write("datadog.feature-flags.precomputed-assignments.v1\u0000".toByteArray())
        fun lp(bytes: ByteArray) { transcript.writeInt(bytes.size); transcript.write(bytes) }
        val url = request.url
        val defaultPort = mapOf("https" to 443, "http" to 80)[url.scheme]
        val authority = url.host + if (url.port == defaultPort) "" else ":${url.port}"
        listOf(request.method, url.scheme, authority, url.encodedPath, url.encodedQuery?.let { "?$it" } ?: "")
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
        transcript.write(sha(body)); transcript.write(sha(leaf))
        transcript.writeByte(intermediates.size)
        intermediates.forEach { transcript.write(sha(bytes(it))) }
        val key = KeyFactory.getInstance(
            "EC"
        ).generatePrivate(PKCS8EncodedKeySpec(bytes(credential["private_key_pkcs8_der_base64"].asString)))
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key); update(output.toByteArray())
        }.sign()
        return Headers.Builder()
            .add(PREFIX + "signature-version", "1")
            .add(PREFIX + "signing-certificate", credential["certificate_der_base64"].asString)
            .add(PREFIX + "signing-chain", intermediates.joinToString(","))
            .add(PREFIX + "certificate-id", sha(leaf).toByteString().hex())
            .add(PREFIX + "issued-at", now.toString())
            .add(PREFIX + "expires-at", (now + 300).toString())
            .add(PREFIX + "signature", signature.toByteString().base64()).build()
    }

    private fun bytes(value: String): ByteArray = checkNotNull(value.decodeBase64()).toByteArray()
    private fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
