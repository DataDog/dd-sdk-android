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
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.sessionreplay.LifecycleCallback
import com.datadog.android.sessionreplay.RecordWriter
import com.datadog.android.sessionreplay.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.storage.NoOpRecordWriter
import com.datadog.android.sessionreplay.internal.storage.SessionReplayRecordWriter
import com.datadog.android.sessionreplay.internal.time.SessionReplayTimeProvider
import com.datadog.android.v2.api.FeatureEventReceiver
import com.datadog.android.v2.api.SdkCore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal class SessionReplayFeature(
    private val coreFeature: CoreFeature,
    private val sdkCore: SdkCore,
    private val sessionReplayCallbackProvider:
        (RecordWriter, Configuration.Feature.SessionReplay) ->
        LifecycleCallback = { recordWriter, configuration ->
            SessionReplayLifecycleCallback(
                SessionReplayRumContextProvider(sdkCore),
                configuration.privacy,
                recordWriter,
                SessionReplayTimeProvider(coreFeature.contextProvider),
                SessionReplayRecordCallback(sdkCore)
            )
        }
) : FeatureEventReceiver {

    internal lateinit var appContext: Context
    private var isRecording = AtomicBoolean(false)
    internal var sessionReplayCallback: LifecycleCallback = NoOpLifecycleCallback()

    internal var dataWriter: RecordWriter = NoOpRecordWriter()
    internal val initialized = AtomicBoolean(false)

    // region SDKFeature

    internal fun initialize(
        context: Context,
        configuration: Configuration.Feature.SessionReplay
    ) {
        sdkCore.setEventReceiver(SESSION_REPLAY_FEATURE_NAME, this)
        appContext = context
        dataWriter = createDataWriter()
        sessionReplayCallback = sessionReplayCallbackProvider(dataWriter, configuration)
        initialized.set(true)
        startRecording()
    }

    internal fun stop() {
        stopRecording()
        dataWriter = NoOpRecordWriter()
        sessionReplayCallback = NoOpLifecycleCallback()
        initialized.set(false)
    }

    private fun createDataWriter(): RecordWriter {
        return SessionReplayRecordWriter(sdkCore)
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

    // region EventReceiver

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            devLogger.w(UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName))
            return
        }

        if (event[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY] == RUM_SESSION_RENEWED_BUS_MESSAGE) {
            val keepSession = event[RUM_KEEP_SESSION_BUS_MESSAGE_KEY] as? Boolean

            if (keepSession == null) {
                devLogger.w(EVENT_MISSING_MANDATORY_FIELDS)
                return
            }

            if (keepSession) {
                startRecording()
            } else {
                stopRecording()
            }
        } else {
            devLogger.w(
                UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(
                    Locale.US,
                    event[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY]
                )
            )
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
        internal const val UNSUPPORTED_EVENT_TYPE =
            "Session Replay feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "Session Replay feature received an event with unknown value of \"type\" property=%s."
        internal const val EVENT_MISSING_MANDATORY_FIELDS = "Session Replay feature received an " +
            "event where one or more mandatory (keepSession) fields" +
            " are either missing or have wrong type."
        const val SESSION_REPLAY_FEATURE_NAME = "session-replay"
        const val SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY = "type"
        const val RUM_SESSION_RENEWED_BUS_MESSAGE = "rum_session_renewed"
        const val RUM_KEEP_SESSION_BUS_MESSAGE_KEY = "keepSession"
        const val IS_RECORDING_CONTEXT_KEY = "is_recording"
    }
}
