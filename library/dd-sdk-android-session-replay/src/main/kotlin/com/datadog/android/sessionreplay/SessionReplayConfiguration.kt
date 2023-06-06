/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import androidx.annotation.FloatRange
import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper

/**
 * An object describing the configuration of the Session Replay.
 */
data class SessionReplayConfiguration
internal constructor(
    internal val endpointUrl: String,
    internal val privacy: SessionReplayPrivacy,
    internal val extensionSupport: ExtensionSupport,
    internal val samplingRate: Float
) {

    /**
     * A Builder class for a [SessionReplayConfiguration].
     */
    class Builder {
        private var endpointUrl = DatadogEndpoint.SESSION_REPLAY_US1
        private var privacy = SessionReplayPrivacy.MASK_ALL
        private var extensionSupport: ExtensionSupport = NoOpExtensionSupport()
        private var samplingRate: Float = 0f

        /**
         * Adds an extension support implementation. This is mostly used when you want to provide
         * different behaviour of the Session Replay when using other Android UI frameworks
         * than the default ones.
         * @see [ExtensionSupport.getCustomViewMappers]
         */
        fun addExtensionSupport(extensionSupport: ExtensionSupport): Builder {
            this.extensionSupport = extensionSupport
            return this
        }

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
         * Sets the sampling rate for Session Replay recorded Sessions. Please note that this
         * sampling rate will be applied on top of already
         *
         * @param samplingRate the sampling rate must be a value between 0 and 100. A value of 0
         * means no session will be recorded, 100 means all sessions will be recorded.
         */
        fun sessionReplaySampleRate(@FloatRange(from = 0.0, to = 100.0) samplingRate: Float):
            Builder {
            this.samplingRate = samplingRate
            return this
        }

        /**
         * Builds a [Configuration] based on the current state of this Builder.
         */
        fun build(): SessionReplayConfiguration {
            return SessionReplayConfiguration(
                endpointUrl = endpointUrl,
                privacy = privacy,
                extensionSupport = extensionSupport,
                samplingRate = samplingRate
            )
        }
    }

    // region Internal

    internal fun customMappers(): List<MapperTypeWrapper> {
        extensionSupport.getCustomViewMappers().entries.forEach {
            if (it.key.name == privacy.name) {
                return it.value
                    .map { typeMapperPair ->
                        MapperTypeWrapper(typeMapperPair.key, typeMapperPair.value)
                    }
            }
        }
        return emptyList()
    }

    // endregion
}
