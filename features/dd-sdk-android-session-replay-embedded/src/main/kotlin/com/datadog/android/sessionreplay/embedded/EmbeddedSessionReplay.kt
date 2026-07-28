/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded

import android.view.View
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedRecordsWriter
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedReplayFeature
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedViewRegistry
import com.google.gson.JsonObject

/**
 * An entry point to record Session Replay wireframes coming from a view rendered by another,
 * embedded rendering engine (e.g. Flutter, React Native) inside a native Android screen.
 *
 * This API is meant to be called by that engine's own SDK integration code (the Kotlin/Java glue
 * that embeds the engine's view), not by application code directly. It never needs to know how the
 * native Session Replay recorder identifies that view -- only that the same [View] instance is
 * given to [register] as is later embedded, and that [writeRecords] is called with the same
 * `engineKey` used at registration time.
 */
object EmbeddedSessionReplay {

    /**
     * Registers the native [View] backing an embedded engine instance, so that
     * [writeRecords] can later correlate that engine's own records with the placeholder wireframe
     * the native Session Replay recorder emits for this exact [view].
     *
     * Must be called once, as soon as the [view] is created/attached, before any records are
     * forwarded via [writeRecords] for the same [engineKey].
     *
     * @param view the native view backing the embedded engine instance.
     * @param engineKey an opaque, stable identity for this engine instance (e.g. a Flutter
     * engine's binary messenger). Must be the same instance passed to [writeRecords] and
     * [unregister] for this engine.
     */
    @JvmStatic
    fun register(view: View, engineKey: Any) {
        EmbeddedViewRegistry.register(engineKey, view)
    }

    /**
     * Forgets the registration made in [register] for the given [engineKey]. Optional: a
     * registration is held with a weak reference to its [View], so it is naturally forgotten once
     * that view is garbage collected even if this is never called.
     *
     * @param engineKey the same opaque engine identity passed to [register].
     */
    @JvmStatic
    fun unregister(engineKey: Any) {
        EmbeddedViewRegistry.unregister(engineKey)
    }

    /**
     * Forwards a batch of Session Replay records recorded by an embedded engine's own SDK, so they
     * can be composited by the Datadog player into the placeholder wireframe the native recorder
     * emitted for the view registered under [engineKey] in [register].
     *
     * If [engineKey] was never registered (or its view has since been garbage collected), the
     * records are dropped: the player would have no placeholder to correlate them with anyway.
     *
     * @param engineKey the same opaque engine identity passed to [register].
     * @param viewId the RUM view id these records belong to, as tracked by the embedded engine's
     * own SDK.
     * @param records the raw Session Replay records produced by the embedded engine's own SDK.
     * @param sdkCore SDK instance to write the records into.
     */
    @JvmStatic
    @JvmOverloads
    fun writeRecords(
        engineKey: Any,
        viewId: String,
        records: List<JsonObject>,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val featureSdkCore = sdkCore as FeatureSdkCore
        val slotId = EmbeddedViewRegistry.resolveSlotId(engineKey)
        if (slotId == null) {
            featureSdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { UNREGISTERED_ENGINE_KEY_WARNING_MESSAGE }
            )
            return
        }
        val embeddedReplayFeature = resolveReplayFeature(featureSdkCore) ?: return
        EmbeddedRecordsWriter(
            sdkCore = featureSdkCore,
            dataWriter = embeddedReplayFeature.dataWriter,
            rumContextProvider = embeddedReplayFeature.rumContextProvider
        ).write(slotId, viewId, records)
    }

    // Prevents two racing threads from both constructing and registering their own
    // EmbeddedReplayFeature, leaking the loser's storage/upload-scheduler resources.
    private val resolveReplayFeatureLock = Any()

    // Avoids re-logging on every writeRecords() call made before Datadog.initialize() completes.
    private val loggedSessionReplayFeatureMissingFor = mutableSetOf<FeatureSdkCore>()

    private fun resolveReplayFeature(sdkCore: FeatureSdkCore): EmbeddedReplayFeature? {
        existingReplayFeature(sdkCore)?.let { return it }
        synchronized(resolveReplayFeatureLock) {
            existingReplayFeature(sdkCore)?.let { return it }
            val sessionReplayFeature = sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
                ?.unwrap<StorageBackedFeature>()
            return if (sessionReplayFeature != null) {
                EmbeddedReplayFeature(sdkCore, sessionReplayFeature.requestFactory)
                    .apply { sdkCore.registerFeature(this) }
            } else {
                if (loggedSessionReplayFeatureMissingFor.add(sdkCore)) {
                    sdkCore.internalLogger.log(
                        InternalLogger.Level.INFO,
                        InternalLogger.Target.USER,
                        { SESSION_REPLAY_FEATURE_MISSING_INFO }
                    )
                }
                null
            }
        }
    }

    private fun existingReplayFeature(sdkCore: FeatureSdkCore): EmbeddedReplayFeature? {
        return sdkCore.getFeature(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
            ?.unwrap<StorageBackedFeature>() as? EmbeddedReplayFeature
    }

    internal const val SESSION_REPLAY_FEATURE_MISSING_INFO =
        "Session replay feature is not registered, will ignore embedded replay records."
    internal const val UNREGISTERED_ENGINE_KEY_WARNING_MESSAGE =
        "Trying to write embedded Session Replay records for an engine that was never " +
            "registered (or whose view has been garbage collected) via " +
            "EmbeddedSessionReplay.register(). The records will be dropped."
}
