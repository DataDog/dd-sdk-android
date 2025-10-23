/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.DefaultRumEvaluationLogger
import com.datadog.android.flags.internal.NoOpFlagsClient
import com.datadog.android.flags.internal.NoOpRumEvaluationLogger
import com.datadog.android.flags.internal.RumEvaluationLogger
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.FlagsContext
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.NoOpFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsDownloader
import com.datadog.android.flags.model.ResolutionDetails
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
     * Resolves a flag value with detailed resolution information.
     *
     * This is the core resolution method that provides comprehensive details about the flag
     * resolution process, including the resolved value, variant identifier, resolution reason,
     * error information, and any associated metadata.
     *
     * ## Usage Examples
     *
     * ```kotlin
     * // Boolean flag
     * val boolResult = client.resolve("feature-enabled", false)
     * if (boolResult.errorCode == null) {
     *     println("Feature is ${boolResult.value}, variant: ${boolResult.variant}")
     * }
     *
     * // String flag
     * val stringResult = client.resolve("api-endpoint", "https://default.com")
     * println("Using endpoint: ${stringResult.value}")
     *
     * // With metadata
     * val result = client.resolve("experiment", "control")
     * result.flagMetadata?.let { metadata ->
     *     println("Experiment metadata: $metadata")
     * }
     * ```
     *
     * @param T The type of the flag value (Boolean, String, Int, Double, or JSONObject). Must be non-null.
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed. Cannot be null.
     * @return [com.datadog.android.flags.model.ResolutionDetails] containing the value, variant, reason, error info, and metadata.
     */
    fun <T : Any> resolve(flagKey: String, defaultValue: T): ResolutionDetails<T>

    /**
     * Builder for creating [FlagsClient] instances with custom configuration.
     *
     * The builder uses a selective override pattern: configuration fields set explicitly
     * on the builder override the defaults from [com.datadog.android.flags.internal.FlagsFeature]. Fields not set on the
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
         * Multiple [FlagsClient] instances with different names can coexist within the same [com.datadog.android.api.SdkCore].
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
         * 1. Validates the [com.datadog.android.flags.internal.FlagsFeature] is enabled
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
                .getFeature(Feature.Companion.FLAGS_FEATURE_NAME)
                ?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                return NoOpFlagsClient(
                    name = name,
                    reason = "Flags feature not enabled",
                    logger = sdkCore.internalLogger
                )
            }

            return flagsFeature.getOrRegisterNewClient(name) {
                createInternal(
                    configuration = flagsFeature.flagsConfiguration,
                    featureSdkCore = sdkCore,
                    flagsFeature = flagsFeature
                )
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
        fun get(name: String = DEFAULT_CLIENT_NAME, sdkCore: SdkCore = Datadog.getInstance()): FlagsClient {
            val featureCore = sdkCore as FeatureSdkCore
            val logger = featureCore.internalLogger

            val flagsFeature = featureCore.getFeature(Feature.Companion.FLAGS_FEATURE_NAME)?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                logger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
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
                val buildHint: String = if (name == DEFAULT_CLIENT_NAME) {
                    "Create a client first using: FlagsClient.Builder().build(). "
                } else {
                    "Create a client first using: FlagsClient.Builder(\"$name\").build(). "
                }
                logger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    {
                        "No FlagsClient with name '$name' exists for SDK instance '${sdkCore.name}'. " +
                            buildHint +
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

        @Suppress("LongMethod")
        internal fun createInternal(
            configuration: FlagsConfiguration,
            featureSdkCore: FeatureSdkCore,
            flagsFeature: FlagsFeature
        ): FlagsClient {
            val executorService = featureSdkCore.createSingleThreadExecutorService(
                executorContext = FLAGS_CLIENT_EXECUTOR_NAME
            )

            val datadogContext = (featureSdkCore as? InternalSdkCore)?.getDatadogContext()
            val internalLogger = featureSdkCore.internalLogger

            // Get required context parameters
            val clientToken = datadogContext?.clientToken
            val site = datadogContext?.site
            val env = datadogContext?.env
            val applicationId = flagsFeature.applicationId

            // Validate required parameters
            if (clientToken == null || site == null || env == null) {
                val missingParams = listOfNotNull(
                    "clientToken".takeIf { clientToken == null },
                    "site".takeIf { site == null },
                    "env".takeIf { env == null }
                ).joinToString(", ")

                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { "Missing required context parameters: $missingParams" }
                )

                return NoOpFlagsClient(
                    name = "unknown",
                    reason = "Failed to create client - missing SDK context parameters: $missingParams",
                    logger = internalLogger
                )
            } else {
                // Create FlagsContext combining core SDK context with feature configuration
                val flagsContext = FlagsContext(
                    applicationId = applicationId,
                    clientToken = clientToken,
                    site = site,
                    env = env
                )

                val datastore = (featureSdkCore as FeatureSdkCore).getFeature(Feature.Companion.FLAGS_FEATURE_NAME)
                    ?.dataStore
                val flagsRepository = if (datastore != null) {
                    DefaultFlagsRepository(
                        featureSdkCore = featureSdkCore,
                        dataStore = datastore,
                        instanceName = "default"
                    )
                } else {
                    NoOpFlagsRepository()
                }

                val callFactory = featureSdkCore.createOkHttpCallFactory()
                val assignmentsDownloader = PrecomputedAssignmentsDownloader(
                    internalLogger = featureSdkCore.internalLogger,
                    callFactory = callFactory,
                    flagsContext = flagsContext,
                    requestFactory = flagsFeature.precomputedRequestFactory
                )

                val precomputeMapper = PrecomputeMapper(featureSdkCore.internalLogger)

                val evaluationsManager = EvaluationsManager(
                    executorService = executorService,
                    internalLogger = featureSdkCore.internalLogger,
                    flagsRepository = flagsRepository,
                    assignmentsReader = assignmentsDownloader,
                    precomputeMapper = precomputeMapper
                )

                val rumEvaluationLogger = createRumEvaluationLogger(featureSdkCore)

                return DatadogFlagsClient(
                    featureSdkCore = featureSdkCore,
                    evaluationsManager = evaluationsManager,
                    flagsRepository = flagsRepository,
                    flagsConfiguration = configuration,
                    rumEvaluationLogger = rumEvaluationLogger,
                    processor = flagsFeature.processor
                )
            }
        }

        private fun createRumEvaluationLogger(featureSdkCore: FeatureSdkCore): RumEvaluationLogger {
            val rumFeatureScope = featureSdkCore.getFeature(Feature.Companion.RUM_FEATURE_NAME)

            return rumFeatureScope?.let {
                DefaultRumEvaluationLogger(it)
            } ?: NoOpRumEvaluationLogger()
        }
    }
}