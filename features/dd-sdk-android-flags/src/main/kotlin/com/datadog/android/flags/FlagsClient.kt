/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.DefaultRumEvaluationLogger
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.LogWithPolicy
import com.datadog.android.flags.internal.NoOpFlagsClient
import com.datadog.android.flags.internal.NoOpRumEvaluationLogger
import com.datadog.android.flags.internal.RumEvaluationLogger
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.FlagsContext
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsDownloader
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.NoOpFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.internal.utils.DDCoreSubscription
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
     * This method returns immediately without blocking. The actual context update and flag
     * fetching happen asynchronously on a background thread.
     *
     * @param context The [EvaluationContext] containing targeting key and attributes.
     * @param callback Optional callback to notify when the operation completes or fails.
     */
    fun setEvaluationContext(context: EvaluationContext, callback: EvaluationContextCallback? = null)

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
     * Resolves a structured flag value as a [JSONObject].
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The JSON object value of the flag, or the default value if unavailable.
     */
    fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject

    /**
     * Resolves a structured flag value as a Map.
     *
     * The returned Map contains only primitives (String, Int, Long, Double, Boolean),
     * null values, nested Maps, and Lists. All nested structures are recursively
     * converted.
     *
     * This method is useful for integrations that prefer working with Kotlin collections
     * over JSON types.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The map to return if the flag cannot be retrieved or parsed.
     * @return The map value of the flag, or the default value if unavailable.
     */
    fun resolveStructureValue(flagKey: String, defaultValue: Map<String, Any?>): Map<String, Any?>

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
     * @return [ResolutionDetails] containing the value, variant, reason, error info, and metadata.
     */
    fun <T : Any> resolve(flagKey: String, defaultValue: T): ResolutionDetails<T>

    /**
     * Observable interface for tracking client state changes.
     *
     * Provides three ways to observe state:
     * - Synchronous: [StateObservable.getCurrentState] for immediate queries (Java-friendly)
     * - Callback: [StateObservable.addListener] for traditional observers (Java-friendly)
     *
     * Example:
     * ```kotlin
     * // Synchronous
     * val current = client.state.getCurrentState()
     *
     * // Callback
     * client.state.addListener(listener)
     * ```
     */
    val state: StateObservable

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
            this.name = name.ifBlank {
                val flagsFeature = (sdkCore as FeatureSdkCore)
                    .getFeature(FLAGS_FEATURE_NAME)
                    ?.unwrap<FlagsFeature>()
                flagsFeature?.logErrorWithPolicy(
                    message = "FlagsClient name cannot be blank. Using default client name",
                    level = InternalLogger.Level.ERROR,
                    shouldCrashInStrict = true
                )
                DEFAULT_CLIENT_NAME
            }
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
         * - Logs a warning according to the graceful mode policy
         * - Returns the existing [FlagsClient]
         * - In strict mode (debug builds with gracefulModeEnabled=false), throws an exception
         *
         * @return the created [FlagsClient], existing client, or [NoOpFlagsClient].
         * @throws IllegalStateException in strict mode (debug builds with gracefulModeEnabled=false)
         *         if a client with the same name already exists. This prevents accidental duplicate
         *         client creation during development.
         */
        fun build(): FlagsClient {
            // Validate that the Flags feature is enabled
            val flagsFeature = sdkCore
                .getFeature(FLAGS_FEATURE_NAME)
                ?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                // Feature not enabled - use fallback logging (no feature to determine policy)
                // Default to graceful behavior when feature not available
                val logger = sdkCore.internalLogger
                val logWithPolicy: LogWithPolicy = { message, level ->
                    logger.log(level, InternalLogger.Target.USER, { message })
                }

                logWithPolicy(
                    "Failed to create FlagsClient named '$name': Flags feature must be " +
                        "enabled first. Call Flags.enable() before creating clients. " +
                        "Operating in no-op mode.",
                    InternalLogger.Level.ERROR
                )

                return NoOpFlagsClient(
                    name = name,
                    reason = "Flags feature not enabled",
                    logWithPolicy = logWithPolicy
                )
            }

            return flagsFeature.getOrRegisterNewClient(name) {
                createInternal(
                    configuration = flagsFeature.flagsConfiguration,
                    featureSdkCore = sdkCore,
                    flagsFeature = flagsFeature,
                    name = name
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
         * errors according to the graceful mode policy.
         *
         * @param name the [FlagsClient] name. Defaults to "default".
         * @param sdkCore the SDK instance. Defaults to the default Datadog instance.
         * @return the [FlagsClient] with the specified name, or [NoOpFlagsClient] if not found.
         * @throws IllegalStateException in strict mode (debug builds with gracefulModeEnabled=false)
         *         if the client doesn't exist. This helps catch configuration errors during development.
         */
        @JvmOverloads
        @JvmStatic
        fun get(name: String = DEFAULT_CLIENT_NAME, sdkCore: SdkCore = Datadog.getInstance()): FlagsClient {
            val featureCore = sdkCore as FeatureSdkCore
            val logger = featureCore.internalLogger

            val flagsFeature = featureCore.getFeature(FLAGS_FEATURE_NAME)?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                logger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { "Flags feature is not enabled. Returning NoOpFlagsClient." }
                )

                // No feature available to log through, so we use the logger directly.
                return NoOpFlagsClient(
                    name = name,
                    reason = "Flags feature not enabled",
                    logWithPolicy = { message, level ->
                        logger.log(level, InternalLogger.Target.USER, { message })
                    }
                )
            }
            return flagsFeature.getClient(name) ?: handleClientNotFound(name, flagsFeature, sdkCore)
        }

        private fun handleClientNotFound(
            name: String,
            flagsFeature: FlagsFeature,
            sdkCore: FeatureSdkCore
        ): FlagsClient {
            val buildHint = if (name == DEFAULT_CLIENT_NAME) {
                "FlagsClient.Builder().build()"
            } else {
                "FlagsClient.Builder(\"$name\").build()"
            }

            // Client not found, in strict mode this call will crash the app.
            flagsFeature.logErrorWithPolicy(
                message = "No FlagsClient with name '$name' exists for SDK instance '${sdkCore.name}'. " +
                    "Create a client first using: $buildHint. " +
                    "Operating in no-op mode.",
                level = InternalLogger.Level.ERROR,
                shouldCrashInStrict = true
            )

            // Did not crash, not in strict mode, so return a NoOpFlagsClient that logs through the Feature's policy-driven logger.
            return NoOpFlagsClient(
                name = name,
                reason = "Client '$name' not found - get() called before build()",
                logWithPolicy = { message, level ->
                    flagsFeature.logErrorWithPolicy(message, level, shouldCrashInStrict = false)
                }
            )
        }

        // region Internal

        internal const val FLAGS_NETWORK_EXECUTOR_NAME = "flags-network"
        internal const val FLAGS_STATE_NOTIFICATION_EXECUTOR_NAME = "flags-state-notifications"

        @Suppress("LongMethod")
        internal fun createInternal(
            configuration: FlagsConfiguration,
            featureSdkCore: FeatureSdkCore,
            flagsFeature: FlagsFeature,
            name: String
        ): FlagsClient {
            val networkExecutorService = featureSdkCore.createSingleThreadExecutorService(
                executorContext = FLAGS_NETWORK_EXECUTOR_NAME
            )
            val stateNotificationExecutorService = featureSdkCore.createSingleThreadExecutorService(
                executorContext = FLAGS_STATE_NOTIFICATION_EXECUTOR_NAME
            )

            val datadogContext = (featureSdkCore as InternalSdkCore).getDatadogContext()

            // Validate required context
            if (datadogContext == null) {
                flagsFeature.logErrorWithPolicy(
                    message = "Missing DatadogContext from SDK core",
                    level = InternalLogger.Level.ERROR,
                    shouldCrashInStrict = true
                )

                return NoOpFlagsClient(
                    name = name,
                    reason = "Failed to create client - missing DatadogContext",
                    logWithPolicy = { message, level ->
                        flagsFeature.logErrorWithPolicy(message, level, shouldCrashInStrict = false)
                    }
                )
            } else {
                // Build the various dependencies for the [DatadogFlagsClient]
                val applicationId = flagsFeature.applicationId

                val flagsContext = FlagsContext.create(
                    datadogContext = datadogContext,
                    applicationId = applicationId,
                    flagsConfiguration = configuration
                )

                val datastore = (featureSdkCore as FeatureSdkCore).getFeature(FLAGS_FEATURE_NAME)
                    ?.dataStore
                val flagsRepository = if (datastore != null) {
                    DefaultFlagsRepository(
                        featureSdkCore = featureSdkCore,
                        dataStore = datastore,
                        instanceName = name
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

                val flagStateManager = FlagsStateManager(
                    DDCoreSubscription.create(),
                    stateNotificationExecutorService,
                    featureSdkCore.internalLogger
                )

                val evaluationsManager = EvaluationsManager(
                    executorService = networkExecutorService,
                    internalLogger = featureSdkCore.internalLogger,
                    flagsRepository = flagsRepository,
                    assignmentsReader = assignmentsDownloader,
                    precomputeMapper = precomputeMapper,
                    flagStateManager = flagStateManager
                )

                val rumEvaluationLogger = createRumEvaluationLogger(featureSdkCore)

                return DatadogFlagsClient(
                    featureSdkCore = featureSdkCore,
                    evaluationsManager = evaluationsManager,
                    flagsRepository = flagsRepository,
                    flagsConfiguration = configuration,
                    rumEvaluationLogger = rumEvaluationLogger,
                    processor = flagsFeature.processor,
                    flagStateManager = flagStateManager
                )
            }
        }

        private fun createRumEvaluationLogger(featureSdkCore: FeatureSdkCore): RumEvaluationLogger {
            val rumFeatureScope = featureSdkCore.getFeature(RUM_FEATURE_NAME)

            return rumFeatureScope?.let {
                DefaultRumEvaluationLogger(it)
            } ?: NoOpRumEvaluationLogger()
        }
    }
}
