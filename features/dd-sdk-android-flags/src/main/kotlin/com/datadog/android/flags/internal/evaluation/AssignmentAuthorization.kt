/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext

/** Keeps assignment credentials outside targeting data and protects assignments across subject changes. */
internal class AssignmentAuthorization(
    private val internalLogger: InternalLogger?,
    val stateLock: Any
) {
    private var currentRequest: Request? = null
    private var authorizationIsolationActive = false
    private var staleResponseProtectionEnabled = false

    /**
     * Extracts the reserved credential from [context] and records the sanitized context as the latest request.
     * The token is kept separate from targeting data and is carried by its prepared request.
     */
    fun prepare(context: EvaluationContext): Request {
        val inputAttributes: Map<out String?, String?> = context.attributes
        val rawToken = inputAttributes.entries.firstOrNull { it.key == ATTRIBUTE }?.value
        val token = normalize(rawToken)
        val request = Request(targetingContext(context), token)
        synchronized(stateLock) {
            currentRequest = request
            if (token != null) {
                staleResponseProtectionEnabled = true
                authorizationIsolationActive = true
            }
        }
        return request
    }

    /** Returns a defensive copy without the reserved credential or Java-injected null entries. */
    @Suppress("UnsafeThirdPartyFunctionCall") // Kotlin collection reads sanitize Java-injected null entries below.
    fun targetingContext(context: EvaluationContext): EvaluationContext {
        val inputAttributes: Map<out String?, String?> = context.attributes
        val requiresCopy = inputAttributes.any { (key, value) -> key == null || key == ATTRIBUTE || value == null }
        if (!requiresCopy) return context
        val attributes = buildMap {
            inputAttributes.forEach { (key, value) ->
                if (key != null && key != ATTRIBUTE && value != null) put(key, value)
            }
        }
        return context.copy(attributes = attributes)
    }

    fun ifCurrent(request: Request?, action: () -> Unit): Boolean = synchronized(stateLock) {
        if (staleResponseProtectionEnabled && currentRequest !== request) {
            false
        } else {
            action()
            true
        }
    }

    fun onAuthorizationRejected(request: Request?) = synchronized(stateLock) {
        if (currentRequest === request) {
            authorizationIsolationActive = true
            staleResponseProtectionEnabled = true
        }
    }

    fun onAssignmentsAccepted(request: Request?) = synchronized(stateLock) {
        if (currentRequest === request) authorizationIsolationActive = false
    }

    fun fallback(
        request: Request?,
        installedContext: EvaluationContext?,
        requireExactContext: Boolean
    ): Fallback {
        if (request == null || installedContext == null) return Fallback(false, false)
        return synchronized(stateLock) {
            val protectedFallback = authorizationIsolationActive && currentRequest === request
            val usable = if (protectedFallback && !requireExactContext) {
                installedContext.targetingKey == request.context.targetingKey
            } else {
                installedContext == request.context
            }
            Fallback(usable, usable && protectedFallback)
        }
    }

    fun onFallbackAccepted(request: Request?) = onAssignmentsAccepted(request)

    fun resolutionContext(installedContext: EvaluationContext?): ResolutionContext = synchronized(stateLock) {
        val request = currentRequest
        if (!authorizationIsolationActive || request == null ||
            installedContext?.targetingKey == request.context.targetingKey
        ) {
            ResolutionContext(installedContext, null)
        } else {
            ResolutionContext(request.context, ErrorCode.GENERAL)
        }
    }

    fun isStaleResponseProtectionEnabled(): Boolean = synchronized(stateLock) { staleResponseProtectionEnabled }

    @Suppress("UnsafeThirdPartyFunctionCall") // Regex.matches is safe for the non-null String branch.
    private fun normalize(value: String?): String? = when {
        value.isNullOrEmpty() -> null
        !TOKEN_VALUE.matches(value) -> {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { INVALID_TOKEN_MESSAGE },
                onlyOnce = true
            )
            null
        }

        // JWT contents are intentionally opaque. The assignment endpoint validates all claims.
        else -> value
    }

    class Request(val context: EvaluationContext, val token: String?)

    data class Fallback(val usable: Boolean, val authorizationProtected: Boolean)

    data class ResolutionContext(val context: EvaluationContext?, val errorCode: ErrorCode?)

    companion object {
        const val ATTRIBUTE = "dd_assignment_token"
        const val HEADER = "DD-Assignment-Token"
        const val AUTHORIZATION_FAILED = "ASSIGNMENT_AUTHORIZATION_FAILED"
        const val INVALID_TOKEN_MESSAGE =
            "Assignment token omitted: expected a string containing only printable ASCII characters without " +
                "whitespace. Continuing without an assignment token."

        private val TOKEN_VALUE = Regex("^[\\x21-\\x7e]+$")
    }
}
