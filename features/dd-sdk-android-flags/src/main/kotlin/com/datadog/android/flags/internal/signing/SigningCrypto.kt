/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal fun signingCheck(condition: Boolean) {
    if (!condition) signingFailure()
}

@Suppress("ThrowingInternalException") // Every signing entrypoint catches this bounded validation failure.
internal fun signingFailure(): Nothing = throw IllegalArgumentException("Invalid response signing v3 input")

internal fun ByteArray.signingBytes(): ByteString = try {
    toByteString()
} catch (_: IndexOutOfBoundsException) {
    signingFailure()
} catch (_: IllegalArgumentException) {
    signingFailure()
}

internal fun Buffer.signingBytes(): ByteArray = try {
    readByteArray()
} catch (_: IOException) {
    signingFailure()
} catch (_: IllegalArgumentException) {
    signingFailure()
}

internal fun signingHex(value: String): ByteArray = try {
    value.decodeHex().toByteArray()
} catch (_: IllegalArgumentException) {
    signingFailure()
}

internal fun signingSlice(value: ByteArray, start: Int, end: Int): ByteArray {
    signingCheck(start >= 0 && end >= start && end <= value.size)
    return try {
        ByteArray(end - start) { value[start + it] }
    } catch (_: NegativeArraySizeException) {
        signingFailure()
    }
}

internal fun signingCertificate(bytes: ByteArray): X509Certificate = try {
    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
} catch (_: GeneralSecurityException) {
    signingFailure()
} catch (_: ProviderException) {
    signingFailure()
}

internal fun verifyCertificate(certificate: X509Certificate, key: PublicKey): Boolean = try {
    certificate.verify(key)
    true
} catch (_: GeneralSecurityException) {
    false
} catch (_: ProviderException) {
    false
}

internal fun verifySignature(key: PublicKey, transcript: ByteArray, signature: ByteArray): Boolean = try {
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(key)
    verifier.update(transcript)
    verifier.verify(signature)
} catch (_: GeneralSecurityException) {
    false
} catch (_: ProviderException) {
    false
}
