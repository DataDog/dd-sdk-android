/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.openfeature.internal.exposure.DatadogProviderMetadata
import com.datadog.android.flags.openfeature.internal.exposure.ExposureMetadata
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.HookContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An OpenFeature hook that passes each completed flag evaluation, with its Datadog exposure decision,
 * to [callback], so that an application can record exposures in its own data store.
 *
 * Register it with `OpenFeatureAPI.addHooks(listOf(ExposureHook { evaluation -> ... }))`, or on a client.
 * Use [ExposureEvaluation.shouldSend] to keep new or changed exposures. The SDK sends these exposures to Datadog
 * when [com.datadog.android.flags.FlagsConfiguration.Builder.trackExposures] is on.
 *
 * The callback runs in the final hook stage, so it sees errors from `before` and `after` hooks. The provider
 * records the exposure in the final hook stage, after the provider and every `before` and `after` hook succeed.
 * Provider hooks run first in the final stage. If a later `finallyAfter` hook throws, the exposure stays recorded,
 * OpenFeature rethrows the exception, and it skips the `finallyAfter` hooks after the failing one, which can
 * include this hook.
 * With experimentation opt-out enabled, an evaluation whose context a newer context replaced before recording
 * is a non-exposure.
 *
 * The callback runs for evaluations of a [DatadogFlagsProvider], set as the OpenFeature provider or wrapped by a
 * provider that returns its `metadata`. A wrapping provider also returns the Datadog provider's `hooks`, which
 * record every exposure; a wrapper that drops them records an exposure when this callback runs. Composed
 * providers such as `MultiProvider` skip provider hooks, so the SDK records no exposures under them.
 *
 * An exception from the callback does not affect the evaluation. The first one is logged as a warning,
 * later ones at debug level. An `Error` from the callback reaches the caller of the evaluation, with the exposure
 * already recorded.
 *
 * @param callback receives each evaluation, on the evaluating thread. It must not do I/O.
 * @param internalLogger logs evaluations that come from a provider other than [DatadogFlagsProvider].
 */
class ExposureHook internal constructor(
    private val callback: ExposureCallback,
    private val internalLogger: InternalLogger
) : Hook<Any> {

    constructor(callback: ExposureCallback) : this(callback, InternalLogger.UNBOUND)

    private val callbackFailed = AtomicBoolean(false)
    private val warnedUnsupportedProvider = AtomicBoolean(false)

    override fun finallyAfter(ctx: HookContext<Any>, details: FlagEvaluationDetails<Any>, hints: Map<String, Any>) {
        val providerMetadata = ctx.providerMetadata as? DatadogProviderMetadata
        if (providerMetadata == null) {
            if (!warnedUnsupportedProvider.getAndSet(true)) {
                log(internalLogger, InternalLogger.Level.WARN) { UNSUPPORTED_PROVIDER_MESSAGE }
            }
            return
        }
        @Suppress("TooGenericExceptionCaught") // The callback is application code; an Error propagates.
        try {
            createEvaluations(ctx, details, providerMetadata).forEach(callback::onEvaluation)
        } catch (e: Exception) {
            val level = if (callbackFailed.getAndSet(true)) InternalLogger.Level.DEBUG else InternalLogger.Level.WARN
            log(providerMetadata.internalLogger, level, e) { CALLBACK_FAILED_MESSAGE }
        }
    }

    private fun createEvaluations(
        hookContext: HookContext<Any>,
        details: FlagEvaluationDetails<Any>,
        providerMetadata: DatadogProviderMetadata
    ): List<ExposureEvaluation> {
        val decisions = providerMetadata.exposureDecisions
        val decision = decisions.find(details)
        val completion = if (decision != null) decisions.completeAll(details) else null
        val dependencyEvaluations = completion?.dependencies.orEmpty().map { dependency ->
            val dependencyDecision = dependency.decision
            createEvaluation(
                flagKey = dependencyDecision.flagKey,
                variant = dependencyDecision.variantKey,
                allocationKey = dependencyDecision.allocationKey,
                hookFeatures = dependencyDecision.hookFeatures,
                isExposureDecision = dependencyDecision.isExposure,
                cacheHit = dependency.cacheHit,
                hookContext = hookContext,
                subject = decision?.context
            )
        }
        val cacheHit = completion?.rootCacheHit.takeIf { decision?.isExposure == true }
        return dependencyEvaluations + createEvaluation(
            flagKey = details.flagKey,
            variant = details.variant,
            allocationKey = details.metadata.getString(ExposureMetadata.ALLOCATION_KEY),
            hookFeatures = decision?.hookFeatures.orEmpty(),
            isExposureDecision = decision?.isExposure == true,
            cacheHit = cacheHit,
            hookContext = hookContext,
            subject = decision?.context
        )
    }

    private fun createEvaluation(
        flagKey: String,
        variant: String?,
        allocationKey: String?,
        hookFeatures: Map<String, Any>,
        isExposureDecision: Boolean,
        cacheHit: Boolean?,
        hookContext: HookContext<Any>,
        subject: com.datadog.android.flags.model.EvaluationContext?
    ): ExposureEvaluation {
        val isExposure = isExposureDecision && cacheHit != null
        val openFeatureContext = hookContext.ctx
        val context = if (subject != null) {
            ImmutableContext(subject.targetingKey, subject.attributes.mapValues { Value.String(it.value) })
        } else {
            openFeatureContext?.let { ImmutableContext(it.getTargetingKey(), it.asMap()) }
        }
        return ExposureEvaluation(
            flagKey = flagKey,
            variant = variant,
            allocationKey = allocationKey,
            targetingKey = (subject?.targetingKey ?: openFeatureContext?.getTargetingKey())?.takeIf { it.isNotEmpty() },
            evaluationContext = context,
            features = hookFeatures,
            isExposure = isExposure,
            cacheHit = cacheHit
        )
    }

    private fun log(
        logger: InternalLogger,
        level: InternalLogger.Level,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        logger.log(level, InternalLogger.Target.USER, message, throwable)
    }

    private companion object {
        private const val CALLBACK_FAILED_MESSAGE =
            "Exposure callback failed; later failures are logged at debug level."
        private const val UNSUPPORTED_PROVIDER_MESSAGE =
            "ExposureHook only reports evaluations of a DatadogFlagsProvider set directly as the OpenFeature " +
                "provider. Other evaluations are ignored."
    }
}
