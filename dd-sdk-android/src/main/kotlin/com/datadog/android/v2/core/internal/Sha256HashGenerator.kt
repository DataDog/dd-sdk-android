/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import com.datadog.android.core.internal.utils.unboundInternalLogger
import com.datadog.android.v2.api.InternalLogger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

internal class Sha256HashGenerator : HashGenerator {
    override fun generate(input: String): String? {
        return try {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(input.toByteArray(Charsets.UTF_8))

            val hashBytes = messageDigest.digest()

            hashBytes.joinToString(separator = "") { "%02x".format(Locale.US, it) }
        } catch (e: NoSuchAlgorithmException) {
            unboundInternalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                SHA_256_HASH_GENERATION_ERROR,
                e
            )
            null
        }
    }

    companion object {
        const val SHA_256_HASH_GENERATION_ERROR = "Cannot generate SHA-256 hash."
    }
}
