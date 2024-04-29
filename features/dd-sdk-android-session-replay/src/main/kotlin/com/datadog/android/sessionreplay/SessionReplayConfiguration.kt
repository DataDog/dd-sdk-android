/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import androidx.annotation.FloatRange
import com.datadog.android.sessionreplay.internal.NoOpExtensionSupport
import com.datadog.android.sessionreplay.internal.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper

/**
 * Describes configuration to be used for the Session Replay feature.
 */
data class SessionReplayConfiguration internal constructor(
    internal val customEndpointUrl: String?,
    internal val privacy: SessionReplayPrivacy,
    internal val customMappers: List<MapperTypeWrapper>,
    internal val customOptionSelectorDetectors: List<OptionSelectorDetector>,
    internal val sampleRate: Float
) {

    /**
     * A Builder class for a [SessionReplayConfiguration].
     * @param sampleRate must be a value between 0 and 100. A value of 0
     * means no session will be recorded, 100 means all sessions will be recorded.
     */
    class Builder(@FloatRange(from = 0.0, to = 100.0) private val sampleRate: Float) {
        private var customEndpointUrl: String? = null
        private var privacy = SessionReplayPrivacy.MASK
        private var extensionSupport: ExtensionSupport = NoOpExtensionSupport()

        /**
         * Adds an extension support implementation. This is mostly used when you want to provide
         * different behaviour of the Session Replay when using other Android UI frameworks
         * than the default ones.
         * @see [ExtensionSupport.getLegacyCustomViewMappers]
         */
        fun addExtensionSupport(extensionSupport: ExtensionSupport): Builder {
            this.extensionSupport = extensionSupport
            return this
        }

        /**
         * Let the Session Replay target a custom server.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            customEndpointUrl = endpoint
            return this
        }

        /**
         * Sets the privacy rule for the Session Replay feature.
         * If not specified all the elements will be masked by default (MASK).
         * @see SessionReplayPrivacy.ALLOW
         * @see SessionReplayPrivacy.MASK
         * @see SessionReplayPrivacy.MASK_USER_INPUT
         */
        fun setPrivacy(privacy: SessionReplayPrivacy): Builder {
            this.privacy = privacy
            return this
        }

        /**
         * Builds a [SessionReplayConfiguration] based on the current state of this Builder.
         */
        fun build(): SessionReplayConfiguration {
            return SessionReplayConfiguration(
                customEndpointUrl = customEndpointUrl,
                privacy = privacy,
                customMappers = customMappers(),
                customOptionSelectorDetectors = extensionSupport.getOptionSelectorDetectors(),
                sampleRate = sampleRate
            )
        }

        private fun customMappers(): List<MapperTypeWrapper> {
            return extensionSupport.getCustomViewMappers()
                .map { typeMapperPair ->
                    MapperTypeWrapper(typeMapperPair.key, typeMapperPair.value)
                }
        }
    }
}
