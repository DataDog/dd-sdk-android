/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient

/**
 * An implementation of [Feature] for getting and reporting
 * feature flags to the RUM dashboard.
 */
internal class FlagsFeature(private val sdkCore: FeatureSdkCore, internal val flagsConfiguration: FlagsConfiguration) :
    Feature,
    FeatureContextUpdateReceiver {
    @Volatile
    internal var applicationId: String? = null
        private set

    /**
     * Registry of [FlagsClient] instances by name.
     * This map stores all clients created for this feature instance.
     */
    private val registeredClients: MutableMap<String, FlagsClient> = mutableMapOf()

    /**
     * Gets a registered [FlagsClient] by name.
     *
     * @param name The client name to lookup
     * @return The registered [FlagsClient], or null if not found
     */
    internal fun getClient(name: String): FlagsClient? = synchronized(registeredClients) { registeredClients[name] }

    internal fun getOrRegisterNewClient(name: String, newClientFactory: () -> FlagsClient): FlagsClient {
        synchronized(registeredClients) {
            // Check for existing client
            val existingClient = registeredClients[name]
            if (existingClient != null) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    {
                        "Attempted to create a FlagsClient named '$name', but one already exists. " +
                            "Existing client will be used, and new configuration will be ignored."
                    }
                )
                return existingClient
            }

            // Create and register client
            val newClient = newClientFactory()
            if (newClient !is NoOpFlagsClient) {
                registeredClients[name] = newClient
            }
            return newClient
        }
    }

    internal fun unregisterClient(name: String) = registeredClients.remove(name)

    internal fun clearClients() = registeredClients.clear()

    // region Context Receiver

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            applicationId = context["application_id"].toString()
        }
    }

    override fun onStop() {
        synchronized(registeredClients) {
            registeredClients.clear()
        }
        sdkCore.removeContextUpdateReceiver(this)
    }

    // endregion

    // region Feature

    override val name: String = FLAGS_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        sdkCore.setContextUpdateReceiver(this)
    }

    // endregion
}
