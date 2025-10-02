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
     * Resolves a double flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The double value of the flag, or the default value if unavailable.
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
         * Gets the client with the specified name from the SDK core.
         *
         * If the client doesn't exist:
         * - For default client (name == "default"): creates and returns it
         * - For custom names: returns a NOPClient
         *
         * @param name the client name. Defaults to "default".
         * @param sdkCore the SDK instance. Defaults to the default Datadog instance.
         * @return the [FlagsClient] with the specified name, a newly created default client,
         * or a NOPClient if a custom name was requested but not found.
         */
        @JvmOverloads
        @JvmStatic
        fun get(name: String = DEFAULT_CLIENT_NAME, sdkCore: SdkCore = Datadog.getInstance()): FlagsClient {
            val key = ClientKey(sdkCore, name)

            synchronized(registeredClients) {
                var client = registeredClients[key]

                // If requesting default client and it doesn't exist, create it
                if (client == null && name == DEFAULT_CLIENT_NAME) {
                    client = createInternal(sdkCore)
                    registerIfAbsent(client, key)
                } else if (client == null) {
                    // Custom name not found, return NOP
                    val errorMsg = "No FlagsClient with name '$name' for SDK instance " +
                        "with name \${sdkCore.name} found, returning no-op implementation."
                    val logger = (sdkCore as? FeatureSdkCore)?.internalLogger
                    logger?.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.USER,
                        { errorMsg }
                    )
                    client = NoOpFlagsClient(logger)
                }

                return client
            }
        }

        // region Internal

        internal fun registerIfAbsent(client: FlagsClient, clientKey: ClientKey) = synchronized(registeredClients) {
            if (registeredClients.containsKey(clientKey)) {
                (clientKey.sdkCore as FeatureSdkCore).internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { "A FlagsClient has already been registered for this SDK instance" }
                )
            } else {
                registeredClients[clientKey] = client
            }
        }

        // For testing
        internal fun registerIfAbsent(client: FlagsClient, sdkCore: FeatureSdkCore, clientName: String) =
            registerIfAbsent(client, ClientKey(sdkCore, clientName))

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

        /**
         * Creates a client with the specified name for the SDK core.
         *
         * If a client with this name already exists, returns the existing client and logs a warning.
         * This method never returns null or crashes - it always returns a valid FlagsClient.
         * If creation fails, a NOPClient is returned.
         *
         * @param name the client name. Defaults to "default".
         * @param sdkCore the SDK instance. Defaults to the default Datadog instance.
         * @return the created or existing [FlagsClient], or a NOPClient if creation fails.
         */
        @JvmOverloads
        @JvmStatic
        fun create(name: String = DEFAULT_CLIENT_NAME, sdkCore: SdkCore = Datadog.getInstance()): FlagsClient {
            val key = ClientKey(sdkCore, name)

            synchronized(registeredClients) {
                // If client already exists, return it with a warning
                val existingClient = registeredClients[key]
                if (existingClient != null) {
                    val logger = (sdkCore as? FeatureSdkCore)?.internalLogger
                    logger?.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.USER,
                        {
                            "A FlagsClient with name '$name' has already been created for this SDK instance. " +
                                "Returning existing client."
                        }
                    )
                    return existingClient
                }

                // Create new client
                val newClient = createInternal(sdkCore)
                registerIfAbsent(newClient, key)
                return newClient
            }
        }

        internal fun createInternal(sdkCore: SdkCore): FlagsClient {
            val flagsFeature = (sdkCore as FeatureSdkCore).getFeature(FLAGS_FEATURE_NAME)?.unwrap<FlagsFeature>()

            if (flagsFeature == null) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { FLAGS_NOT_ENABLED_MESSAGE }
                )
                return NoOpFlagsClient(sdkCore.internalLogger)
            }
            return createInternal(sdkCore, flagsFeature)
        }

        internal fun createInternal(sdkCore: FeatureSdkCore, flagsFeature: FlagsFeature): FlagsClient {
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

                return NoOpFlagsClient(sdkCore.internalLogger)
            } else {
                // Create FlagsContext combining core SDK context with feature configuration
                val flagsContext = FlagsContext.create(
                    datadogContext,
                    applicationId,
                    flagsFeature.flagsConfiguration
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
