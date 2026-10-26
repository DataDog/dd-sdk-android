/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.flags.FlagsClient
import dev.openfeature.kotlin.sdk.FeatureProvider

/**
 * Creates an OpenFeature [FeatureProvider] from this [FlagsClient].
 *
 * This is the recommended way to integrate Datadog Feature Flags with OpenFeature.
 * Use [FlagsClient.Builder] to configure your client,
 * then call this extension to get an OpenFeature provider.
 *
 * ## Usage Examples
 *
 * ```kotlin
 * import com.datadog.android.flags.FlagsClient
 * import com.datadog.android.flags.openfeature.asOpenFeatureProvider
 * import dev.openfeature.kotlin.sdk.OpenFeatureAPI
 *
 * // Default configuration
 * val provider = FlagsClient.Builder().build().asOpenFeatureProvider()
 *
 * // Named client
 * val provider = FlagsClient.Builder("analytics").build().asOpenFeatureProvider()
 *
 * // Optional context-based evaluation telemetry control
 * val provider = FlagsClient.Builder("analytics")
 *     .enableEvaluationTelemetryOptOutParameter(true)
 *     .build()
 *     .asOpenFeatureProvider()
 *
 * // Set it as the OpenFeature provider
 * OpenFeatureAPI.setProviderAndWait(provider)
 *
 * // Use OpenFeature API
 * val client = OpenFeatureAPI.getClient()
 * val isEnabled = client.getBooleanValue("my-feature", false)
 * ```
 *
 * When enabled, only Boolean `true` in `dd_evaluation_telemetry_opt_out` suppresses new evaluation
 * telemetry and automatic RUM flag annotations. Suppression does not change exposure logging or flag values.
 * The attribute remains available for targeting and exposures. Context changes use the normal assignment refresh path.
 * The telemetry decision applies immediately, including while an assignment refresh is pending or fails.
 *
 * @return A [FeatureProvider] that delegates to this [FlagsClient]
 * @see FlagsClient.Builder for configuring the underlying flags client
 */
fun FlagsClient.asOpenFeatureProvider(): FeatureProvider = DatadogFlagsProvider.wrap(this)
