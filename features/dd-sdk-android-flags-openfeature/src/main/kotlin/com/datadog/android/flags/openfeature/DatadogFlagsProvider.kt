/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.openfeature.internal.adapters.convertToValue
import com.datadog.android.flags.openfeature.internal.adapters.convertValueToMap
import com.datadog.android.flags.openfeature.internal.adapters.toDatadogEvaluationContext
import com.datadog.android.flags.openfeature.internal.adapters.toOpenFeatureErrorCode
import com.datadog.android.flags.openfeature.internal.adapters.toProviderEvaluation
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import dev.openfeature.kotlin.sdk.EvaluationContext as OpenFeatureEvaluationContext
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode as OpenFeatureErrorCode

/**
 * OpenFeature [FeatureProvider] implementation backed by Datadog Feature Flags.
 *
 * This provider bridges the OpenFeature API with Datadog's Feature Flags SDK, enabling
 * standardized feature flag management while leveraging Datadog's observability platform.
 *
 * ## Usage
 *
 * Create a [FlagsClient] and wrap it with the extension function:
 *
 * ```kotlin
 * import com.datadog.android.flags.FlagsClient
 * import com.datadog.android.flags.openfeature.asOpenFeatureProvider
 * import dev.openfeature.kotlin.sdk.OpenFeatureAPI
 *
 * // Create a FlagsClient and convert to OpenFeature provider
 * val provider = FlagsClient.Builder().build().asOpenFeatureProvider()
 *
 * // Or with custom configuration
 * val provider = FlagsClient.Builder("analytics")
 *     .useCustomExposureEndpoint("https://custom.endpoint.com")
 *     .build()
 *     .asOpenFeatureProvider()
 *
 * // Set it as the OpenFeature provider
 * OpenFeatureAPI.setProviderAndWait(provider)
 *
 * // Set evaluation context (static-paradigm: applies globally)
 * OpenFeatureAPI.setEvaluationContext(
 *     ImmutableContext(
 *         targetingKey = "user-123",
 *         attributes = mapOf("email" to Value.String("user@example.com"))
 *     )
 * )
 *
 * // Use OpenFeature API
 * val client = OpenFeatureAPI.getClient()
 * val isEnabled = client.getBooleanValue("my-feature", false)
 * ```
 *
 * ## Thread Safety
 *
 * This provider is thread-safe and all methods can be safely called from any thread.
 * The underlying [FlagsClient] handles thread coordination.
 */
class DatadogFlagsProvider private constructor(private val flagsClient: FlagsClient, sdkCore: SdkCore) :
    FeatureProvider {

    private val internalLogger: InternalLogger? = (sdkCore as? FeatureSdkCore)?.internalLogger

    override val metadata: ProviderMetadata = object : ProviderMetadata {
        override val name: String = PROVIDER_NAME
    }

    override val hooks: List<Hook<*>> = emptyList()

    /**
     * Initializes the provider with the given evaluation context.
     *
     * Per the OpenFeature spec, this method blocks until the provider is "ready" - where
     * "ready" means the underlying [FlagsClient] has reached a Ready or Error state.
     *
     * If an initial context is provided, it will be set on the [FlagsClient] before waiting.
     * If no context is provided, the method still waits for the [FlagsClient] to reach
     * a ready state (e.g., from a previous setEvaluationContext call or cached data).
     *
     * The method suspends until the FlagsClient reaches either:
     * - Ready: Flags successfully loaded, resumes normally
     * - Error: Initialization failed, throws exception
     *
     * @param initialContext The initial evaluation context to set (optional)
     * @throws Exception if initialization fails
     */
    override suspend fun initialize(initialContext: OpenFeatureEvaluationContext?) {
        suspendCoroutine<Unit> { continuation ->
            val callback = object : EvaluationContextCallback {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onFailure(error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }

            val datadogContext = initialContext?.toDatadogEvaluationContext()
            if (datadogContext != null) {
                flagsClient.setEvaluationContext(datadogContext, callback)
            } else {
                // No context provided - check if already ready
                when (flagsClient.state.getCurrentState()) {
                    is FlagsClientState.Ready -> continuation.resume(Unit)
                    is FlagsClientState.Error -> continuation.resumeWithException(
                        OpenFeatureError.ProviderFatalError("Provider not ready")
                    )
                    else -> {
                        // Not ready yet - need to wait, but no context to set
                        // This shouldn't happen in normal flow
                        continuation.resumeWithException(
                            Exception("Provider initialization requires a context")
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the evaluation context changes.
     *
     * Per the OpenFeature spec, this method performs blocking work and suspends until
     * the provider is ready again or encounters an error. This allows the OpenFeature SDK
     * to emit PROVIDER_RECONCILING events while this method executes.
     *
     * Uses the callback API to wait for completion without manual listener management.
     *
     * @param oldContext The previous evaluation context (unused)
     * @param newContext The new evaluation context to set
     * @throws Exception if an unrecoverable error occurs during context reconciliation
     */
    override suspend fun onContextSet(
        oldContext: OpenFeatureEvaluationContext?,
        newContext: OpenFeatureEvaluationContext
    ) {
        suspendCoroutine<Unit> { continuation ->
            val callback = object : EvaluationContextCallback {
                override fun onSuccess() {
                    continuation.resume(Unit)
                }

                override fun onFailure(error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }

            flagsClient.setEvaluationContext(newContext.toDatadogEvaluationContext(), callback)
        }
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Boolean> {
        context?.let {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { INVOCATION_CONTEXT_NOT_SUPPORTED_MESSAGE }
            )
        }
        return flagsClient.resolve(key, defaultValue).toProviderEvaluation()
    }

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<String> {
        context?.let {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { INVOCATION_CONTEXT_NOT_SUPPORTED_MESSAGE }
            )
        }
        return flagsClient.resolve(key, defaultValue).toProviderEvaluation()
    }

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Int> {
        context?.let {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { INVOCATION_CONTEXT_NOT_SUPPORTED_MESSAGE }
            )
        }
        return flagsClient.resolve(key, defaultValue).toProviderEvaluation()
    }

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Double> {
        context?.let {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { INVOCATION_CONTEXT_NOT_SUPPORTED_MESSAGE }
            )
        }
        return flagsClient.resolve(key, defaultValue).toProviderEvaluation()
    }

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Value> {
        context?.let {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { INVOCATION_CONTEXT_NOT_SUPPORTED_MESSAGE }
            )
        }

        val mapDefault = (convertValueToMap(defaultValue) as? Map<String, Any?>) ?: emptyMap()

        val resolutionDetails = flagsClient.resolve(key, mapDefault)

        val errorCode = resolutionDetails.errorCode
        if (errorCode != null) {
            return ProviderEvaluation(
                value = defaultValue,
                variant = resolutionDetails.variant,
                reason = resolutionDetails.reason?.name,
                errorCode = errorCode.toOpenFeatureErrorCode(),
                errorMessage = resolutionDetails.errorMessage
            )
        }

        return try {
            val resultValue = Value.Structure(
                resolutionDetails.value.mapValues { (_, v) -> convertToValue(v, internalLogger) }
            )

            ProviderEvaluation(
                value = resultValue,
                variant = resolutionDetails.variant,
                reason = resolutionDetails.reason?.name,
                errorCode = null,
                errorMessage = null
            )
        } catch (e: ClassCastException) {
            ProviderEvaluation(
                value = defaultValue,
                reason = ERROR_REASON,
                errorCode = OpenFeatureErrorCode.TYPE_MISMATCH,
                errorMessage = "Type mismatch during value conversion: ${e.message}"
            )
        } catch (e: IllegalStateException) {
            ProviderEvaluation(
                value = defaultValue,
                reason = ERROR_REASON,
                errorCode = OpenFeatureErrorCode.GENERAL,
                errorMessage = "Invalid value state: ${e.message}"
            )
        }
    }

    override fun shutdown() {
        // No-op: FlagsClient lifecycle is managed separately
    }

    /**
     * Returns a Flow that emits provider state change events.
     *
     * Per the OpenFeature spec, providers emit only certain events - others are handled
     * by the SDK automatically:
     *
     * **Provider emits** (via this Flow):
     * - [FlagsClientState.Ready] → [OpenFeatureProviderEvents.ProviderReady]
     * - [FlagsClientState.Stale] → [OpenFeatureProviderEvents.ProviderStale]
     * - [FlagsClientState.Error] → [OpenFeatureProviderEvents.ProviderError]
     *
     * **SDK emits** (not from provider):
     * - PROVIDER_RECONCILING: SDK emits while [onContextSet] is executing
     * - PROVIDER_CONTEXT_CHANGED: SDK emits when [onContextSet] completes
     *
     * **Filtered** (not emitted):
     * - [FlagsClientState.NotReady]: Pre-initialization state, [initialize] blocks
     * - [FlagsClientState.Reconciling]: Context reconciliation, SDK handles via blocking [onContextSet]
     *
     * The Flow automatically cleans up the listener when the collector is cancelled.
     *
     * @return Flow of provider state change events
     */
    override fun observe(): Flow<OpenFeatureProviderEvents> = callbackFlow {
        val listener = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                val providerEvent: OpenFeatureProviderEvents? = when (newState) {
                    FlagsClientState.NotReady -> null // SDK handles via blocking initialize()
                    FlagsClientState.Reconciling -> null // SDK emits PROVIDER_RECONCILING
                    FlagsClientState.Ready -> OpenFeatureProviderEvents.ProviderReady
                    FlagsClientState.Stale -> OpenFeatureProviderEvents.ProviderStale
                    is FlagsClientState.Error -> OpenFeatureProviderEvents.ProviderError(
                        error = OpenFeatureError.ProviderFatalError()
                    )
                }
                providerEvent?.let { trySend(it) }
            }
        }

        flagsClient.state.addListener(listener)
        awaitClose {
            flagsClient.state.removeListener(listener)
        }
    }

    companion object {
        private const val PROVIDER_NAME = "Datadog Feature Flags Provider"
        private const val ERROR_REASON = "ERROR"
        private const val INVOCATION_CONTEXT_NOT_SUPPORTED_MESSAGE =
            "Invocation Context is not supported in Static-Paradigm clients"

        internal fun wrap(flagsClient: FlagsClient, sdkCore: SdkCore = Datadog.getInstance()): DatadogFlagsProvider =
            DatadogFlagsProvider(flagsClient, sdkCore)
    }
}
