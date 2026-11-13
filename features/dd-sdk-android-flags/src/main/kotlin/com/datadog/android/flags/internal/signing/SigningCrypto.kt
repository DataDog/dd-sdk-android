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
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.ProviderException
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun signingCheck(condition: Boolean) {
    if (!condition) signingFailure()
}

@Suppress("ThrowingInternalException") // Every signing entrypoint catches this bounded validation failure.
internal fun signingFailure(): Nothing = throw IllegalArgumentException("Invalid response signing v1 input")

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

/** Read the profile directly; Android's X.509 parser rejects valid non-string subject attributes. */
internal fun signingPublicKey(spki: ByteArray): PublicKey = try {
    KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
} catch (_: GeneralSecurityException) {
    signingFailure()
} catch (_: NullPointerException) {
    signingFailure()
} catch (_: ProviderException) {
    signingFailure()
}

private const val UTC_TIME_TAG = 23
private const val GENERALIZED_TIME_TAG = 24
private const val UTC_TIME_CENTURY_CUTOFF = 50
private const val MILLIS_PER_SECOND = 1000

internal fun signingTime(value: SigningDer): Long = try {
    signingCheck(value.tag == UTC_TIME_TAG || value.tag == GENERALIZED_TIME_TAG)
    val encoded = value.value.toString(Charsets.US_ASCII)
    val full = if (value.tag == UTC_TIME_TAG) {
        val year = encoded.take(2).toIntOrNull() ?: signingFailure()
        (if (year >= UTC_TIME_CENTURY_CUTOFF) "19" else "20") + encoded
    } else {
        encoded
    }
    val format = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val date = format.parse(full) ?: signingFailure()
    signingCheck(format.format(date) == full)
    date.time / MILLIS_PER_SECOND
} catch (_: ParseException) {
    signingFailure()
} catch (_: IllegalArgumentException) {
    signingFailure()
} catch (_: NullPointerException) {
    signingFailure()
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
