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
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.sessionreplay.LifecycleCallback
import com.datadog.android.sessionreplay.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRecordPersistenceStrategy
import com.datadog.android.sessionreplay.internal.domain.SessionReplaySerializedRecordWriter
import java.util.concurrent.atomic.AtomicBoolean

internal class SessionReplayFeature(
    coreFeature: CoreFeature,
    private val configuration: Configuration.Feature.SessionReplay,
    private val sessionReplayCallbackProvider: (PersistenceStrategy<String>) ->
    LifecycleCallback = {
        SessionReplayLifecycleCallback(
            SessionReplayContextProvider(),
            configuration.privacy,
            SessionReplaySerializedRecordWriter(it.getWriter())
        )
    }
) : SdkFeature<String, Configuration.Feature.SessionReplay>(coreFeature) {

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
        sessionReplayCallback = sessionReplayCallbackProvider(persistenceStrategy)
        startRecording()
    }

    override fun onStop() {
        super.onStop()
        stopRecording()
        sessionReplayCallback = NoOpLifecycleCallback()
    }

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.SessionReplay
    ): PersistenceStrategy<String> {
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
    }
}
