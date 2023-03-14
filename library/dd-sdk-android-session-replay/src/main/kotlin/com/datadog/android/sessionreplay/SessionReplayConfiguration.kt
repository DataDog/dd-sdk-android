/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.core.configuration.Configuration

/**
 * An object describing the configuration of the Session Replay.
 */
data class SessionReplayConfiguration
internal constructor(
    internal val customEndpointUrl: String?,
    internal val privacy: SessionReplayPrivacy
) {

    /**
     * A Builder class for a [SessionReplayConfiguration].
     */
    class Builder {
        private var customEndpointUrl: String? = null
        private var privacy = SessionReplayPrivacy.MASK_ALL

        /**
         * Let the Session Replay target a custom server.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            customEndpointUrl = endpoint
            return this
        }

        /**
         * Sets the privacy rule for the Session Replay feature.
         * If not specified all the elements will be masked by default (MASK_ALL).
         * @see SessionReplayPrivacy.ALLOW_ALL
         * @see SessionReplayPrivacy.MASK_ALL
         */
        fun setPrivacy(privacy: SessionReplayPrivacy): Builder {
            this.privacy = privacy
            return this
        }

        /**
         * Builds a [Configuration] based on the current state of this Builder.
         */
        fun build(): SessionReplayConfiguration {
            return SessionReplayConfiguration(
                customEndpointUrl = customEndpointUrl,
                privacy = privacy
            )
        }
    }
}
