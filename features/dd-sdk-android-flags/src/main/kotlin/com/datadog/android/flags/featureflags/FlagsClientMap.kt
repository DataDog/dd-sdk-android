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
import com.datadog.android.flags.featureflags.FlagsClientMap.instance
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient

/**
 * Client for querying feature flags.
 */
object FlagsClientMap {

    private val registeredClients: MutableMap<SdkCore, FlagsClient> = mutableMapOf()

    // region FlagsClientMap

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

    // endregion

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
    @JvmStatic
    private fun reset() {
        clear()
    }

    // endregion

    // region Constants
    // Constants can be added here if needed
    // endregion
}
