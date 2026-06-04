/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

import com.datadog.android.internal.utils.toHexString
import java.net.URLEncoder
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

        private const val SCREEN_NAMESPACE_VIEW_PREFIX = "view:"

        /**
         * Creates a [HeatmapIdentifier] for the given UI element by hashing its canonical path,
         * or null if hashing fails.
         *
         * @param elementPath the path segments from the root view down to this element,
         *   where each segment identifies a view in the hierarchy (e.g. its resource entry name).
         * @param screenName the current RUM view URL, used to scope identifiers to a screen.
         *   The `view:` namespace prefix is applied internally — callers pass the raw URL
         *   (e.g. `https://example.com/home`), not the prefixed form.
         * @param appPackageName the application package name (e.g. `com.example.app`),
         *   used to globally namespace identifiers across apps.
         * @param onHashingFailure invoked with the caught exception if hashing fails.
         *   Callers should forward this to telemetry.
         */
        fun create(
            elementPath: List<String>,
            screenName: String,
            appPackageName: String,
            onHashingFailure: (Throwable) -> Unit = {}
        ): HeatmapIdentifier? {
            val path = canonicalPath(elementPath, screenName, appPackageName)
            return sha256Hex(path, onHashingFailure)?.let { HeatmapIdentifier(it) }
        }

        private fun canonicalPath(
            elementPath: List<String>,
            screenName: String,
            appPackageName: String
        ): String = buildString {
            append(escape(appPackageName))
            append(SEPARATOR)
            append(escape(SCREEN_NAMESPACE_VIEW_PREFIX + screenName))
            for (segment in elementPath) {
                append(SEPARATOR)
                append(escape(segment))
            }
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // UTF-8 is always available on Android; cannot throw
        private fun escape(input: String): String = URLEncoder.encode(input, "UTF-8")

        private fun sha256Hex(input: String, onHashingFailure: (Throwable) -> Unit): String? {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                // digest(ByteArray) does not throw: the input is non-null (toByteArray always
                // returns a non-null array) and DigestException is only declared on the
                // offset/length overload, not this one.
                @Suppress("UnsafeThirdPartyFunctionCall")
                digest.digest(input.toByteArray(Charsets.UTF_8)).toHexString()
            } catch (e: NoSuchAlgorithmException) {
                onHashingFailure(e)
                null
            }
        }
    }
}
