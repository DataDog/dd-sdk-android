/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal

import android.content.Context
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.sessionreplay.embedded.internal.rum.EmbeddedRumEventContextProvider
import com.datadog.android.sessionreplay.embedded.internal.storage.EmbeddedReplayDataWriter
import com.datadog.android.sessionreplay.embedded.internal.storage.EmbeddedReplayEventSerializer
import com.google.gson.JsonObject

/**
 * A separate, storage-backed feature for records coming from an embedded engine's own SDK (mirrors
 * the existing WebView-replay feature). Kept separate from the core Session Replay feature so this
 * module can register/unregister/batch independently, while reusing the core feature's
 * [requestFactory] so uploads reach the same intake.
 */
internal class EmbeddedReplayFeature(
    private val sdkCore: FeatureSdkCore,
    override val requestFactory: RequestFactory
) : StorageBackedFeature {

    internal var dataWriter: DataWriter<JsonObject> = NoOpDataWriter()

    // Scoped to this feature instance (one per SdkCore) so its warn-once latch actually persists
    // across the repeated writeRecords() calls an embedded engine makes over a session.
    internal val rumContextProvider = EmbeddedRumEventContextProvider(sdkCore.internalLogger)

    override val name: String = EMBEDDED_REPLAY_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        dataWriter = EmbeddedReplayDataWriter(
            serializer = EmbeddedReplayEventSerializer(),
            internalLogger = sdkCore.internalLogger
        )
    }

    override val storageConfiguration: FeatureStorageConfiguration = STORAGE_CONFIGURATION

    override fun onStop() {
        dataWriter = NoOpDataWriter()
    }

    companion object {
        internal const val EMBEDDED_REPLAY_FEATURE_NAME = "embedded-replay"

        /**
         * Storage configuration with the following parameters:
         * max item size = 10 MB,
         * max batch size = 10 MB, SR intake batch limit is 10MB
         * old batch threshold = 18 hours (default).
         */
        internal val STORAGE_CONFIGURATION: FeatureStorageConfiguration =
            FeatureStorageConfiguration.DEFAULT.copy(
                maxItemSize = 10 * 1024 * 1024,
                maxBatchSize = 10 * 1024 * 1024
            )
    }
}
