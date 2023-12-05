/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import com.datadog.android.api.InternalLogger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

internal class MD5HashGenerator(
    private val logger: InternalLogger
) : HashGenerator {
    override fun generate(input: ByteArray): String? {
        return try {
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(input)

            val hashBytes = messageDigest.digest()

            hashBytes.joinToString(separator = "") { "%02x".format(Locale.US, it) }
        } catch (e: NoSuchAlgorithmException) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { MD5_HASH_GENERATION_ERROR },
                e
            )
            null
        }
    }

    private companion object {
        private const val MD5_HASH_GENERATION_ERROR = "Cannot generate MD5 hash."
    }
}
