/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
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
 * later ones at debug level.
 *
 * @param callback receives each evaluation, on the evaluating thread. It must not do I/O.
 * @param internalLogger provides the logger when the evaluation is not from a Datadog provider.
 */
class ExposureHook internal constructor(
    private val callback: ExposureCallback,
    private val internalLogger: () -> InternalLogger
) : Hook<Any> {

    constructor(callback: ExposureCallback) : this(
        callback,
        { (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger ?: InternalLogger.UNBOUND }
    )

    private val callbackFailed = AtomicBoolean(false)
    private val warnedUnsupportedProvider = AtomicBoolean(false)

    override fun finallyAfter(ctx: HookContext<Any>, details: FlagEvaluationDetails<Any>, hints: Map<String, Any>) {
        val providerMetadata = ctx.providerMetadata as? DatadogProviderMetadata
        if (providerMetadata == null) {
            if (!warnedUnsupportedProvider.getAndSet(true)) {
                log(internalLogger(), InternalLogger.Level.WARN) { UNSUPPORTED_PROVIDER_MESSAGE }
            }
            return
        }
        @Suppress("TooGenericExceptionCaught") // The callback is application code.
        try {
            callback.onEvaluation(createEvaluation(ctx, details, providerMetadata))
        } catch (e: Throwable) {
            val level = if (callbackFailed.getAndSet(true)) InternalLogger.Level.DEBUG else InternalLogger.Level.WARN
            log(providerMetadata.internalLogger, level, e) { CALLBACK_FAILED_MESSAGE }
        }
    }

    private fun createEvaluation(
        ctx: HookContext<Any>,
        details: FlagEvaluationDetails<Any>,
        providerMetadata: DatadogProviderMetadata
    ): ExposureEvaluation {
        val decisions = providerMetadata.exposureDecisions
        val decision = decisions.find(details)
        val cacheHit = if (decision?.isExposure == true) decisions.complete(details) else null
        val isExposure = cacheHit != null
        val subject = decision?.context
        val context = if (subject != null) {
            ImmutableContext(subject.targetingKey, subject.attributes.mapValues { Value.String(it.value) })
        } else {
            ctx.ctx?.let { ImmutableContext(it.getTargetingKey(), it.asMap()) }
        }
        return ExposureEvaluation(
            flagKey = details.flagKey,
            variant = details.variant,
            allocationKey = details.metadata.getString(ExposureMetadata.ALLOCATION_KEY),
            targetingKey = (subject?.targetingKey ?: ctx.ctx?.getTargetingKey())?.takeIf { it.isNotEmpty() },
            evaluationContext = context,
            // A mutable copy per callback: Java callers may mutate it, and toMap() returns a fixed map for one entry.
            features = LinkedHashMap(decision?.hookFeatures.orEmpty()),
            exposure = isExposure,
            cacheHit = cacheHit
        )
    }

    private fun log(
        logger: InternalLogger,
        level: InternalLogger.Level,
        throwable: Throwable? = null,
        message: () -> String
    ) {
        @Suppress("TooGenericExceptionCaught") // Logging must not fail the evaluation either.
        try {
            logger.log(level, InternalLogger.Target.USER, message, throwable)
        } catch (ignored: Throwable) {
            // Nothing else can report it.
        }
    }

    private companion object {
        private const val CALLBACK_FAILED_MESSAGE =
            "Exposure callback failed; later failures are logged at debug level."
        private const val UNSUPPORTED_PROVIDER_MESSAGE =
            "ExposureHook only reports evaluations of a DatadogFlagsProvider set directly as the OpenFeature " +
                "provider. Other evaluations are ignored."
    }
}
