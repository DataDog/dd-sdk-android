package com.datadog.android.sessionreplay.internal

import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.sessionreplay.SessionReplayPrivacy

/**
 * An object describing the configuration of the Session Replay.
 */
data class SessionReplayConfiguration
internal constructor(
    internal val endpointUrl: String,
    internal val privacy: SessionReplayPrivacy
) {

    /**
     * A Builder class for a [SessionReplayConfiguration].
     */
    class Builder {
        private var endpointUrl = DatadogEndpoint.SESSION_REPLAY_US1
        private var privacy = SessionReplayPrivacy.MASK_ALL

        /**
         * Let the Session Replay target your preferred Datadog's site.
         */
        fun useSite(site: DatadogSite): Builder {
            endpointUrl = site.sessionReplayEndpoint()
            return this
        }

        /**
         * Let the Session Replay target a custom server.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            endpointUrl = endpoint
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
                endpointUrl = endpointUrl,
                privacy = privacy
            )
        }
    }
}
