/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

/**
 * Configuration options for a FlagsClient instance.
 *
 * @param useContextFromCore Whether to use context from the SDK core for flag evaluation.
 * @param clientKey Identifier for this client instance in the DataStore. Used to namespace
 * flag configurations so each client instance stores its own data within the feature-wide DataStore.
 */
data class FlagsClientConfiguration(
    val useContextFromCore: Boolean = false,
    val clientKey: String = DEFAULT_CLIENT_KEY
) {
    /**
     * A Builder class for a [FlagsClientConfiguration].
     */
    class Builder {
        private var useContextFromCore: Boolean = false
        private var clientKey: String = DEFAULT_CLIENT_KEY

        /**
         * Sets whether to use context from the SDK core for flag evaluation.
         * @param enabled Whether to use context from core.
         */
        fun setUseContextFromCore(enabled: Boolean): Builder {
            this.useContextFromCore = enabled
            return this
        }

        /**
         * Sets the client key identifier for this client instance.
         * @param clientKey The client key to use.
         */
        fun setClientKey(clientKey: String): Builder {
            this.clientKey = clientKey
            return this
        }

        /**
         * Builds a [FlagsClientConfiguration] based on the current state of this Builder.
         * @return a new [FlagsClientConfiguration] instance.
         */
        fun build(): FlagsClientConfiguration = FlagsClientConfiguration(
            useContextFromCore = useContextFromCore,
            clientKey = clientKey
        )
    }

    companion object {
        const val DEFAULT_CLIENT_KEY = "default"

        /**
         * Default configuration for FlagsClient instances.
         */
        val DEFAULT = FlagsClientConfiguration()
    }
}