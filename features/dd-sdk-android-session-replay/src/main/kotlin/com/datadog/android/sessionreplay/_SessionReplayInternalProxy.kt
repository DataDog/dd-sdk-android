/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.lint.InternalApi
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentEvent
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistration
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry

/**
 * This class exposes internal methods that are used by other Datadog modules and cross platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross platform frameworks.
 *
 * Methods, members, and functionality of this class  are subject to change without notice, as they
 * are not considered part of the public interface of the Datadog SDK.
 */
@InternalApi
@Suppress(
    "ClassName",
    "ClassNaming"
)
class _SessionReplayInternalProxy(private val builder: SessionReplayConfiguration.Builder) {
    /**
     * Sets an internal callback for session replay.
     *
     * @param internalCallback callback instance to override specific parts of the codebase.
     * @return [SessionReplayConfiguration.Builder] instance.
     */
    fun setInternalCallback(
        internalCallback: SessionReplayInternalCallback
    ): SessionReplayConfiguration.Builder {
        return builder.setInternalCallback(internalCallback)
    }

    companion object {
        /**
         * Identifies [view] as a host slot for embedded Session Replay content.
         *
         * The slot identifier is supplied by the embedding SDK and is independent from the native
         * wireframe identifier. Passing `null` permanently detaches the slot and removes its
         * embedded-content wireframe from the next capture. Reassigning the current slot identifier
         * has no effect.
         */
        @UiThread
        fun setEmbeddedContentSlotId(view: View, slotId: String?) {
            val previousSlotId =
                view.getTag(R.id.datadog_session_replay_slot_id) as? String
            if (previousSlotId == slotId) {
                return
            }
            val previousRegistration =
                view.getTag(R.id.datadog_session_replay_slot_registration)
                    as? EmbeddedContentSlotRegistration
            val newRegistration = slotId?.let {
                EmbeddedContentSlotRegistration(it)
            }
            view.setTag(R.id.datadog_session_replay_slot_id, slotId)
            view.setTag(R.id.datadog_session_replay_slot_registration, newRegistration)
            EmbeddedContentSlotRegistry.notifySlotChanged(
                previousRegistration = previousRegistration,
                newRegistration = newRegistration
            )
            @Suppress("UnsafeThirdPartyFunctionCall") // Android documents no exception for scheduling invalidation.
            view.postInvalidateOnAnimation()
        }

        /**
         * Queues Session Replay records produced by an embedded renderer.
         */
        @AnyThread
        fun addEmbeddedContentRecords(
            records: List<Map<String, Any?>>,
            slotId: String,
            viewId: String,
            sdkCore: SdkCore = Datadog.getInstance()
        ) {
            sendEmbeddedContentEvent(
                sdkCore,
                EmbeddedContentEvent.RecordBatch(snapshotRecords(records), slotId, viewId)
            )
        }

        /**
         * Queues a Session Replay resource produced by an embedded renderer.
         */
        @AnyThread
        fun addEmbeddedContentResource(
            identifier: String,
            resourceData: ByteArray,
            mimeType: String,
            sdkCore: SdkCore = Datadog.getInstance()
        ) {
            sendEmbeddedContentEvent(
                sdkCore,
                EmbeddedContentEvent.Resource(identifier, resourceData.copyOf(), mimeType)
            )
        }

        private fun sendEmbeddedContentEvent(sdkCore: SdkCore, event: EmbeddedContentEvent) {
            (sdkCore as? FeatureSdkCore)
                ?.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
                ?.unwrap<SessionReplayFeature>()
                ?.receiveEmbeddedContentEvent(event)
        }

        private fun snapshotRecords(records: List<Map<String, Any?>>): List<Map<String, Any?>> {
            return records.map { record ->
                buildMap {
                    record.forEach { (key, value) ->
                        put(key, snapshotValue(value))
                    }
                }
            }
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // Standard collection mapping and array copies only.
        private fun snapshotValue(value: Any?): Any? {
            return when (value) {
                is Map<*, *> -> buildMap {
                    value.forEach { (key, nestedValue) ->
                        put(key, snapshotValue(nestedValue))
                    }
                }
                is Iterable<*> -> value.map { snapshotValue(it) }
                is Array<*> -> value.map { snapshotValue(it) }
                is ByteArray -> value.copyOf()
                is ShortArray -> value.copyOf()
                is IntArray -> value.copyOf()
                is LongArray -> value.copyOf()
                is FloatArray -> value.copyOf()
                is DoubleArray -> value.copyOf()
                is BooleanArray -> value.copyOf()
                is CharArray -> value.copyOf()
                else -> value
            }
        }
    }
}
