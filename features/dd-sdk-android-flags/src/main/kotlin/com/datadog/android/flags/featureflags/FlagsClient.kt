/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.featureflags.internal.DatadogFlagsClient
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient
import com.datadog.android.flags.featureflags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.net.DefaultFlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.internal.FlagsFeature
import org.json.JSONObject

/**
 * Client interface for evaluating feature flags and experiments.
 *
 * This interface defines the public API that applications use to retrieve feature flag values.
 * It follows the OpenFeature specification closely, to simplify implementing the OpenFeature
 * Provider API on top.
 *
 * ## Usage
 *
 * To create a [FlagsClient], use the [Builder]:
 * ```
 * // Default client
 * val client = FlagsClient.Builder().build()
 *
 * // Named client
 * val client = FlagsClient.Builder("analytics").build()
 *
 * // With custom configuration
 * val client = FlagsClient.Builder("analytics")
 *     .useCustomExposureEndpoint("https://custom.endpoint.com/exposure")
 *     .build()
 * ```
 *
 * To retrieve an existing [FlagsClient], use [get]:
 * ```
 * val client = FlagsClient.get("analytics")
 * ```
 */
interface FlagsClient {
    /**
     * Sets the [EvaluationContext] for flag resolution.
     *
     * The context is used to determine which flag values to return based on user targeting
     * rules. This method triggers a background fetch of updated flag evaluations.
     *
     * @param context The [EvaluationContext] containing targeting key and attributes.
     */
    fun setEvaluationContext(context: EvaluationContext)

    /**
     * Resolves a boolean flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The boolean value of the flag, or the default value if unavailable.
     */
    fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean

    /**
     * Resolves a string flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The string value of the flag, or the default value if unavailable.
     */
    fun resolveStringValue(flagKey: String, defaultValue: String): String

    /**
     * Resolves a numeric flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The numeric value of the flag as a double, or the default value if unavailable.
     */
    fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double

    /**
     * Resolves an integer flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The integer value of the flag, or the default value if unavailable.
     */
    fun resolveIntValue(flagKey: String, defaultValue: Int): Int

    /**
     * Resolves a structured flag value as a JSON object.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The JSON object value of the flag, or the default value if unavailable.
     */
    fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject

    /**
     * Builder for creating [FlagsClient] instances with custom configuration.
     *
     * The builder uses a selective override pattern: configuration fields set explicitly
     * on the builder override the defaults from [FlagsFeature]. Fields not set on the
     * builder use the feature-level defaults.
     *
     * ## Usage Examples
     *
     * Default [FlagsClient]:
     * ```
     * val client = FlagsClient.Builder().build()
     * ```
     *
     * Named [FlagsClient]:
     * ```
     * val client = FlagsClient.Builder("analytics").build()
     * ```
     *
     * With custom SDK core:
     * ```
     * val client = FlagsClient.Builder(sdkCore = customCore)
     *     .build()
     * ```
     */
    class Builder {
        private val name: String
        private val sdkCore: FeatureSdkCore

        /**
         * Creates a builder for a named [FlagsClient].
         *
         * The name is used to identify and retrieve this [FlagsClient] later via [get].
         * Multiple [FlagsClient] instances with different names can coexist within the same [SdkCore].
         *
         * @param name the client name. Must be non-empty.
         * @param sdkCore the SDK instance to associate with this client. Defaults to main instance.
         */
        constructor(name: String = DEFAULT_CLIENT_NAME, sdkCore: SdkCore = Datadog.getInstance()) {
            this.name = name
            this.sdkCore = sdkCore as FeatureSdkCore
        }

        /**
         * Builds and registers a [FlagsClient] instance.
         *
         * This method:
         * 1. Validates the [FlagsFeature] is enabled
         * 2. Creates and registers the client
         * 3. Returns the created client or [NoOpFlagsClient] on failure
         *
         * If a [FlagsClient] with the same name already exists for this [SdkCore]:
         * - Logs a warning (WARN level, USER target)
         * - Returns the existing [FlagsClient]
         *
         * @return the created [FlagsClient], existing client, or [NoOpFlagsClient].
         */
        fun build(): FlagsClient {
            // Validate that the Flags feature is enabled
            val flagsFeature = sdkCore
                .getFeature(FLAGS_FEATURE_NAME)
                ?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                return NoOpFlagsClient(
                    name = name,
                    reason = "Flags feature not enabled",
                    logger = sdkCore.internalLogger
                )
            }

            return flagsFeature.getOrRegisterNewClient(name) {
                createInternal(flagsFeature.getFlagsConfiguration(), sdkCore, flagsFeature)
            }
        }
    }

    /**
     * Companion object providing static access to [FlagsClient] instances.
     *
     * This companion manages the retrieval of [FlagsClient] instances from the [FlagsFeature],
     * ensuring thread-safe access and proper lifecycle management.
     */
    companion object {
        private const val DEFAULT_CLIENT_NAME = "default"

        /**
         * Gets the [FlagsClient] with the specified name from the SDK core.
         *
         * If no [FlagsClient] exists with the given name, returns a [NoOpFlagsClient] that logs
         * critical errors but never crashes.
         *
         * @param name the [FlagsClient] name. Defaults to "default".
         * @param sdkCore the SDK instance. Defaults to the default Datadog instance.
         * @return the [FlagsClient] with the specified name, or [NoOpFlagsClient] if not found.
         */
        @JvmOverloads
        @JvmStatic
        fun get(
            name: String = DEFAULT_CLIENT_NAME,
            sdkCore: FeatureSdkCore = Datadog.getInstance() as FeatureSdkCore
        ): FlagsClient {
            val logger = sdkCore.internalLogger

            val flagsFeature = sdkCore.getFeature(FLAGS_FEATURE_NAME)?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                logger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
                    {
                        "Flags feature is not enabled. Returning NoOpFlagsClient which always returns default values."
                    }
                )

                return NoOpFlagsClient(
                    name = name,
                    reason = "Flags feature not enabled",
                    logger = logger
                )
            }

            var client = flagsFeature.getClient(name)

            if (client == null) {
                logger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
                    {
                        "No FlagsClient with name '$name' exists for SDK instance '${sdkCore.name}'. " +
                            if (name == DEFAULT_CLIENT_NAME) {
                                "Create a client first using: FlagsClient.Builder().build(). "
                            } else {
                                "Create a client first using: FlagsClient.Builder(\"$name\").build(). "
                            } +
                            "Returning NoOpFlagsClient which always returns default values."
                    }
                )

                client = NoOpFlagsClient(
                    name = name,
                    reason = "Client '$name' not found - get() called before build()",
                    logger = logger
                )
            }

            return client
        }

        // region Internal

        internal const val FLAGS_CLIENT_EXECUTOR_NAME = "flags-client-executor"

        internal fun createInternal(
            flagsConfiguration: FlagsConfiguration,
            sdkCore: FeatureSdkCore,
            flagsFeature: FlagsFeature
        ): FlagsClient {
            val executorService = sdkCore.createSingleThreadExecutorService(
                executorContext = FLAGS_CLIENT_EXECUTOR_NAME
            )

            val internalLogger = sdkCore.internalLogger
            val datadogContext = (sdkCore as? InternalSdkCore)?.getDatadogContext()
            val applicationId = flagsFeature.applicationId

            // Get required context parameters
            val clientToken = datadogContext?.clientToken
            val site = datadogContext?.site
            val env = datadogContext?.env

            // Validate required parameters
            if (clientToken == null || site == null || env == null) {
                val missingParams = listOfNotNull(
                    "clientToken".takeIf { clientToken == null },
                    "site".takeIf { site == null },
                    "env".takeIf { env == null }
                ).joinToString(", ")

                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Missing required context parameters: $missingParams" }
                )

                return NoOpFlagsClient(
                    name = "unknown",
                    reason = "Failed to create client - missing SDK context parameters: $missingParams",
                    logger = internalLogger
                )
            } else {
                // Create FlagsContext combining core SDK context with feature configuration
                val flagsContext = FlagsContext.create(
                    datadogContext,
                    applicationId,
                    flagsConfiguration
                )

                val flagsRepository = DefaultFlagsRepository(
                    featureSdkCore = sdkCore
                )

                val flagsNetworkManager = DefaultFlagsNetworkManager(
                    internalLogger = sdkCore.internalLogger,
                    flagsContext = flagsContext
                )

                val precomputeMapper = PrecomputeMapper(sdkCore.internalLogger)

                val evaluationsManager = EvaluationsManager(
                    executorService = executorService,
                    internalLogger = sdkCore.internalLogger,
                    flagsRepository = flagsRepository,
                    flagsNetworkManager = flagsNetworkManager,
                    precomputeMapper = precomputeMapper
                )

                return DatadogFlagsClient(
                    featureSdkCore = sdkCore,
                    evaluationsManager = evaluationsManager,
                    flagsRepository = flagsRepository
                )
            }
        }

        // endregion
    }
}
