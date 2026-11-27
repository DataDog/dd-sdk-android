/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.signing

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningFailureReason
import com.datadog.android.flags.ResponseSigningMode
import java.util.Locale

/**
 * The bounded `flags.response_signing` event, shared with the iOS and browser SDKs. OBSERVE reports every result,
 * REQUIRED reports failures only, and OFF reports nothing.
 *
 * The message carries mode, result and reason, so each outcome has its own telemetry identity. The core RUM telemetry
 * handler deduplicates events by identity for each RUM session and samples them; without RUM, nothing is sent.
 *
 * @param logger the core logger; the event goes to its telemetry target
 * @param site the configured Datadog site, for example `datad0g.com`
 * @param sdkVersion the SDK version string
 */
internal class ResponseSigningTelemetry(
    private val logger: InternalLogger,
    private val site: String,
    private val sdkVersion: String
) {
    fun report(mode: ResponseSigningMode, failure: ResponseSigningFailureReason?) {
        val modeName = when (mode) {
            ResponseSigningMode.OFF -> null
            ResponseSigningMode.OBSERVE -> "observe"
            ResponseSigningMode.REQUIRED -> if (failure == null) null else "required"
        }
        if (modeName == null) return
        val result = if (failure == null) VERIFIED else UNVERIFIED
        val properties = mutableMapOf<String, Any?>(
            "mode" to modeName,
            "result" to result,
            "sdk_version" to sdkVersion,
            "site" to site
        )
        failure?.let { properties["reason"] = it.code }
        logger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.TELEMETRY,
            { listOfNotNull(EVENT, modeName, result, failure?.code).joinToString(" ") },
            additionalProperties = properties
        )
    }

    companion object {
        const val EVENT = "flags.response_signing"
        private const val VERIFIED = "verified"
        private const val UNVERIFIED = "unverified"
        private const val INTAKE_PREFIX = "https://browser-intake-"

        /** `datad0g.com` for staging and `datadoghq.com` for US1, from the site intake host. */
        fun siteName(intakeEndpoint: String): String =
            intakeEndpoint.removePrefix(INTAKE_PREFIX).lowercase(Locale.US)
    }
}
