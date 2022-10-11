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
import com.datadog.android.core.internal.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.sessionreplay.LifecycleCallback
import com.datadog.android.sessionreplay.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRecordPersistenceStrategy
import com.datadog.android.sessionreplay.internal.domain.SessionReplaySerializedRecordWriter
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.atomic.AtomicBoolean

internal class SessionReplayFeature(
    private val coreFeature: CoreFeature,
    private val storage: Storage,
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
) {

    internal lateinit var appContext: Context
    private var isRecording = AtomicBoolean(false)
    internal var sessionReplayCallback: LifecycleCallback = NoOpLifecycleCallback()

    internal var persistenceStrategy: PersistenceStrategy<String> = NoOpPersistenceStrategy()
    internal val initialized = AtomicBoolean(false)

    // region SDKFeature

    internal fun initialize(
        context: Context,
        configuration: Configuration.Feature.SessionReplay
    ) {
        appContext = context
        persistenceStrategy = createPersistenceStrategy(storage)
        sessionReplayCallback = sessionReplayCallbackProvider(persistenceStrategy, configuration)
        initialized.set(true)
        startRecording()
    }

    internal fun stop() {
        stopRecording()
        persistenceStrategy = NoOpPersistenceStrategy()
        sessionReplayCallback = NoOpLifecycleCallback()
        initialized.set(false)
    }

    private fun createPersistenceStrategy(
        storage: Storage
    ): PersistenceStrategy<String> {
        return SessionReplayRecordPersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.persistenceExecutorService,
            sdkLogger,
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
