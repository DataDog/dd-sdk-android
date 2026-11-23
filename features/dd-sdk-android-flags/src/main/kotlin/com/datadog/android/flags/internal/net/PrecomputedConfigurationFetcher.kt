/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import androidx.annotation.WorkerThread
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.diagnostics.UUIDv7Generator
import com.datadog.android.flags.internal.evaluation.AssignmentAuthorization
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Fetch
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Sdk
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Trigger
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Fetches precomputed assignments for one context and returns them as a detached configuration.
 *
 * It reuses the request, download and parsing code of [com.datadog.android.flags.FlagsClient], but it does not read
 * or change any client state, repository, cache or listener. The caller decides what to do with the result.
 *
 * @param sdkCore supplies the [com.datadog.android.api.context.DatadogContext] (site, client token, RUM context).
 * @param assignmentsReader executes the precomputed assignments request.
 * @param precomputeMapper parses the response body.
 * @param experimentationOptOutMode validates and acknowledges the experimentation opt-out attribute.
 */
internal class PrecomputedConfigurationFetcher(
    private val sdkCore: FeatureSdkCore,
    private val assignmentsReader: PrecomputedAssignmentsReader,
    private val precomputeMapper: PrecomputeMapper,
    private val experimentationOptOutMode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED
) {
    private val instanceId = UUIDv7Generator.generate(sdkCore.timeProvider.getDeviceTimestampMillis()).toString()
    private val fetchSequence = AtomicLong(0)

    /**
     * Fetches the configuration for [context]. Blocks until the request completes.
     */
    @WorkerThread
    @Suppress("ReturnCount") // Each failure has its own early exit.
    fun fetch(context: EvaluationContext): PrecomputedConfigurationFetchResult {
        // Fresh instances: the fetch must not share request state with any client.
        val authorization = AssignmentAuthorization(sdkCore.internalLogger, Any()).prepare(context)
        val request = ExperimentationOptOut(experimentationOptOutMode).prepare(authorization.context, authorization)
        if (request.invalid) return failure(PrecomputedConfigurationFetchResult.Reason.INVALID_CONTEXT)

        val datadogContext = if (sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME) != null) {
            (sdkCore as? InternalSdkCore)?.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))
        } else {
            null
        } ?: return failure(PrecomputedConfigurationFetchResult.Reason.UNAVAILABLE)

        val sequence = fetchSequence.incrementAndGet()
        val diagnostics = Diagnostics(
            Sdk(instanceId),
            Fetch(sequence, if (sequence == 1L) Trigger.INITIALIZATION else Trigger.CONTEXT_CHANGE)
        )
        // Taken before the request, like the browser SDK, so the value never overstates freshness.
        val fetchedAt = sdkCore.timeProvider.getDeviceTimestampMillis()
        val token = authorization.token
        val result = if (token == null) {
            assignmentsReader.readPrecomputedFlags(request.context, datadogContext, diagnostics)
        } else {
            assignmentsReader.readPrecomputedFlags(request.context, datadogContext, diagnostics, token)
        }

        return when (result) {
            is PrecomputedAssignmentsResponse -> {
                val assignments = precomputeMapper.map(
                    result.body,
                    request.takeIf { it.optedOut },
                    redactErrors = token != null
                )
                // The React Native bridge requires original flag names, which encoded data omits.
                val supportsObfuscation = datadogContext.source == SOURCE_ANDROID
                if (assignments == null || (assignments.obfuscation != null && !supportsObfuscation)) {
                    failure(PrecomputedConfigurationFetchResult.Reason.INVALID_RESPONSE)
                } else {
                    PrecomputedConfigurationFetchResult.Success(
                        configuration = FetchedPrecomputedConfiguration(
                            response = result.body,
                            context = request.context,
                            fetchedAt = fetchedAt,
                            etag = result.etag,
                            assignments = assignments
                        ),
                        flagSelectionIgnoredReason = result.flagSelectionIgnoredReason
                    )
                }
            }

            PrecomputedAssignmentsResult.AuthorizationRejected ->
                failure(PrecomputedConfigurationFetchResult.Reason.AUTHORIZATION_REJECTED)

            null -> failure(PrecomputedConfigurationFetchResult.Reason.NETWORK_ERROR)
        }
    }

    private fun failure(reason: PrecomputedConfigurationFetchResult.Reason) =
        PrecomputedConfigurationFetchResult.Failure(reason)

    private companion object {
        const val SOURCE_ANDROID = "android"
    }
}

/**
 * The outcome of [PrecomputedConfigurationFetcher.fetch].
 */
internal sealed interface PrecomputedConfigurationFetchResult {

    /**
     * [flagSelectionIgnoredReason] is the sanitized `DD-Flag-Selection-Ignored` reason, or null when absent.
     */
    data class Success(
        val configuration: FetchedPrecomputedConfiguration,
        val flagSelectionIgnoredReason: String?
    ) : PrecomputedConfigurationFetchResult

    data class Failure(val reason: Reason) : PrecomputedConfigurationFetchResult

    enum class Reason {
        /** The experimentation opt-out attribute is not a valid timestamp. No request was sent. */
        INVALID_CONTEXT,

        /** Flags is not enabled, or the SDK context is not available. No request was sent. */
        UNAVAILABLE,

        /** The request could not be built or did not get a successful response. */
        NETWORK_ERROR,

        /** The server rejected the assignment authorization token. */
        AUTHORIZATION_REJECTED,

        /** The response body could not be parsed, or it is encoded in a way this caller cannot read. */
        INVALID_RESPONSE
    }
}

/**
 * A precomputed configuration detached from any client. It carries the same data as the `precomputed` entry of the
 * portable configuration string: the raw [response] body, the [context] it was computed for, [fetchedAt] in
 * milliseconds since epoch, and the optional [etag]. [assignments] is [response] parsed once.
 *
 * [context] never contains the assignment authorization token, so the configuration is safe to store.
 */
internal data class FetchedPrecomputedConfiguration(
    val response: String,
    val context: EvaluationContext,
    val fetchedAt: Long,
    val etag: String?,
    val assignments: PrecomputedAssignments
)
