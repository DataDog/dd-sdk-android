/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.UnparsedFlag
import com.datadog.android.lint.InternalApi

/**
 * This class exposes internal methods that are used by other Datadog modules and cross-platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross-platform frameworks.
 *
 * Methods, members, and functionality of this class are subject to change without notice, as they
 * are not considered part of the public interface of the Datadog SDK.
 */
@InternalApi
@Suppress("ClassName", "ClassNaming", "UndocumentedPublicFunction")
class _FlagsInternalProxy(private val client: FlagsClient) {
    fun setEvaluationContext(
        context: EvaluationContext,
        callback: EvaluationContextCallback?,
        telemetryOptedOut: Boolean
    ) {
        if (client is DatadogFlagsClient) {
            client.setEvaluationContext(context, callback, telemetryOptedOut)
        } else {
            client.setEvaluationContext(context, callback)
        }
    }

    fun isEvaluationTelemetryOptOutParameterEnabled(): Boolean =
        (client as? DatadogFlagsClient)?.evaluationTelemetryOptOutParameterEnabled == true

    fun getFlagAssignmentsSnapshot(): Map<String, UnparsedFlag> = if (client is DatadogFlagsClient) {
        client.getFlagAssignmentsSnapshot()
    } else {
        emptyMap()
    }

    /**
     * Records exposure, evaluation, and RUM tracking according to the enabled native features.
     * Use the separate operations below for independently selected tracking hooks.
     */
    fun trackFlagSnapshotEvaluation(flagKey: String, flag: UnparsedFlag, context: EvaluationContext) {
        if (client is DatadogFlagsClient) {
            client.trackFlagSnapshotEvaluation(flagKey, flag, context)
        }
    }

    /**
     * Records only an exposure, using the flag and context captured by the caller, without fetching configuration.
     * Evaluation telemetry opt-out intentionally does not suppress exposures.
     */
    fun trackFlagExposure(flagKey: String, flag: UnparsedFlag, context: EvaluationContext) {
        if (client is DatadogFlagsClient) {
            client.trackFlagExposure(flagKey, flag, context)
        }
    }

    /**
     * Records only an evaluation event, without fetching configuration or reporting to RUM.
     * Respects the client's evaluation telemetry opt-out settings.
     * A missing flag represents a runtime default. Unknown OpenFeature error codes are reported as `GENERAL`.
     * The sanitized code is sent as the event's error message, matching the iOS bridge.
     */
    fun trackFlagEvaluationEvent(
        flagKey: String,
        flag: UnparsedFlag?,
        context: EvaluationContext,
        errorCode: String?
    ) {
        if (client is DatadogFlagsClient) {
            client.trackFlagEvaluationEvent(flagKey, flag, context, errorCode)
        }
    }

    /**
     * Records only a RUM flag annotation, respecting the client's evaluation telemetry opt-out settings.
     * Does not fetch configuration, record an exposure, or record an evaluation event.
     */
    fun trackFlagRumEvaluation(flagKey: String, variantKey: String, context: EvaluationContext) {
        if (client is DatadogFlagsClient) {
            client.trackFlagRumEvaluation(flagKey, variantKey, context)
        }
    }
}
