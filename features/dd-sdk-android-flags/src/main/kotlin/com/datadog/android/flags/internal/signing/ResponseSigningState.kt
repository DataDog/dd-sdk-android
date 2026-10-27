/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Request
import java.io.IOException
import java.security.SecureRandom

internal data class ResponseSigningConfiguration(
    val mode: ResponseSigningMode = ResponseSigningMode.OFF,
    val trustedRoots: List<String> = emptyList()
)

internal data class SignedAssignmentsResponse(
    val json: String,
    val proof: VerifiedResponse?,
    val isCancelled: () -> Boolean = { false }
)

/** Serializes request generations with admission; no response can change trust anchors. */
internal class ResponseSigningState(
    val configuration: ResponseSigningConfiguration,
    private val logger: InternalLogger,
    private val context: ResponseSigningContext = ResponseSigningContext(),
    private val nowSeconds: () -> Long
) {
    val lock = context.lock
    private var identity = context.snapshot()
    private var generation = 0L
    private var requestedContext: EvaluationContext? = null
    private var expectedScope: String? = null
    val verifier = ResponseSigningVerifier(configuration.trustedRoots)

    fun now(): Long = nowSeconds()

    fun currentScope(): String? = synchronized(lock) { if (identity === context.snapshot()) expectedScope else null }

    fun begin(context: EvaluationContext): Long = synchronized(lock) {
        generation += 1
        requestedContext = context
        identity = this.context.snapshot()
        expectedScope = null
        generation
    }

    fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        requestGeneration == generation && identity.active && identity === context.snapshot()
    }

    fun admit(requestGeneration: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (!isCurrent(requestGeneration)) return false
        action()
        true
    }

    fun permits(context: EvaluationContext, proof: VerifiedResponse): Boolean = synchronized(lock) {
        identity.active && identity === this.context.snapshot() && context == requestedContext &&
            proof.scope == expectedScope && now() in proof.verifiedAt until proof.expiresAt
    }

    fun prepare(original: Request, requestGeneration: Long): Request? = try {
        signingCheck(configuration.mode != ResponseSigningMode.REQUIRED || configuration.trustedRoots.isNotEmpty())
        val nonce = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(nonce)
        val capabilities = original.header("x-dd-feature-flags-capabilities")
            ?.split(',')?.map { it.trim() }?.toMutableSet() ?: mutableSetOf()
        capabilities.add(ResponseSigningVerifier.CAPABILITY)
        val request = original.newBuilder()
            .header("x-dd-feature-flags-capabilities", capabilities.sorted().joinToString(","))
            .header(ResponseSigningVerifier.PREFIX + "signature-version", "3")
            .header(ResponseSigningVerifier.PREFIX + "request-nonce", nonce.signingBytes().hex())
            .build()
        signingCheck(original.header("dd-application-id") == identity.applicationId)
        val scope = ResponseSigningVerifier.scope(request)
        admit(requestGeneration) { expectedScope = scope }
        request
    } catch (_: IllegalArgumentException) {
        prepareUnverified(original, requestGeneration)
    } catch (_: IOException) {
        prepareUnverified(original, requestGeneration)
    } catch (_: IllegalStateException) {
        prepareUnverified(original, requestGeneration)
    } catch (_: java.security.ProviderException) {
        prepareUnverified(original, requestGeneration)
    }

    private fun prepareUnverified(original: Request, requestGeneration: Long): Request? {
        report(false)
        if (configuration.mode == ResponseSigningMode.OBSERVE) {
            val scope = ResponseSigningVerifier.unverifiedScope(original)
            admit(requestGeneration) { expectedScope = scope }
        }
        return null
    }

    fun report(verified: Boolean) {
        logger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.TELEMETRY,
            { if (verified) "Flag response signing v3: verified" else "Flag response signing v3: unverified" },
            onlyOnce = true
        )
    }
    private companion object {
        const val NONCE_BYTES = 16
    }
}
