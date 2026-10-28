/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.os.Handler
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.rum.model.ErrorEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

/**
 * Holds a single fatal OOM error on disk until the profiling feature reports whether its OOM
 * profile is attached, so that its profiling status matches exactly.
 *
 * The record moves PENDING → RESERVED (profiling was allowed to write the profile) → RESOLVED, each
 * step stored before the next one starts. Only a PENDING error is written without a profiling status
 * on timeout: once reserved, the outcome reported by profiling is waited for, up to
 * [RESERVED_MAX_AGE_MS].
 */
@Suppress("TooManyFunctions")
internal class PendingOomErrorStore(
    private val sdkCore: FeatureSdkCore,
    private val handler: Handler,
    private val dataWriterProvider: () -> DataWriter<Any>
) {
    private val lock = Any()

    @Volatile
    private var dataStore: DataStoreHandler? = null

    // Guarded by lock.
    private var isLoading = false
    private var isTimedOut = false
    private var record: PendingOomError? = null
    private val queuedRequests = mutableListOf<String>()

    private val timeoutRunnable = Runnable { onTimeout() }

    fun start(dataStore: DataStoreHandler) {
        this.dataStore = dataStore
        synchronized(lock) { isLoading = true }
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        dataStore.value(
            key = KEY,
            version = VERSION,
            callback = object : DataStoreReadCallback<PendingOomError> {
                override fun onSuccess(dataStoreContent: DataStoreContent<PendingOomError>?) {
                    onLoaded(dataStoreContent?.data)
                }

                override fun onFailure() {
                    onLoaded(null)
                }
            },
            deserializer = PendingOomErrorDeserializer()
        )
    }

    fun stop() {
        handler.removeCallbacks(timeoutRunnable)
    }

    /**
     * Holds [event] instead of writing it. Called from the crash write scope: an error held before
     * (crash loop) is written first through [batchWriter], as nothing can be attached to it any more.
     *
     * @param event the fatal OOM error to hold.
     * @param attachedErrorId ID of the error whose OOM profile profiling reports as written.
     * @param batchWriter the crash write scope's batch writer.
     */
    @WorkerThread
    fun hold(event: ErrorEvent, attachedErrorId: String?, batchWriter: EventBatchWriter) {
        val newRecord = PendingOomError(event, State.PENDING, sdkCore.timeProvider.getDeviceTimestampMillis())
        val previous = synchronized(lock) {
            record.also { record = newRecord }
        }
        if (previous != null) {
            val outcome = when (previous.state) {
                State.PENDING -> null
                State.RESERVED -> previous.errorId == attachedErrorId
                State.RESOLVED_ATTACHED -> true
                State.RESOLVED_NOT_ATTACHED -> false
            }
            dataWriterProvider().write(batchWriter, previous.withProfiling(outcome), EventType.CRASH)
            log(InternalLogger.Target.USER, LOG_FLUSHED_CRASH_LOOP)
        }
        dataStore?.setValue(KEY, newRecord, VERSION, null, PendingOomErrorSerializer())
        log(InternalLogger.Target.USER, LOG_HELD)
    }

    fun onAttachRequest(rumErrorId: String) {
        val current = synchronized(lock) {
            if (isLoading) {
                queuedRequests.add(rumErrorId)
                return
            }
            record
        }
        answerAttachRequest(rumErrorId, current)
    }

    fun onResolution(rumErrorId: String, outcome: ProfilerEvent.OomProfileResolution.Outcome) {
        val current = synchronized(lock) { record }?.takeIf { it.errorId == rumErrorId }
        if (current == null || current.state.isResolved) {
            // Already resolved or flushed: only tell profiling to stop resending.
            sendToProfiling(ProfilerEvent.RumOomProfileResolutionAck(rumErrorId))
            return
        }
        val state = if (outcome == ProfilerEvent.OomProfileResolution.Outcome.ATTACHED) {
            State.RESOLVED_ATTACHED
        } else {
            State.RESOLVED_NOT_ATTACHED
        }
        val resolved = current.copy(state = state)
        persist(resolved) {
            if (replaceRecord(current, resolved)) {
                sendToProfiling(ProfilerEvent.RumOomProfileResolutionAck(rumErrorId))
                flush(resolved, state == State.RESOLVED_ATTACHED)
            }
        }
    }

    private fun onLoaded(loaded: PendingOomError?) {
        var stale: PendingOomError? = null
        val (requests, timedOut) = synchronized(lock) {
            isLoading = false
            if (record == null) {
                record = loaded
            } else {
                // An error was held while loading: the loaded one can't get a profile any more.
                stale = loaded
            }
            queuedRequests.toList().also { queuedRequests.clear() } to isTimedOut
        }
        stale?.let { flush(it, it.resolvedOutcome()) }
        val current = loaded?.takeIf { stale == null }
        when (current?.state) {
            null -> Unit
            State.RESOLVED_ATTACHED -> flush(current, true)
            State.RESOLVED_NOT_ATTACHED -> flush(current, false)
            State.RESERVED -> if (current.isExpired()) {
                flush(current, false)
            } else {
                sendToProfiling(ProfilerEvent.RumHeldOomErrorEvent(current.errorId))
            }

            State.PENDING -> if (timedOut) {
                log(InternalLogger.Target.USER, LOG_TIMED_OUT)
                flush(current, null)
            } else {
                sendToProfiling(ProfilerEvent.RumHeldOomErrorEvent(current.errorId))
            }
        }
        requests.forEach { answerAttachRequest(it, synchronized(lock) { record }) }
    }

    private fun onTimeout() {
        val current = synchronized(lock) {
            isTimedOut = true
            if (isLoading) return
            record
        } ?: return
        when {
            current.state == State.PENDING -> {
                log(InternalLogger.Target.USER, LOG_TIMED_OUT)
                flush(current, null)
            }

            current.state == State.RESERVED && current.isExpired() -> flush(current, false)

            else -> Unit
        }
    }

    private fun answerAttachRequest(rumErrorId: String, current: PendingOomError?) {
        val matching = current?.takeIf { it.errorId == rumErrorId }
        when (matching?.state) {
            State.PENDING -> {
                val reserved = matching.copy(state = State.RESERVED)
                persist(reserved, onFailure = { reply(rumErrorId, false) }) {
                    val isReserved = replaceRecord(matching, reserved)
                    if (!isReserved) removeIfNothingHeld()
                    reply(rumErrorId, isReserved)
                }
            }

            State.RESERVED,
            State.RESOLVED_ATTACHED,
            State.RESOLVED_NOT_ATTACHED -> reply(rumErrorId, true)

            null -> reply(rumErrorId, false)
        }
    }

    private fun replaceRecord(expected: PendingOomError, replacement: PendingOomError): Boolean {
        return synchronized(lock) {
            if (record === expected) {
                record = replacement
                true
            } else {
                false
            }
        }
    }

    private fun reply(rumErrorId: String, granted: Boolean) {
        sendToProfiling(ProfilerEvent.RumOomProfileAttachReply(rumErrorId, granted))
    }

    private fun persist(
        value: PendingOomError,
        onFailure: () -> Unit = {},
        onSuccess: () -> Unit
    ) {
        val store = dataStore
        if (store == null) {
            onFailure()
            return
        }
        store.setValue(
            KEY,
            value,
            VERSION,
            object : DataStoreWriteCallback {
                override fun onSuccess() = onSuccess()

                override fun onFailure() = onFailure()
            },
            PendingOomErrorSerializer()
        )
    }

    /**
     * Writes the held error, then removes it from disk. It is first released in memory, so that no
     * attach request can be granted for it any more. A failed write keeps it on disk: it is written
     * again on the next launch, with the same status when it was resolved.
     */
    private fun flush(held: PendingOomError, attached: Boolean?) {
        synchronized(lock) {
            if (record?.errorId == held.errorId) record = null
        }
        val feature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        feature.withWriteContext { _, writeScope: EventWriteScope ->
            writeScope { batchWriter ->
                val event = held.withProfiling(attached)
                if (dataWriterProvider().write(batchWriter, event, EventType.CRASH)) {
                    removeIfNothingHeld()
                }
            }
        }
    }

    private fun removeIfNothingHeld() {
        if (synchronized(lock) { record == null }) {
            dataStore?.removeValue(KEY)
        }
    }

    private fun sendToProfiling(event: ProfilerEvent) {
        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.sendEvent(event)
    }

    private fun log(target: InternalLogger.Target, message: String) {
        sdkCore.internalLogger.log(InternalLogger.Level.DEBUG, target, { message })
    }

    private fun PendingOomError.isExpired(): Boolean {
        return sdkCore.timeProvider.getDeviceTimestampMillis() - heldAtMs >= RESERVED_MAX_AGE_MS
    }

    internal enum class State {
        PENDING,
        RESERVED,
        RESOLVED_ATTACHED,
        RESOLVED_NOT_ATTACHED;

        val isResolved: Boolean
            get() = this == RESOLVED_ATTACHED || this == RESOLVED_NOT_ATTACHED
    }

    internal data class PendingOomError(
        val event: ErrorEvent,
        val state: State,
        val heldAtMs: Long
    ) {
        val errorId: String
            get() = event.error.id.orEmpty()

        fun resolvedOutcome(): Boolean? = when (state) {
            State.RESOLVED_ATTACHED -> true
            State.RESOLVED_NOT_ATTACHED, State.RESERVED -> false
            State.PENDING -> null
        }

        /** `true` → profile attached, `false` → not attached, `null` → no profiling status. */
        fun withProfiling(attached: Boolean?): ErrorEvent {
            val profiling = attached?.let {
                ErrorEvent.Profiling(
                    status = if (it) ErrorEvent.ProfilingStatus.RUNNING else ErrorEvent.ProfilingStatus.STOPPED
                )
            }
            return event.copy(dd = event.dd.copy(profiling = profiling))
        }
    }

    internal class PendingOomErrorSerializer : Serializer<PendingOomError> {
        override fun serialize(model: PendingOomError): String {
            val json = JsonObject()
            json.add(KEY_EVENT, model.event.toJson())
            json.addProperty(KEY_STATE, model.state.name)
            json.addProperty(KEY_HELD_AT, model.heldAtMs)
            return json.toString()
        }
    }

    internal class PendingOomErrorDeserializer : Deserializer<String, PendingOomError> {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        override fun deserialize(model: String): PendingOomError? {
            return try {
                val json = JsonParser.parseString(model).asJsonObject
                PendingOomError(
                    event = ErrorEvent.fromJsonObject(json.getAsJsonObject(KEY_EVENT)),
                    state = State.valueOf(json.get(KEY_STATE).asString),
                    heldAtMs = json.get(KEY_HELD_AT).asLong
                )
            } catch (e: Exception) {
                // Left half-written by a process death, or by another SDK version.
                null
            }
        }
    }

    internal companion object {
        internal const val KEY = "pending_oom_error"
        internal const val VERSION = 1
        internal val TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1)

        // Matches the max age of the profiling feature's outcome marker.
        internal val RESERVED_MAX_AGE_MS = TimeUnit.HOURS.toMillis(24)
        private const val KEY_EVENT = "event"
        private const val KEY_STATE = "state"
        private const val KEY_HELD_AT = "held_at"
        internal const val LOG_HELD = "Fatal OOM error held until the outcome of its OOM profile is known."
        internal const val LOG_FLUSHED_CRASH_LOOP = "Previously held OOM error written: a new OOM error is held."
        internal const val LOG_TIMED_OUT = "Held OOM error written without profiling status: no outcome received."
    }
}
