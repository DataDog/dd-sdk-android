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
    fun setContext(context: EvaluationContext)

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
         * @return the [FlagsClient] associated with the given SDK core, or a no-op client.
         * if no client is registered for this SDK core.
         */
        @JvmOverloads
        @JvmStatic
        fun get(sdkCore: SdkCore = Datadog.getInstance()): FlagsClient = synchronized(registeredClients) {
            val client = registeredClients[sdkCore]
            if (client == null) {
                val errorMsg = "No FlagsClient for the SDK instance with name ${sdkCore.name} " +
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
         * Register a [FlagsClient] with an [SdkCore] to back the behaviour of the [get] function.
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
