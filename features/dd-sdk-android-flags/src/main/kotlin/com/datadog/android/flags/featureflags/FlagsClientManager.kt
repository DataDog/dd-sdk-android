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
import com.datadog.android.flags.featureflags.FlagsClientManager.get
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient

/**
 * Manager for Flags client instances.
 */
object FlagsClientManager {

    private val registeredProviders: MutableMap<SdkCore, FlagsClient> = mutableMapOf()

    /**
     * Identify whether a [FlagsClient] has previously been registered for the given SDK instance.
     *
     * This check is useful in scenarios where more than one component may be responsible
     * for registering a client.
     *
     * @param sdkCore the [SdkCore] instance to check against. If not provided, default instance
     * will be checked.
     * @return whether a client has been registered
     */
    @JvmOverloads
    @JvmStatic
    fun isRegistered(sdkCore: SdkCore = Datadog.getInstance()): Boolean = synchronized(registeredProviders) {
        registeredProviders.containsKey(sdkCore)
    }

    /**
     * Returns the constant [FlagsClient] instance.
     *
     * Until a Flags feature is enabled, a no-op implementation is returned.
     *
     * @return The client associated with the instance given instance, or a no-op client. If SDK
     * instance is not provided, default instance will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun get(sdkCore: SdkCore = Datadog.getInstance()): FlagsClient = synchronized(registeredProviders) {
        val client = registeredProviders[sdkCore]
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
     * Registration is a one-time operation. Once a client has been registered, all attempts at re-registering
     * will return `false`.
     *
     * Every application intending to use the global FlagsClientManager is responsible for registering it once
     * during its initialization.
     *
     * @param client the client to use as global client.
     * @param sdkCore the instance to register the given client with. If not provided, default
     * instance will be used.
     * @return `true` if the client was registered as a result of this call, `false` otherwise.
     */
    internal fun registerIfAbsent(client: FlagsClient, sdkCore: SdkCore = Datadog.getInstance()): Boolean =
        synchronized(registeredProviders) {
            if (registeredProviders.containsKey(sdkCore)) {
                (sdkCore as FeatureSdkCore).internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { "A FlagsClient has already been registered for this SDK instance" }
                )
                false
            } else {
                @Suppress("UnsafeThirdPartyFunctionCall") // User provided callable, let it throw
                registeredProviders[sdkCore] = client
                true
            }
        }

    internal fun unregister(sdkCore: SdkCore = Datadog.getInstance()) {
        synchronized(registeredProviders) {
            registeredProviders.remove(sdkCore)
        }
    }

    internal fun clear() {
        synchronized(registeredProviders) {
            registeredProviders.clear()
        }
    }

    // This method is mainly for test purposes.
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
