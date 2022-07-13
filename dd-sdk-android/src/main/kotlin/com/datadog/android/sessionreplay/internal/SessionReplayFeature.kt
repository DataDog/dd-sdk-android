/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRecordPersistenceStrategy

internal class SessionReplayFeature(coreFeature: CoreFeature) :
    SdkFeature<Any, Configuration.Feature.SessionReplay>(coreFeature) {

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.SessionReplay
    ): PersistenceStrategy<Any> {
        return SessionReplayRecordPersistenceStrategy(
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            coreFeature.localDataEncryption
        )
    }

    override fun createUploader(configuration: Configuration.Feature.SessionReplay): DataUploader {
        // TODO: This will be added later in RUMM-2273
        return NoOpDataUploader()
    }

    companion object {
        const val SESSION_REPLAY_FEATURE_NAME = "session-replay"
    }
}
