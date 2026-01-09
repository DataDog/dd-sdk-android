/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Creates an OpenFeature [FeatureProvider] from this [FlagsClient].
 *
 * This is the recommended way to integrate Datadog Feature Flags with OpenFeature.
 * Use [FlagsClient.Builder] to configure your client (custom endpoints, etc.),
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
 * // Custom configuration
 * val provider = FlagsClient.Builder("analytics")
 *     .useCustomExposureEndpoint("https://custom.endpoint.com")
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
 * @return A [FeatureProvider] that delegates to this [FlagsClient]
 * @see FlagsClient.Builder for configuring the underlying flags client
 */
fun FlagsClient.asOpenFeatureProvider(): FeatureProvider = DatadogFlagsProvider.wrap(this)

/**
 * Extension function to convert callback-based setEvaluationContext to suspend function.
 *
 * Wraps the callback API in a [suspendCoroutine], converting success/failure callbacks
 * to [resume]/[resumeWithException].
 *
 * @param context The evaluation context to set
 * @throws [OpenFeatureError.GeneralError] if setting the context fails or times out.
 */
@Suppress("PackageNameVisibility") // Extension function, internal for implementation hiding
internal suspend fun FlagsClient.setEvaluationContextSuspend(context: EvaluationContext) {
    suspendCoroutine<Unit> { continuation ->
        val callback = object : EvaluationContextCallback {
            override fun onSuccess() {
                continuation.resume(Unit)
            }

            override fun onFailure(error: Throwable) {
                continuation.resumeWithException(
                    OpenFeatureError.GeneralError(error.message ?: "Unknown error: ${error::class.simpleName}")
                )
            }
        }

        // setEvaluationContext is guaranteed to return within the configured timeout.
        setEvaluationContext(context, callback)
    }
}
