/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
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
import com.datadog.android.flags.internal.FlagsFeature.Companion.FLAGS_FEATURE_NAME
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
 *     .useCustomEndpoint("https://custom.endpoint.com")
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
     * With custom configuration:
     * ```
     * val client = FlagsClient.Builder("analytics")
     *     .useCustomEndpoint("https://custom.endpoint.com")
     *     .build()
     * ```
     *
     * With custom SDK core:
     * ```
     * val client = FlagsClient.Builder(name = "analytics", sdkCore = customCore)
     *     .useFlaggingProxy("https://proxy.example.com")
     *     .build()
     * ```
     */
    class Builder {
        private val name: String
        private val sdkCore: SdkCore

        // Optional configuration overrides (null = use feature default)
        private var explicitCustomEndpoint: String? = null
        private var explicitFlaggingProxy: String? = null

        /**
         * Creates a builder for the default [FlagsClient].
         *
         * @param sdkCore the SDK instance to associate with this [FlagsClient]. Defaults to main instance.
         */
        constructor(sdkCore: SdkCore = Datadog.getInstance()) {
            this.name = Companion.DEFAULT_CLIENT_NAME
            this.sdkCore = sdkCore
        }

        /**
         * Creates a builder for a named [FlagsClient].
         *
         * The name is used to identify and retrieve this [FlagsClient] later via [get].
         * Multiple [FlagsClient] instances with different names can coexist within the same [SdkCore].
         *
         * @param name the client name. Must be non-empty.
         * @param sdkCore the SDK instance to associate with this client. Defaults to main instance.
         */
        constructor(name: String, sdkCore: SdkCore = Datadog.getInstance()) {
            this.name = name
            this.sdkCore = sdkCore
        }

        /**
         * Sets a custom endpoint URL for uploading exposure events.
         *
         * If not called, uses the default from [FlagsFeature] configuration.
         *
         * @param endpointUrl the custom endpoint URL, or null to use default.
         * @return this Builder instance for chaining.
         */
        fun useCustomEndpoint(endpointUrl: String?): Builder {
            this.explicitCustomEndpoint = endpointUrl
            return this
        }

        /**
         * Sets a custom endpoint URL for proxying precomputed assignment requests.
         *
         * If not called, uses the default from [FlagsFeature] configuration.
         *
         * @param proxyUrl the custom proxy URL, or null to use default.
         * @return this Builder instance for chaining.
         */
        fun useFlaggingProxy(proxyUrl: String?): Builder {
            this.explicitFlaggingProxy = proxyUrl
            return this
        }

        /**
         * Builds and registers a [FlagsClient] instance.
         *
         * This method:
         * 1. Validates the [FlagsFeature] is enabled
         * 2. Merges builder config with feature defaults (selective override)
         * 3. Creates and registers the client
         * 4. Returns the created client or [NoOpFlagsClient] on failure
         *
         * If a [FlagsClient] with the same name already exists for this [SdkCore]:
         * - Logs a warning (WARN level, USER target)
         * - Returns the existing [FlagsClient]
         *
         * @return the created [FlagsClient], existing client, or [NoOpFlagsClient].
         */
        @Suppress("ReturnCount")
        fun build(): FlagsClient {
            // Validate that the Flags feature is enabled
            val flagsFeature = (sdkCore as? FeatureSdkCore)
                ?.getFeature(FLAGS_FEATURE_NAME)
                ?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                return NoOpFlagsClient(
                    name = name,
                    reason = "Flags feature not enabled",
                    logger = (sdkCore as? FeatureSdkCore)?.internalLogger
                )
            }

            val key = Companion.ClientKey(sdkCore, name)

            synchronized(Companion.registeredClients) {
                // Check for existing client
                val existingClient = Companion.registeredClients[key]
                if (existingClient != null) {
                    logWarning(
                        sdkCore,
                        "Attempted to create a FlagsClient named '$name', but one already exists. " +
                            "Existing client will be used, and new configuration will be ignored."
                    )
                    return existingClient
                }

                // Merge configuration (selective override)
                val featureConfig = flagsFeature.flagsConfiguration
                val mergedConfig = FlagsConfiguration(
                    customEndpointUrl = explicitCustomEndpoint
                        ?: featureConfig.customEndpointUrl,
                    flaggingProxyUrl = explicitFlaggingProxy
                        ?: featureConfig.flaggingProxyUrl
                )

                // Create and register client
                val newClient = Companion.createInternal(mergedConfig, sdkCore as FeatureSdkCore, flagsFeature)
                if (newClient !is NoOpFlagsClient) {
                    Companion.registeredClients[key] = newClient
                }
                return newClient
            }
        }

        private fun logWarning(sdkCore: SdkCore, message: String) {
            (sdkCore as? FeatureSdkCore)?.internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { message }
            )
        }
    }

    /**
     * Companion object providing static access to [FlagsClient] instances.
     *
     * This companion manages the registration and retrieval of [FlagsClient] instances
     * per SDK core, ensuring thread-safe access and proper lifecycle management.
     */
    companion object {
        /**
         * Composite key for storing multiple named clients per SDK core.
         */
        private data class ClientKey(val sdkCore: SdkCore, val name: String)

        private const val DEFAULT_CLIENT_NAME = "default"

        private val registeredClients: MutableMap<ClientKey, FlagsClient> = mutableMapOf()

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
            val key = ClientKey(sdkCore, name)

            synchronized(registeredClients) {
                val client = registeredClients[key]

                if (client == null) {
                    val logger = (sdkCore as? FeatureSdkCore)?.internalLogger

                    // Log at get() level for visibility
                    logger?.log(
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

                    return NoOpFlagsClient(
                        name = name,
                        reason = "Client '$name' not found - get() called before build()",
                        logger = logger
                    )
                }

                return client
            }
        }

        // region Internal

        internal fun registerIfAbsent(client: FlagsClient, sdkCore: FeatureSdkCore, clientName: String) {
            val clientKey = ClientKey(sdkCore, clientName)
            synchronized(registeredClients) {
                if (registeredClients.containsKey(clientKey)) {
                    sdkCore.internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.USER,
                        { "A FlagsClient has already been registered for this SDK instance" }
                    )
                } else {
                    registeredClients[clientKey] = client
                }
            }
        }

        internal fun unregister(sdkCore: SdkCore = Datadog.getInstance()) {
            synchronized(registeredClients) {
                val key = ClientKey(sdkCore, DEFAULT_CLIENT_NAME)
                registeredClients.remove(key)
            }
        }

        internal fun clear() {
            synchronized(registeredClients) {
                registeredClients.clear()
            }
        }

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

        internal const val FLAGS_NOT_ENABLED_MESSAGE = "Flags feature is not enabled"
    }
}
