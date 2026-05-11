/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

import com.datadog.android.internal.utils.toHexString
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * A stable, globally unique identifier for a UI element, used to correlate
 * RUM tap actions with Session Replay wireframes for heatmap rendering.
 *
 * @property rawValue the hex-encoded SHA-256 hash string that uniquely identifies the element.
 */
data class HeatmapIdentifier(val rawValue: String) {

    companion object {

        private const val SEPARATOR = "/"

        /**
         * Creates a [HeatmapIdentifier] for the given UI element by hashing its canonical path,
         * or null if hashing fails.
         *
         * @param elementPath the path segments from the root view down to this element,
         *   where each segment identifies a view in the hierarchy (e.g. its resource entry name).
         * @param screenName the current RUM view URL, used to scope identifiers to a screen.
         * @param appPackageName the application package name (e.g. `com.example.app`),
         *   used to globally namespace identifiers across apps.
         */
        fun create(
            elementPath: List<String>,
            screenName: String,
            appPackageName: String
        ): HeatmapIdentifier? {
            val path = canonicalPath(elementPath, screenName, appPackageName)
            return sha256Hex(path)?.let { HeatmapIdentifier(it) }
        }

        private fun canonicalPath(
            elementPath: List<String>,
            screenName: String,
            appPackageName: String
        ): String = buildString {
            append(escape(appPackageName))
            append(SEPARATOR)
            append(escape(screenName))
            for (segment in elementPath) {
                append(SEPARATOR)
                append(escape(segment))
            }
        }

        // % must be escaped before / so the encoding is reversible.
        @Suppress("UnsafeThirdPartyFunctionCall") // non-null arguments; cannot throw
        private fun escape(input: String): String =
            input.replace("%", "%25").replace(SEPARATOR, "%2F")

        private fun sha256Hex(input: String): String? {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                // digest(ByteArray) does not throw: the input is non-null (toByteArray always
                // returns a non-null array) and DigestException is only declared on the
                // offset/length overload, not this one.
                @Suppress("UnsafeThirdPartyFunctionCall")
                digest.digest(input.toByteArray(Charsets.UTF_8)).toHexString()
            } catch (_: NoSuchAlgorithmException) {
                null
            }
        }
    }
}
