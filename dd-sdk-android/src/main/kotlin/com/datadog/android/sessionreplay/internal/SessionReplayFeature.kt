/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.sessionreplay.LifecycleCallback
import com.datadog.android.sessionreplay.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRecordPersistenceStrategy
import com.datadog.android.sessionreplay.internal.domain.SessionReplaySerializedRecordWriter
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.atomic.AtomicBoolean

internal class SessionReplayFeature(
    coreFeature: CoreFeature,
    storage: Storage,
    uploader: DataUploader,
    sdkCore: SdkCore,
    private val sessionReplayCallbackProvider:
        (PersistenceStrategy<String>, Configuration.Feature.SessionReplay) ->
        LifecycleCallback = { persistenceStrategy, configuration ->
            SessionReplayLifecycleCallback(
                SessionReplayRumContextProvider(),
                configuration.privacy,
                SessionReplaySerializedRecordWriter(persistenceStrategy.getWriter()),
                SessionReplayTimeProvider(coreFeature.contextProvider),
                SessionReplayRecordCallback(sdkCore)
            )
        }
) : SdkFeature<String, Configuration.Feature.SessionReplay>(coreFeature, storage, uploader) {

    internal lateinit var appContext: Context
    private var isRecording = AtomicBoolean(false)
    internal var sessionReplayCallback: LifecycleCallback = NoOpLifecycleCallback()

    // region SDKFeature

    override fun onInitialize(
        context: Context,
        configuration: Configuration.Feature.SessionReplay
    ) {
        super.onInitialize(context, configuration)
        appContext = context
        sessionReplayCallback = sessionReplayCallbackProvider(persistenceStrategy, configuration)
        startRecording()
    }

    override fun onStop() {
        super.onStop()
        stopRecording()
        sessionReplayCallback = NoOpLifecycleCallback()
    }

    override fun createPersistenceStrategy(
        context: Context,
        storage: Storage,
        configuration: Configuration.Feature.SessionReplay
    ): PersistenceStrategy<String> {
        return SessionReplayRecordPersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            coreFeature.localDataEncryption,
            coreFeature.buildFilePersistenceConfig(),
            storage
        )
    }

    // endregion

    // region SessionReplayFeature

    internal fun stopRecording() {
        if (isRecording.getAndSet(false)) {
            unregisterCallback(appContext)
        }
    }

    internal fun startRecording() {
        if (!isRecording.getAndSet(true)) {
            registerCallback(appContext)
        }
    }

    // endregion

    // region Internal

    private fun registerCallback(context: Context) {
        if (context is Application) {
            sessionReplayCallback.register(context)
        }
    }

    private fun unregisterCallback(context: Context) {
        if (context is Application) {
            sessionReplayCallback.unregisterAndStopRecorders(context)
        }
    }

    // endregion

    companion object {
        const val SESSION_REPLAY_FEATURE_NAME = "session-replay"
        const val IS_RECORDING_CONTEXT_KEY = "is_recording"
    }
}
