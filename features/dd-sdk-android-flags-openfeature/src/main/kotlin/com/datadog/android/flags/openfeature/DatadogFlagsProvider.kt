/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsInvalidContextException
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.openfeature.internal.ContextUpdateOutcome
import com.datadog.android.flags.openfeature.internal.adapters.convertToValue
import com.datadog.android.flags.openfeature.internal.adapters.hookFeatures
import com.datadog.android.flags.openfeature.internal.adapters.toDatadogEvaluationContext
import com.datadog.android.flags.openfeature.internal.adapters.toEvaluationMetadata
import com.datadog.android.flags.openfeature.internal.adapters.toOpenFeatureErrorCode
import com.datadog.android.flags.openfeature.internal.adapters.toProviderEvaluation
import com.datadog.android.flags.openfeature.internal.exposure.DatadogProviderMetadata
import com.datadog.android.flags.openfeature.internal.exposure.ExposureAdmissionHook
import com.datadog.android.flags.openfeature.internal.exposure.ExposureDecisions
import com.datadog.android.flags.openfeature.internal.exposure.ExposureMetadata
import com.datadog.android.flags.openfeature.internal.setEvaluationContextSuspend
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.datadog.android.flags.model.EvaluationContext as DatadogEvaluationContext
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
 * // Enable the context-based telemetry control for this client
 * val provider = FlagsClient.Builder("analytics")
 *     .enableEvaluationTelemetryOptOutParameter(true)
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
class DatadogFlagsProvider private constructor(
    private val flagsClient: FlagsClient,
    private val internalLogger: InternalLogger
) :
    FeatureProvider {

    private val contextUpdateAdmission = ContextUpdateAdmission(this)
    private val experimentationOptOutEnabled = _FlagsInternalProxy(flagsClient).isExperimentationOptOutEnabled()
    private val evaluationTelemetryOptOutParameterEnabled =
        _FlagsInternalProxy(flagsClient).isEvaluationTelemetryOptOutParameterEnabled()

    private val exposureDecisions = ExposureDecisions()
    private val nextDecisionId = AtomicInteger(0)
    private val contextOperationsInFlight = AtomicInteger(0)

    override val metadata: ProviderMetadata = DatadogProviderMetadata(PROVIDER_NAME, exposureDecisions, internalLogger)

    override val hooks: List<Hook<*>> = listOf(ExposureAdmissionHook(exposureDecisions))

    /**
     * Initializes the provider with the given evaluation context.
     *
     * Per the OpenFeature spec, this method blocks until the underlying [FlagsClient] has complete setting
     * the initial evaluation context.
     *
     * If an initial context is provided, it will be set on the [FlagsClient] before waiting, otherwise
     * an empty context will be used in order to initialize the underlying [FlagsClient].
     *
     * The method suspends while the [FlagsClient] in turn, takes the context and fetches the flags from the server.
     *
     * @param initialContext The initial evaluation context to set (optional)
     * @throws java.util.concurrent.CancellationException as internal OpenFeature control flow if a newer context
     * replaces this request before its result is processed, or matching cached assignments leave the provider stale.
     * @throws OpenFeatureError if initialization fails or reaches the configured Flags initialization timeout
     * without matching cached assignments
     */
    override suspend fun initialize(initialContext: OpenFeatureEvaluationContext?) {
        setEvaluationContext(initialContext)
    }

    /**
     * Called when the evaluation context changes.
     *
     * Per the OpenFeature spec, this method performs blocking work and suspends until
     * the provider is ready again or encounters an error. This allows the OpenFeature SDK
     * to emit [PROVIDER_RECONCILING] events while this method executes.
     *
     * Uses the callback API to wait for completion without manual listener management.
     *
     * @param oldContext The previous evaluation context
     * @param newContext The new evaluation context to set
     * @throws java.util.concurrent.CancellationException as internal OpenFeature control flow if a newer context
     * replaces this request before its result is processed, or matching cached assignments leave the provider stale.
     * @throws OpenFeatureError if the provider is unable to set the new context.
     */
    override suspend fun onContextSet(
        oldContext: OpenFeatureEvaluationContext?,
        newContext: OpenFeatureEvaluationContext
    ) {
        setEvaluationContext(newContext)
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Boolean> = contextNotReadyEvaluation(defaultValue, context)
        ?: evaluate(key, defaultValue)

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<String> = contextNotReadyEvaluation(defaultValue, context)
        ?: evaluate(key, defaultValue)

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Int> = contextNotReadyEvaluation(defaultValue, context)
        ?: evaluate(key, defaultValue)

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Double> = contextNotReadyEvaluation(defaultValue, context)
        ?: evaluate(key, defaultValue)

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<Value> = contextNotReadyEvaluation(defaultValue, context)
        ?: evaluateObject(key, defaultValue)

    private fun evaluateObject(key: String, defaultValue: Value): ProviderEvaluation<Value> {
        // Use sentinel to avoid unnecessary conversion of user's default value
        val resolution = _FlagsInternalProxy(flagsClient).resolveDeferringExposure(key, SENTINEL_DEFAULT_MAP)
        val resolutionDetails = resolution.details

        // If we got the sentinel back (via reference equality), return the user's original default
        // This happens when: flag not found, provider not ready, or any error occurs
        if (resolutionDetails.value === SENTINEL_DEFAULT_MAP) {
            return ProviderEvaluation(
                value = defaultValue,
                variant = resolutionDetails.variant,
                reason = resolutionDetails.reason?.name,
                errorCode = resolutionDetails.errorCode?.toOpenFeatureErrorCode(),
                errorMessage = resolutionDetails.errorMessage,
                metadata = resolutionDetails.flagMetadata.toEvaluationMetadata()
            )
        }

        val resultValue: Value = Value.Structure(
            resolutionDetails.value.mapValues { (_, v) -> convertToValue(v, internalLogger) }
        )

        return ProviderEvaluation(
            value = resultValue,
            variant = resolutionDetails.variant,
            reason = resolutionDetails.reason?.name,
            errorCode = null,
            errorMessage = null,
            metadata = decisionMetadata(resolution).toEvaluationMetadata()
        ).also { register(it, resolution) }
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
     * - [FlagsClientState.Error] → [OpenFeatureProviderEvents.ProviderError], except for a response signing failure
     *   that [initialize] or [onContextSet] throws: that error already sets the Error status. A proof expiry while
     *   either method runs keeps its event.
     *
     * **SDK emits** (not from provider):
     * - [PROVIDER_RECONCILING]: SDK emits while [onContextSet] is executing
     * - [PROVIDER_CONTEXT_CHANGED]: SDK emits when [onContextSet] completes
     *
     * **Filtered** (not emitted):
     * - [FlagsClientState.NotReady]: Pre-initialization state, [initialize] blocks
     * - [FlagsClientState.Reconciling]: Context reconciliation, SDK handles via blocking [onContextSet]
     *
     * The Flow automatically cleans up the listener when the collector is cancelled.
     *
     * @return Flow of provider state change events
     */
    // Safe: We properly call awaitClose to clean up listener, satisfying callbackFlow requirements
    @Suppress("UnsafeThirdPartyFunctionCall")
    override fun observe(): Flow<OpenFeatureProviderEvents> = callbackFlow {
        val listener = object : FlagsStateListener {
            override fun onStateChanged(newState: FlagsClientState) {
                val providerEvent: OpenFeatureProviderEvents? = when (newState) {
                    FlagsClientState.NotReady -> null // SDK handles via blocking initialize()
                    FlagsClientState.Reconciling -> null // SDK emits PROVIDER_RECONCILING
                    FlagsClientState.Ready -> OpenFeatureProviderEvents.ProviderReady
                    FlagsClientState.Stale -> OpenFeatureProviderEvents.ProviderStale
                    is FlagsClientState.Error ->
                        if (isReportedByContextOperation(newState.error)) null else providerError(newState.error)
                }
                contextUpdateAdmission.publishStateIfAdmitted {
                    providerEvent?.let { trySend(it) }
                }
            }
        }

        flagsClient.state.addListener(listener)
        // Safe: awaitClose runs cleanup then allows CancellationException to propagate naturally
        @Suppress("UnsafeThirdPartyFunctionCall")
        awaitClose {
            flagsClient.state.removeListener(listener)
        }
    }

    private fun <T : Any> evaluate(key: String, defaultValue: T): ProviderEvaluation<T> {
        val resolution = _FlagsInternalProxy(flagsClient).resolveDeferringExposure(key, defaultValue)
        return resolution.details.copy(flagMetadata = decisionMetadata(resolution)).toProviderEvaluation()
            .also { register(it, resolution) }
    }

    // A decision id makes each evaluation's metadata unique, so it can key the evaluation's decision.
    private fun decisionMetadata(resolution: _FlagsInternalProxy.DeferredExposureResolution<*>): Map<String, Any> {
        val metadata = resolution.details.flagMetadata
        return if (hasDecision(resolution)) {
            metadata + (ExposureMetadata.DECISION_ID to nextDecisionId.incrementAndGet())
        } else {
            metadata
        }
    }

    private fun hasDecision(resolution: _FlagsInternalProxy.DeferredExposureResolution<*>): Boolean =
        resolution.recordExposure != null || resolution.details.hookFeatures().isNotEmpty()

    private fun register(
        evaluation: ProviderEvaluation<*>,
        resolution: _FlagsInternalProxy.DeferredExposureResolution<*>
    ) {
        if (!hasDecision(resolution)) return
        exposureDecisions.register(
            evaluation.metadata,
            resolution.details.hookFeatures(),
            resolution.context,
            resolution.recordExposure
        )
    }

    @Throws(CancellationException::class)
    private suspend fun setEvaluationContext(context: OpenFeatureEvaluationContext?) {
        val operation = contextUpdateAdmission.begin(context)
        val datadogContext = targetingContext(context)
        val telemetryOptedOut = if (evaluationTelemetryOptOutParameterEnabled) {
            context?.asMap()?.get(EVALUATION_TELEMETRY_OPT_OUT) == Value.Boolean(true)
        } else {
            null
        }
        contextOperationsInFlight.incrementAndGet()
        val outcome = try {
            flagsClient.setEvaluationContextSuspend(
                datadogContext,
                telemetryOptedOut,
                isCurrentOperation = { contextUpdateAdmission.isCurrent(operation) },
                admitUpdate = { registration -> contextUpdateAdmission.admit(operation, registration) }
            )
        } finally {
            contextOperationsInFlight.decrementAndGet()
        }
        if (outcome == ContextUpdateOutcome.STALE) {
            // OpenFeature Kotlin 0.6.2 unconditionally publishes Ready when initialize() or onContextSet()
            // returns. Cancellation is the only completion path that skips that overwrite, leaving the
            // ProviderStale event emitted by observe() as the public state.
            @Suppress("UnsafeThirdPartyFunctionCall", "ThrowingInternalException")
            // Deliberate public control flow documented by initialize() and onContextSet().
            throw CancellationException("Context completed with stale assignments")
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // OpenFeatureError constructors only store the message.
    private fun providerError(cause: Throwable?) = OpenFeatureProviderEvents.ProviderError(
        error = when (cause) {
            is FlagsInvalidContextException ->
                OpenFeatureError.InvalidContextError(cause.message ?: "Invalid evaluation context")
            else -> OpenFeatureError.GeneralError(cause?.message ?: "Unable to fetch feature flags")
        }
    )

    // OpenFeature sets the Error status from the error that initialize() or onContextSet() throws. A provider event
    // for the same signing failure would make listeners see it twice. Other failures keep their event.
    private fun isReportedByContextOperation(error: Throwable?): Boolean =
        error != null && contextOperationsInFlight.get() > 0 &&
            _FlagsInternalProxy(flagsClient).isReportedByContextUpdate(error)

    private fun targetingContext(context: OpenFeatureEvaluationContext?): DatadogEvaluationContext =
        context?.toDatadogEvaluationContext(experimentationOptOutEnabled) ?: DatadogEvaluationContext.EMPTY

    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun <T> contextNotReadyEvaluation(
        defaultValue: T,
        context: OpenFeatureEvaluationContext?
    ): ProviderEvaluation<T>? = if (contextUpdateAdmission.canEvaluate(context)) {
        null
    } else {
        ProviderEvaluation(
            value = defaultValue,
            reason = ResolutionReason.ERROR.name,
            errorCode = OpenFeatureErrorCode.PROVIDER_NOT_READY,
            errorMessage = "The current OpenFeature context is not ready."
        )
    }

    private data class ContextUpdateOperation(
        val generation: Long,
        val context: OpenFeatureEvaluationContext?,
        val globallyRegistered: Boolean
    )

    private class ContextUpdateAdmission(private val provider: DatadogFlagsProvider) {
        // begin() and admit() share this lock so a newer operation cannot start between the
        // freshness check and native callback registration.
        private val lock = ReentrantLock()
        private val pendingStatePublications = ConcurrentLinkedQueue<() -> Unit>()
        private var generation: Long = 0
        private data class AdmittedContext(val context: OpenFeatureEvaluationContext?)

        @Volatile
        private var admittedContext: AdmittedContext? = null
        private var registrationInProgress = false
        private val deferredStatePublications = mutableListOf<() -> Unit>()

        @Throws(CancellationException::class)
        @Suppress("UnsafeThirdPartyFunctionCall", "ThrowingInternalException")
        fun begin(context: OpenFeatureEvaluationContext?): ContextUpdateOperation = withAdmissionLock {
            val globallyRegistered = OpenFeatureAPI.getProvider() === provider
            if (globallyRegistered && context != OpenFeatureAPI.getEvaluationContext()) {
                throw CancellationException("A newer OpenFeature context replaced this update.")
            }
            ContextUpdateOperation(
                generation = ++generation,
                context = context,
                globallyRegistered = globallyRegistered
            )
        }

        fun admit(operation: ContextUpdateOperation, registration: () -> Unit): Boolean = withAdmissionLock {
            if (isCurrentLocked(operation)) {
                // Registration returns after queuing native work; the lock is not held while awaiting its result.
                registrationInProgress = true
                try {
                    registration()
                    admittedContext = AdmittedContext(operation.context)
                    if (canPublishStateLocked()) {
                        deferredStatePublications.forEach { it() }
                    }
                } finally {
                    registrationInProgress = false
                    deferredStatePublications.clear()
                }
                true
            } else {
                false
            }
        }

        fun isCurrent(operation: ContextUpdateOperation): Boolean = withAdmissionLock {
            isCurrentLocked(operation)
        }

        @Suppress("UnsafeThirdPartyFunctionCall")
        fun canEvaluate(context: OpenFeatureEvaluationContext?): Boolean =
            OpenFeatureAPI.getProvider() !== provider || hasAdmittedGlobalContext(context)

        @Suppress("UnsafeThirdPartyFunctionCall")
        fun publishStateIfAdmitted(publish: () -> Unit) {
            // Native listeners hold the state-holder lock. Never wait for admission from that callback.
            pendingStatePublications.add(publish)
            drainStatePublications()
        }

        private inline fun <T> withAdmissionLock(action: () -> T): T {
            try {
                return lock.withLock(action)
            } finally {
                drainStatePublications()
            }
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // unlock() follows successful tryLock() on this thread.
        private fun drainStatePublications() {
            while (pendingStatePublications.isNotEmpty()) {
                if (!lock.tryLock()) return
                try {
                    while (true) {
                        val publish = pendingStatePublications.poll() ?: break
                        if (canPublishStateLocked()) {
                            publish()
                        } else if (registrationInProgress) {
                            deferredStatePublications += publish
                        }
                    }
                } finally {
                    lock.unlock()
                }
            }
        }

        @Suppress("UnsafeThirdPartyFunctionCall")
        private fun hasAdmittedGlobalContext(context: OpenFeatureEvaluationContext?): Boolean {
            val admitted = admittedContext
            return admitted != null && admitted.context == context && OpenFeatureAPI.getEvaluationContext() == context
        }

        @Suppress("UnsafeThirdPartyFunctionCall")
        private fun canPublishStateLocked(): Boolean =
            OpenFeatureAPI.getProvider() !== provider || hasAdmittedGlobalContext(admittedContext?.context)

        @Suppress("UnsafeThirdPartyFunctionCall")
        private fun isCurrentLocked(operation: ContextUpdateOperation): Boolean =
            operation.generation == generation &&
                (
                    !operation.globallyRegistered ||
                        (
                            OpenFeatureAPI.getProvider() === provider &&
                                operation.context == OpenFeatureAPI.getEvaluationContext()
                            )
                    )
    }

    companion object {
        private const val PROVIDER_NAME = "Datadog Feature Flags Provider"
        private const val EVALUATION_TELEMETRY_OPT_OUT = "dd_evaluation_telemetry_opt_out"

        /**
         * Sentinel default value used to avoid unnecessary Value-to-Map conversion.
         *
         * When resolving structured flags, we pass this sentinel to the [FlagsClient] instead of
         * converting the user's OpenFeature [Value]. If the [FlagsClient] returns this sentinel
         * (via reference equality), we know no flag was found and can return the user's original
         * default without any conversion overhead.
         */
        private val SENTINEL_DEFAULT_MAP: Map<String, Any?> = emptyMap()

        internal fun wrap(
            flagsClient: FlagsClient,
            internalLogger: InternalLogger
        ): DatadogFlagsProvider = DatadogFlagsProvider(flagsClient, internalLogger)
    }
}
