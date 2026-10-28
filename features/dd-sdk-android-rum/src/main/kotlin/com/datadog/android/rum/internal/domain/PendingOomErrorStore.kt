/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.os.Handler
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilerEvent.OomProfileResolution.Outcome
import com.datadog.android.rum.model.ErrorEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

/**
 * Holds a single fatal OOM error on disk until the profiling feature reports whether its OOM
 * profile was written, so that the error's profiling status matches exactly.
 *
 * Every state change is decided under the store's lock and enqueued to the data store in that same order;
 * its visible effect (granting the attach, acknowledging the outcome, writing the error) only
 * happens once the store reports it persisted. A terminal state is persisted before the error is
 * written, so a reload never reopens the attachment of an error that may already be sent.
 *
 * A reserved error (profiling was allowed to write the profile) is never settled by RUM: it waits
 * for profiling's outcome, on this or a later launch.
 */
@Suppress("TooManyFunctions")
internal class PendingOomErrorStore(
    private val sdkCore: FeatureSdkCore,
    private val handler: Handler,
    private val dataWriterProvider: () -> DataWriter<Any>
) {
    private val lock = Any()

    // All guarded by lock.
    private var dataStore: DataStoreHandler? = null
    private var isLoaded = false
    private var isTimedOut = false
    private var record: PendingOomError? = null
    private val actionsAwaitingLoad = mutableListOf<() -> Unit>()

    private val timeoutRunnable = Runnable { onTimeout() }

    fun start(dataStore: DataStoreHandler) {
        synchronized(lock) { this.dataStore = dataStore }
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
     * Holds [event] instead of writing it, if the slot is free and loaded. Otherwise (a previous
     * error is still held, or it is not loaded yet) the caller writes [event] right away and must
     * not let a profile be attached to it.
     *
     * @param event the fatal OOM error to hold.
     * @return whether [event] is held.
     */
    @WorkerThread
    fun tryHold(event: ErrorEvent): Boolean {
        synchronized(lock) {
            val store = dataStore
            if (!isLoaded || record != null || store == null) return false
            val held = PendingOomError(event, State.PENDING, isPersisted = false)
            record = held
            store.setValue(KEY, held, VERSION, persistedCallback(held), PendingOomErrorSerializer())
        }
        log(LOG_HELD)
        return true
    }

    fun onAttachRequest(rumErrorId: String) {
        val reply: Boolean? = synchronized(lock) {
            if (!isLoaded) {
                actionsAwaitingLoad.add { onAttachRequest(rumErrorId) }
                return
            }
            val current = record?.takeIf { it.errorId == rumErrorId }
            when {
                current == null || current.state.isTerminal -> false
                current.state == State.PENDING -> {
                    transition(current, State.RESERVED) { isPersisted ->
                        if (!isPersisted) revert(current)
                        reply(rumErrorId, isPersisted)
                    }
                    null
                }
                // A reservation being persisted is granted by its own callback.
                current.isPersisted -> true
                else -> null
            }
        }
        reply?.let { reply(rumErrorId, it) }
    }

    fun onResolution(rumErrorId: String, outcome: Outcome) {
        val isHandled = synchronized(lock) {
            if (!isLoaded) {
                actionsAwaitingLoad.add { onResolution(rumErrorId, outcome) }
                return
            }
            val current = record?.takeIf { it.errorId == rumErrorId && !it.state.isTerminal }
            if (current != null) {
                val state = if (outcome == Outcome.ATTACHED) State.RESOLVED_ATTACHED else State.RESOLVED_NOT_ATTACHED
                transition(current, state) { isPersisted ->
                    if (isPersisted) {
                        sendToProfiling(ProfilerEvent.RumOomProfileResolutionAck(rumErrorId))
                    } else {
                        // Profiling sends the outcome again on the next announcement.
                        revert(current)
                    }
                }
            }
            current != null
        }
        // Already settled, or not held by this store: only tell profiling to stop resending.
        if (!isHandled) sendToProfiling(ProfilerEvent.RumOomProfileResolutionAck(rumErrorId))
    }

    fun onProfilingReady() {
        val toAnnounce = synchronized(lock) {
            if (!isLoaded) {
                actionsAwaitingLoad.add { onProfilingReady() }
                return
            }
            record?.takeIf { !it.state.isTerminal }
        }
        toAnnounce?.let { announce(it) }
    }

    private fun onLoaded(loaded: PendingOomError?) {
        val actions = synchronized(lock) {
            isLoaded = true
            record = loaded?.copy(isPersisted = true)
            actionsAwaitingLoad.toList().also { actionsAwaitingLoad.clear() }
        }
        val current = synchronized(lock) { record }
        when (current?.state) {
            null -> Unit
            State.PENDING -> if (synchronized(lock) { isTimedOut }) onTimeout() else announce(current)
            State.RESERVED -> announce(current)
            else -> write(current)
        }
        actions.forEach { it() }
    }

    private fun onTimeout() {
        synchronized(lock) {
            isTimedOut = true
            val current = record ?: return
            if (!isLoaded || current.state != State.PENDING) return
            log(LOG_TIMED_OUT)
            transition(current, State.RESOLVED_NO_PROFILE) { isPersisted ->
                if (!isPersisted) revert(current)
            }
        }
    }

    /**
     * Must be called under the lock: the new state is published and enqueued to the store together,
     * so that store writes keep the order of the decisions. A terminal state is written once
     * persisted.
     */
    private fun transition(from: PendingOomError, to: State, onPersisted: (Boolean) -> Unit) {
        val next = from.copy(state = to, isPersisted = false)
        record = next
        val store = dataStore
        if (store == null) {
            onPersisted(false)
            return
        }
        store.setValue(
            KEY,
            next,
            VERSION,
            object : DataStoreWriteCallback {
                override fun onSuccess() {
                    markPersisted(next)
                    onPersisted(true)
                    if (to.isTerminal) write(next)
                }

                override fun onFailure() = onPersisted(false)
            },
            PendingOomErrorSerializer()
        )
    }

    private fun persistedCallback(held: PendingOomError) = object : DataStoreWriteCallback {
        override fun onSuccess() = markPersisted(held)

        override fun onFailure() = Unit
    }

    private fun markPersisted(value: PendingOomError) {
        synchronized(lock) {
            if (record === value) record = value.copy(isPersisted = true)
        }
    }

    private fun revert(previous: PendingOomError) {
        synchronized(lock) {
            if (record?.errorId == previous.errorId && record?.isPersisted == false) record = previous
        }
    }

    /**
     * Writes a terminal record, then removes it. A failed write keeps it on disk, to be written
     * again on the next launch with the same status.
     */
    private fun write(held: PendingOomError) {
        val feature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        feature.withWriteContext { _, writeScope ->
            writeScope { batchWriter ->
                val event = held.toEvent()
                if (dataWriterProvider().write(batchWriter, event, EventType.CRASH)) {
                    synchronized(lock) {
                        if (record?.errorId == held.errorId) {
                            record = null
                            dataStore?.removeValue(KEY)
                        }
                    }
                }
            }
        }
    }

    private fun announce(held: PendingOomError) {
        sendToProfiling(ProfilerEvent.RumHeldOomErrorEvent(held.errorId))
    }

    private fun reply(rumErrorId: String, granted: Boolean) {
        sendToProfiling(ProfilerEvent.RumOomProfileAttachReply(rumErrorId, granted))
    }

    private fun sendToProfiling(event: ProfilerEvent) {
        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.sendEvent(event)
    }

    private fun log(message: String) {
        sdkCore.internalLogger.log(InternalLogger.Level.DEBUG, InternalLogger.Target.USER, { message })
    }

    internal enum class State {
        PENDING,
        RESERVED,
        RESOLVED_ATTACHED,
        RESOLVED_NOT_ATTACHED,
        RESOLVED_NO_PROFILE;

        val isTerminal: Boolean
            get() = this != PENDING && this != RESERVED
    }

    /**
     * @param event the held error, as built at crash time.
     * @param state where the attach handshake stands.
     * @param isPersisted whether [state] is known to be on disk; only meaningful in memory.
     */
    internal data class PendingOomError(
        val event: ErrorEvent,
        val state: State,
        val isPersisted: Boolean = true
    ) {
        val errorId: String
            get() = event.error.id.orEmpty()

        fun toEvent(): ErrorEvent {
            val status = when (state) {
                State.RESOLVED_ATTACHED -> ErrorEvent.ProfilingStatus.RUNNING
                State.RESOLVED_NOT_ATTACHED -> ErrorEvent.ProfilingStatus.STOPPED
                else -> null
            }
            return event.copy(dd = event.dd.copy(profiling = status?.let { ErrorEvent.Profiling(status = it) }))
        }
    }

    internal class PendingOomErrorSerializer : Serializer<PendingOomError> {
        override fun serialize(model: PendingOomError): String {
            val json = JsonObject()
            json.add(KEY_EVENT, model.event.toJson())
            json.addProperty(KEY_STATE, model.state.name)
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
                    state = State.valueOf(json.get(KEY_STATE).asString)
                )
            } catch (e: Exception) {
                // Left half-written by a process death, or by another SDK version.
                null
            }
        }
    }

    internal companion object {
        internal const val KEY = "pending_oom_error"
        internal const val VERSION = 2
        internal val TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1)
        private const val KEY_EVENT = "event"
        private const val KEY_STATE = "state"
        internal const val LOG_HELD = "Fatal OOM error held until the outcome of its OOM profile is known."
        internal const val LOG_TIMED_OUT =
            "Held OOM error written without profiling status: no OOM profile reserved in time."
    }
}
