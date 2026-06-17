/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

import com.datadog.android.internal.utils.toHexString
import java.io.UnsupportedEncodingException
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

        private val sha256DigestPerThread = object : ThreadLocal<MessageDigest>() {
            @Suppress("UnsafeThirdPartyFunctionCall") // SHA-256 is always available on Android
            override fun initialValue(): MessageDigest = MessageDigest.getInstance("SHA-256")
        }

        /**
         * Creates a [HeatmapIdentifier] for the given UI element by hashing its canonical path,
         * or null if hashing fails.
         *
         * All string parameters must be **raw, unencoded** values — this function applies
         * percent-encoding internally. Cross-platform SDKs must pass raw strings (e.g. the
         * literal screen name returned by `getCurrentViewUrl()`, not a pre-encoded version)
         * so that the identifier is always produced by the same Android encoding logic.
         *
         * @param elementPath the path segments from the root view down to this element,
         *   where each segment identifies a view in the hierarchy (e.g. its resource entry name).
         * @param screenName the current RUM view URL, used to scope identifiers to a screen.
         *   The `view:` namespace prefix is applied internally — callers pass the raw URL
         *   (e.g. `https://example.com/home`), not the prefixed form.
         * @param appPackageName the application package name (e.g. `com.example.app`),
         *   used to globally namespace identifiers across apps.
         * @param onHashingFailure invoked with the caught exception if hashing fails.
         */
        fun create(
            elementPath: List<String>,
            screenName: String,
            appPackageName: String,
            onHashingFailure: (Throwable) -> Unit = {}
        ): HeatmapIdentifier? {
            return try {
                val path = canonicalPath(elementPath, screenName, appPackageName)
                sha256Hex(path, onHashingFailure)?.let { HeatmapIdentifier(it) }
            } catch (e: NoSuchAlgorithmException) {
                // Thrown by the ThreadLocal initialiser if SHA-256 is unavailable.
                onHashingFailure(e)
                null
            } catch (e: UnsupportedEncodingException) {
                // Thrown by URLEncoder.encode if UTF-8 is unavailable (unreachable on Android).
                onHashingFailure(e)
                null
            }
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

        @Suppress("UnsafeThirdPartyFunctionCall") // UnsupportedEncodingException caught in create()
        private fun escape(input: String): String = URLEncoder.encode(input, "UTF-8")

        private fun sha256Hex(input: String, onHashingFailure: (Throwable) -> Unit): String? {
            // ThreadLocal.get() is typed as MessageDigest? (Kotlin platform type) — the ?: guard
            // routes any unexpected null through onHashingFailure rather than throwing an NPE.
            @Suppress("UnsafeThirdPartyFunctionCall")
            val digest = sha256DigestPerThread.get() ?: run {
                onHashingFailure(NoSuchAlgorithmException("SHA-256 MessageDigest unavailable"))
                return null
            }
            // digest(ByteArray) auto-resets on completion, so no explicit reset is needed.
            @Suppress("UnsafeThirdPartyFunctionCall")
            return digest.digest(input.toByteArray(Charsets.UTF_8)).toHexString()
        }
    }
}
