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
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient
import com.datadog.android.flags.featureflags.model.EvaluationContext
import org.json.JSONObject

/**
 * An interface for defining Flags clients.
 */
interface FlagsClient {
    /**
     * Set the context for the client.
     * @param context The evaluation context containing targeting key and attributes.
     */
    fun setContext(context: EvaluationContext)

    /**
     * Resolve a Boolean flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean

    /**
     * Resolve a String flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveStringValue(flagKey: String, defaultValue: String): String

    /**
     * Resolve a Number flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveNumberValue(flagKey: String, defaultValue: Number): Number

    /**
     * Resolve a Int flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveIntValue(flagKey: String, defaultValue: Int): Int

    /**
     * Resolve a Structure flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject

    /**
     * Companion object providing static access to [FlagsClient] instances.
     *
     * This companion manages the registration and retrieval of [FlagsClient] instances
     * per SDK core, ensuring thread-safe access and proper lifecycle management.
     */
    companion object {
        private val registeredClients: MutableMap<SdkCore, FlagsClient> = mutableMapOf()

        /**
         * Returns the [FlagsClient] instance for the given SDK core.
         *
         * This method is thread-safe and will return the same client instance for the same SDK core
         * across multiple calls. If no client has been registered for the given SDK core, a no-op
         * implementation will be returned instead.
         *
         * @param sdkCore the [SdkCore] instance to retrieve the client for. If not provided,
         * the default Datadog SDK instance will be used.
         * @return the [FlagsClient] associated with the given SDK core, or a [NoOpFlagsClient]
         * if no client is registered for this SDK core.
         */
        @JvmOverloads
        @JvmStatic
        fun instance(sdkCore: SdkCore = Datadog.getInstance()): FlagsClient = synchronized(registeredClients) {
            val client = registeredClients[sdkCore]
            if (client == null) {
                val errorMsg = "No FlagsClient for the SDK instance with name \${sdkCore.name} " +
                    "found, returning no-op implementation."
                (sdkCore as? FeatureSdkCore)
                    ?.internalLogger
                    ?.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.USER,
                        { errorMsg }
                    )
                NoOpFlagsClient()
            } else {
                client
            }
        }

        // region Internal

        /**
         * Register a [FlagsClient] with an [SdkCore] to back the behaviour of the [instance] function.
         *
         * This method is thread-safe and implements a one-time registration pattern. Once a client
         * has been registered for a specific SDK core, all subsequent registration attempts for that
         * same core will be rejected and logged as a warning.
         *
         * Applications using the Datadog Flags feature must call this method once during initialization
         * for each SDK core they intend to use.
         *
         * @param client the [FlagsClient] to register for the given SDK core
         * @param sdkCore the [SdkCore] instance to associate with the client. If not provided,
         * the default Datadog SDK instance will be used.
         * @return `true` if the client was successfully registered, `false` if a client was
         * already registered for this SDK core (in which case a warning will be logged).
         */
        internal fun registerIfAbsent(client: FlagsClient, sdkCore: SdkCore = Datadog.getInstance()): Boolean =
            synchronized(registeredClients) {
                if (registeredClients.containsKey(sdkCore)) {
                    (sdkCore as FeatureSdkCore).internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.USER,
                        { "A FlagsClient has already been registered for this SDK instance" }
                    )
                    false
                } else {
                    @Suppress("UnsafeThirdPartyFunctionCall") // User provided callable, let it throw
                    registeredClients[sdkCore] = client
                    true
                }
            }

        /**
         * Unregisters the [FlagsClient] associated with the given SDK core.
         *
         * After calling this method, subsequent calls to [instance] for the same SDK core
         * will return a [NoOpFlagsClient]. This method is thread-safe and will silently
         * do nothing if no client was registered for the given SDK core.
         *
         * @param sdkCore the [SdkCore] instance to unregister the client for. If not provided,
         * the default Datadog SDK instance will be used.
         */
        internal fun unregister(sdkCore: SdkCore = Datadog.getInstance()) {
            synchronized(registeredClients) {
                registeredClients.remove(sdkCore)
            }
        }

        /**
         * Removes all registered [FlagsClient] instances from all SDK cores.
         *
         * After calling this method, all subsequent calls to [instance] will return
         * [NoOpFlagsClient] instances regardless of the SDK core. This method is thread-safe
         * and is primarily intended for testing purposes or SDK shutdown scenarios.
         */
        internal fun clear() {
            synchronized(registeredClients) {
                registeredClients.clear()
            }
        }

        /**
         * Resets the internal state by clearing all registered clients.
         *
         * This method is intended for testing purposes only and should not be used
         * in production code. It delegates to [clear] to remove all client registrations.
         */
        @Suppress("unused")
        private fun reset() {
            clear()
        }

        // endregion
    }
}
