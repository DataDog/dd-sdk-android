/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.content.Context
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.sessionreplay.internal.net.ResourceRequestFactory
import com.datadog.android.sessionreplay.internal.storage.NoOpResourceWriter
import com.datadog.android.sessionreplay.internal.storage.SessionReplayResourceWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session Replay feature class, which needs to be registered with Datadog SDK instance.
 */
internal class ResourcesFeature(
    private val sdkCore: FeatureSdkCore
) : StorageBackedFeature {

    internal var dataWriter: ResourceWriter = NoOpResourceWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = SESSION_REPLAY_RESOURCES_FEATURE_NAME

    override val requestFactory: RequestFactory = ResourceRequestFactory(
        customEndpointUrl = null,
        internalLogger = sdkCore.internalLogger
    )

    override fun onInitialize(appContext: Context) {
        dataWriter = createResourceWriter()
        initialized.set(true)
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        STORAGE_CONFIGURATION

    override fun onStop() {
        dataWriter = NoOpResourceWriter()
        initialized.set(false)
    }

    private fun createResourceWriter(): ResourceWriter {
        return SessionReplayResourceWriter(sdkCore)
    }

    // endregion

    internal companion object {

        /**
         * Session Replay storage configuration with the following parameters:
         * max item size = 10 MB,
         * max items per batch = 500,
         * max batch size = 10 MB, SR intake batch limit is 10MB
         * old batch threshold = 18 hours.
         */
        internal val STORAGE_CONFIGURATION: FeatureStorageConfiguration =
            FeatureStorageConfiguration.DEFAULT.copy(
                maxItemSize = 10 * 1024 * 1024,
                maxBatchSize = 10 * 1024 * 1024
            )

        internal const val SESSION_REPLAY_RESOURCES_FEATURE_NAME = "session-replay-resources"
    }
}
