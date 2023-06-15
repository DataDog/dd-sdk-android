/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Application
import android.content.Context
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.SessionReplayRecordCallback
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRequestFactory
import com.datadog.android.sessionreplay.internal.storage.NoOpRecordWriter
import com.datadog.android.sessionreplay.internal.storage.SessionReplayRecordWriter
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session Replay feature class, which needs to be registered with Datadog SDK instance.
 */
class SessionReplayFeature internal constructor(
    configuration: SessionReplayConfiguration,
    private val sessionReplayRecorderProvider: (SdkCore, RecordWriter, Application) -> Recorder,
    private val rateBasedSampler: Sampler = RateBasedSampler(configuration.sampleRate)
) : StorageBackedFeature, FeatureEventReceiver {

    /**
     * Creates Session Replay feature.
     *
     * @param configuration Session Replay configuration, which can be created
     * using [SessionReplayConfiguration.Builder].
     */
    constructor(configuration: SessionReplayConfiguration) : this(
        configuration,
        { sdkCore, recordWriter, application ->
            SessionReplayRecorder(
                application,
                rumContextProvider = SessionReplayRumContextProvider(sdkCore),
                privacy = configuration.privacy,
                recordWriter = recordWriter,
                timeProvider = SessionReplayTimeProvider(sdkCore),
                recordCallback = SessionReplayRecordCallback(sdkCore),
                customMappers = configuration.customMappers(),
                customOptionSelectorDetectors =
                configuration.extensionSupport.getOptionSelectorDetectors()
            )
        }
    )

    private lateinit var application: Application
    private lateinit var sdkCore: SdkCore
    private var isRecording = AtomicBoolean(false)
    internal var sessionReplayRecorder: Recorder = NoOpRecorder()
    internal var dataWriter: RecordWriter = NoOpRecordWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.SESSION_REPLAY_FEATURE_NAME

    override fun onInitialize(
        sdkCore: SdkCore,
        appContext: Context
    ) {
        if (appContext !is Application) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                REQUIRES_APPLICATION_CONTEXT_WARN_MESSAGE
            )
            return
        }

        this.sdkCore = sdkCore
        this.application = appContext

        sdkCore.setEventReceiver(SESSION_REPLAY_FEATURE_NAME, this)
        dataWriter = createDataWriter()
        sessionReplayRecorder = sessionReplayRecorderProvider(sdkCore, dataWriter, appContext)
        sessionReplayRecorder.registerCallbacks()
        initialized.set(true)
    }

    override val requestFactory: RequestFactory =
        SessionReplayRequestFactory(configuration.endpointUrl)

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        stopRecording()
        sessionReplayRecorder.unregisterCallbacks()
        dataWriter = NoOpRecordWriter()
        sessionReplayRecorder = NoOpRecorder()
        initialized.set(false)
    }

    // endregion

    // region EventReceiver

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName)
            )
            return
        }

        handleRumSession(event)
    }

    // endregion

    // region Internal

    private fun handleRumSession(sessionMetadata: Map<*, *>) {
        if (sessionMetadata[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY] ==
            RUM_SESSION_RENEWED_BUS_MESSAGE
        ) {
            checkStatusAndApplySample(sessionMetadata)
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(
                    Locale.US,
                    sessionMetadata[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY]
                )
            )
        }
    }

    private fun checkStatusAndApplySample(sessionMetadata: Map<*, *>) {
        val keepSession = sessionMetadata[RUM_KEEP_SESSION_BUS_MESSAGE_KEY] as? Boolean

        if (keepSession == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                EVENT_MISSING_MANDATORY_FIELDS
            )
            return
        }

        if (keepSession && rateBasedSampler.sample()) {
            startRecording()
        } else {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                SESSION_SAMPLED_OUT_MESSAGE
            )
            stopRecording()
        }
    }

    internal fun startRecording() {
        if (!initialized.get()) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                CANNOT_START_RECORDING_NOT_INITIALIZED
            )
            return
        }
        if (!isRecording.getAndSet(true)) {
            sessionReplayRecorder.resumeRecorders()
        }
    }

    private fun createDataWriter(): RecordWriter {
        return SessionReplayRecordWriter(sdkCore)
    }

    internal fun stopRecording() {
        if (isRecording.getAndSet(false)) {
            sessionReplayRecorder.stopRecorders()
        }
    }

    // endregion

    internal companion object {
        internal const val REQUIRES_APPLICATION_CONTEXT_WARN_MESSAGE = "Session Replay could not " +
            "be initialized without the Application context."
        internal const val SESSION_SAMPLED_OUT_MESSAGE = "This session was sampled out from" +
            " recording. No replay will be provided for it."
        internal const val UNSUPPORTED_EVENT_TYPE =
            "Session Replay feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "Session Replay feature received an event with unknown value of \"type\" property=%s."
        internal const val EVENT_MISSING_MANDATORY_FIELDS = "Session Replay feature received an " +
            "event where one or more mandatory (keepSession) fields" +
            " are either missing or have wrong type."
        internal const val CANNOT_START_RECORDING_NOT_INITIALIZED =
            "Cannot start session recording, because Session Replay feature is not initialized."
        const val SESSION_REPLAY_FEATURE_NAME = "session-replay"
        const val SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY = "type"
        const val RUM_SESSION_RENEWED_BUS_MESSAGE = "rum_session_renewed"
        const val RUM_KEEP_SESSION_BUS_MESSAGE_KEY = "keepSession"
    }
}
