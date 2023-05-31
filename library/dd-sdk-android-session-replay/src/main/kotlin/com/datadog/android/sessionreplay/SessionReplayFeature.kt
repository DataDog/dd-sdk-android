/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Application
import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.sessionreplay.internal.LifecycleCallback
import com.datadog.android.sessionreplay.internal.NoOpLifecycleCallback
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.SessionReplayRecordCallback
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRequestFactory
import com.datadog.android.sessionreplay.internal.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
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
    customEndpointUrl: String?,
    internal val privacy: SessionReplayPrivacy,
    private val sessionReplayCallbackProvider: (SdkCore, RecordWriter) -> LifecycleCallback
) : StorageBackedFeature, FeatureEventReceiver {

    internal constructor(
        customEndpointUrl: String?,
        privacy: SessionReplayPrivacy,
        customMappers: List<MapperTypeWrapper>,
        customOptionSelectorDetectors: List<OptionSelectorDetector>
    ) : this(
        customEndpointUrl,
        privacy,
        { sdkCore, recordWriter ->
            SessionReplayLifecycleCallback(
                rumContextProvider = SessionReplayRumContextProvider(sdkCore),
                privacy = privacy,
                recordWriter = recordWriter,
                timeProvider = SessionReplayTimeProvider(sdkCore),
                recordCallback = SessionReplayRecordCallback(sdkCore),
                customMappers = customMappers,
                customOptionSelectorDetectors = customOptionSelectorDetectors
            )
        }
    )

    internal lateinit var appContext: Context
    internal lateinit var sdkCore: SdkCore
    private var isRecording = AtomicBoolean(false)
    internal var sessionReplayCallback: LifecycleCallback = NoOpLifecycleCallback()

    internal var dataWriter: RecordWriter = NoOpRecordWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.SESSION_REPLAY_FEATURE_NAME

    override fun onInitialize(
        sdkCore: SdkCore,
        appContext: Context
    ) {
        this.sdkCore = sdkCore
        this.appContext = appContext

        sdkCore.setEventReceiver(SESSION_REPLAY_FEATURE_NAME, this)
        dataWriter = createDataWriter()
        sessionReplayCallback = sessionReplayCallbackProvider(sdkCore, dataWriter)
        initialized.set(true)
        startRecording()
    }

    override val requestFactory: RequestFactory =
        SessionReplayRequestFactory(customEndpointUrl)

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        stopRecording()
        dataWriter = NoOpRecordWriter()
        sessionReplayCallback = NoOpLifecycleCallback()
        initialized.set(false)
    }

    // endregion

    private fun createDataWriter(): RecordWriter {
        return SessionReplayRecordWriter(sdkCore)
    }

    // region SessionReplayFeature

    /**
     * Stops the session recording.
     *
     * Session Replay feature will only work for recorded
     * sessions.
     */
    fun stopRecording() {
        if (isRecording.getAndSet(false)) {
            unregisterCallback(appContext)
        }
    }

    /**
     * Starts/resumes the session recording.
     *
     * Session Replay feature will only work for recorded
     * sessions.
     */
    fun startRecording() {
        if (!initialized.get()) {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                CANNOT_START_RECORDING_NOT_INITIALIZED
            )
            return
        }
        if (!isRecording.getAndSet(true)) {
            registerCallback(appContext)
        }
    }

    // endregion

    // region EventReceiver

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            sdkCore._internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName)
            )
            return
        }

        if (event[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY] == RUM_SESSION_RENEWED_BUS_MESSAGE) {
            val keepSession = event[RUM_KEEP_SESSION_BUS_MESSAGE_KEY] as? Boolean

            if (keepSession == null) {
                sdkCore._internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    EVENT_MISSING_MANDATORY_FIELDS
                )
                return
            }

            if (keepSession) {
                startRecording()
            } else {
                stopRecording()
            }
        } else {
            sdkCore._internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
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

    /**
     * A Builder class for a [SessionReplayFeature].
     */
    class Builder {
        private var customEndpointUrl: String? = null
        private var privacy = SessionReplayPrivacy.MASK_ALL
        private var extensionSupport: ExtensionSupport = NoOpExtensionSupport()

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
         * Let the Session Replay target a custom server.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            customEndpointUrl = endpoint
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

        /**
         * Builds a [Configuration] based on the current state of this Builder.
         */
        fun build(): SessionReplayFeature {
            return SessionReplayFeature(
                customEndpointUrl = customEndpointUrl,
                privacy = privacy,
                customMappers = customMappers(),
                customOptionSelectorDetectors = extensionSupport.getOptionSelectorDetectors()
            )
        }
    }

    internal companion object {
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
