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
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Request
import java.io.IOException
import java.security.ProviderException
import java.security.SecureRandom

/**
 * @param mode the customer opt-in; OFF sends today's requests unchanged
 * @param testTrustedRoots test-only anchors; empty in production, which pins [ResponseSigningTrustRoots.STAGING_ROOT]
 * @param expireVerifiedFlags REQUIRED only: true stops serving verified flags at proof expiry instead of going stale
 */
internal data class ResponseSigningConfiguration(
    val mode: ResponseSigningMode = ResponseSigningMode.OFF,
    val testTrustedRoots: List<String> = emptyList(),
    val expireVerifiedFlags: Boolean = false
) {
    val anchors: List<String> get() = testTrustedRoots.ifEmpty { listOf(ResponseSigningTrustRoots.STAGING_ROOT) }
}

/** Serializes request generations with admission; no response can change trust anchors. */
@Suppress("TooManyFunctions") // Each generation, scope and admission check is a separate locked operation.
internal class ResponseSigningState(
    val configuration: ResponseSigningConfiguration,
    private val logger: InternalLogger,
    private val context: ResponseSigningContext = ResponseSigningContext(),
    minimumNotBefore: MinimumNotBeforeStore = InMemoryMinimumNotBeforeStore(),
    private val telemetry: ResponseSigningTelemetry = ResponseSigningTelemetry(logger, "", ""),
    private val nowSeconds: () -> Long
) {
    val lock = context.lock
    private var identity = context.snapshot()
    private var generation = 0L
    private var requestedContext: EvaluationContext? = null
    private var expectedScope: String? = null
    val verifier = ResponseSigningVerifier(configuration.anchors, minimumNotBefore) { reason ->
        // Draft revocation details stay in debug output; telemetry carries only the shared event.
        logger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            { "Flag response signing v1: $reason" },
            onlyOnce = true
        )
    }

    fun now(): Long = nowSeconds()

    fun currentScope(): String? = synchronized(lock) { if (identity === context.snapshot()) expectedScope else null }

    // The previous scope stays readable until prepare() replaces it, as OFF keeps previous assignments during a
    // fetch. Only a new feature identity (stop or restart) clears it.
    fun begin(context: EvaluationContext): Long = synchronized(lock) {
        generation += 1
        requestedContext = context
        val snapshot = this.context.snapshot()
        if (identity !== snapshot) {
            identity = snapshot
            expectedScope = null
        }
        generation
    }

    fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        requestGeneration == generation && isIdentityCurrent()
    }

    /** Whether [requestGeneration] is still the latest request, whatever the feature lifecycle did. */
    fun isLatest(requestGeneration: Long): Boolean = synchronized(lock) { requestGeneration == generation }

    /** Lifecycle validity only; a newer request generation does not make it false. */
    fun isIdentityCurrent(): Boolean = synchronized(lock) { identity.active && identity === context.snapshot() }

    /** OBSERVE: an accepted response whose request a transport changed persists under that request's own scope. */
    fun rescope(requestGeneration: Long, emitted: Request) {
        val scope = ResponseSigningVerifier.unverifiedScope(emitted)
        admit(requestGeneration) { expectedScope = scope }
    }

    fun admit(requestGeneration: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (!isCurrent(requestGeneration)) return false
        action()
        true
    }

    /**
     * [allowStale] keeps a proof admissible outside its time window (expired, or the clock moved back); the identity,
     * context and scope still apply. Otherwise the clock may be at most [CLOCK_STEP_ALLOWANCE_SECONDS] before
     * admission.
     */
    fun permits(context: EvaluationContext, proof: VerifiedResponse, allowStale: Boolean = false): Boolean =
        synchronized(lock) {
            val now = now()
            val inWindow = now + CLOCK_STEP_ALLOWANCE_SECONDS >= proof.verifiedAt && now < proof.expiresAt
            identity.active && identity === this.context.snapshot() && context == requestedContext &&
                proof.scope == expectedScope && (allowStale || inWindow)
        }

    /** The signed request, or the reason that the SDK could not prepare one; exactly one is set. */
    class Preparation(val request: Request?, val failure: ResponseSigningFailureReason?)

    fun prepare(original: Request, requestGeneration: Long): Preparation = try {
        val nonce = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(nonce)
        val capabilities = original.header("x-dd-feature-flags-capabilities")
            ?.split(',')?.map { it.trim() }?.toMutableSet() ?: mutableSetOf()
        capabilities.add(ResponseSigningVerifier.CAPABILITY)
        // The edge buffers responses it signs, so the request keeps its ordinary transport selection.
        val request = original.newBuilder()
            .header("x-dd-feature-flags-capabilities", capabilities.sorted().joinToString(","))
            .header(ResponseSigningVerifier.PREFIX + "signature-version", ResponseSigningVerifier.SIGNATURE_VERSION)
            .header(ResponseSigningVerifier.PREFIX + "request-nonce", nonce.signingBytes().hex())
            .build()
        val scope = ResponseSigningVerifier.scope(request)
        admit(requestGeneration) { expectedScope = scope }
        Preparation(request, null)
    } catch (_: IllegalArgumentException) {
        prepareUnverified(original, requestGeneration, ResponseSigningFailureReason.UNSUPPORTED_REQUEST)
    } catch (_: IOException) {
        prepareUnverified(original, requestGeneration, ResponseSigningFailureReason.UNSUPPORTED_REQUEST)
    } catch (_: IllegalStateException) {
        prepareUnverified(original, requestGeneration, ResponseSigningFailureReason.UNSUPPORTED_REQUEST)
    } catch (_: ProviderException) {
        prepareUnverified(original, requestGeneration, ResponseSigningFailureReason.UNAVAILABLE_CRYPTO)
    }

    private fun prepareUnverified(
        original: Request,
        requestGeneration: Long,
        failure: ResponseSigningFailureReason
    ): Preparation {
        report(failure)
        if (configuration.mode == ResponseSigningMode.OBSERVE) {
            val scope = ResponseSigningVerifier.unverifiedScope(original)
            admit(requestGeneration) { expectedScope = scope }
        }
        return Preparation(null, failure)
    }

    /** The shared telemetry event for every outcome; a failure also logs the user message (an error when required). */
    fun report(failure: ResponseSigningFailureReason?) {
        telemetry.report(configuration.mode, failure)
        if (failure == null) return
        logger.log(
            if (configuration.mode == ResponseSigningMode.REQUIRED) {
                InternalLogger.Level.ERROR
            } else {
                InternalLogger.Level.WARN
            },
            InternalLogger.Target.USER,
            { failure.message },
            onlyOnce = true
        )
    }
    companion object {
        private const val NONCE_BYTES = 16

        /** A backward clock step up to this size keeps a proof valid; the same as the issued-at allowance. */
        const val CLOCK_STEP_ALLOWANCE_SECONDS = 30L
    }
}
