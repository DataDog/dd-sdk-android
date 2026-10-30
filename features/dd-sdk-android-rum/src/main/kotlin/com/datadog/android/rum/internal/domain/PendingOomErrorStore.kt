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
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.rum.model.ErrorEvent
import com.google.gson.JsonParseException
import java.util.concurrent.TimeUnit

/**
 * Holds a single fatal OOM error on disk until the next launch, where the profiling feature reports
 * whether its OOM profile was written. The error says "running" only on a "written" answer.
 */
internal class PendingOomErrorStore(
    private val sdkCore: FeatureSdkCore,
    private val handler: Handler,
    private val dataWriterProvider: () -> DataWriter<Any>
) {
    private val lock = Any()

    // All guarded by lock.
    private var dataStore: DataStoreHandler? = null
    private var isLoaded = false

    // A record is on disk: loaded from a previous process, or held by this one.
    private var isSlotBusy = false

    // The record loaded from a previous process, until it is answered or times out.
    private var awaiting: ErrorEvent? = null

    private val timeoutRunnable = Runnable { onTimeout() }

    fun start(dataStore: DataStoreHandler) {
        synchronized(lock) { this.dataStore = dataStore }
        dataStore.value(
            key = KEY,
            version = VERSION,
            callback = object : DataStoreReadCallback<ErrorEvent> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ErrorEvent>?) {
                    onLoaded(dataStoreContent?.data)
                }

                override fun onFailure() {
                    onLoaded(null)
                }
            },
            deserializer = ErrorEventDeserializer()
        )
    }

    fun stop() {
        handler.removeCallbacks(timeoutRunnable)
    }

    /**
     * Holds [event] instead of writing it, if the slot is loaded and free. Otherwise the caller
     * writes [event] right away, and must not let a profile be attached to it.
     *
     * @param event the fatal OOM error to hold.
     * @return whether [event] is held.
     */
    @WorkerThread
    fun tryHold(event: ErrorEvent): Boolean {
        synchronized(lock) {
            val store = dataStore
            if (!isLoaded || isSlotBusy || store == null) return false
            isSlotBusy = true
            store.setValue(KEY, event, VERSION, null, ErrorEventSerializer())
        }
        log(LOG_HELD)
        return true
    }

    fun onProfilingReady() {
        synchronized(lock) { awaiting }?.let { announce(it) }
    }

    fun onOomProfileResolution(rumErrorId: String, isProfileWritten: Boolean) {
        val event = take(rumErrorId) ?: return
        val status = if (isProfileWritten) ErrorEvent.ProfilingStatus.RUNNING else ErrorEvent.ProfilingStatus.STOPPED
        write(event, status)
    }

    private fun onLoaded(loaded: ErrorEvent?) {
        synchronized(lock) {
            isLoaded = true
            isSlotBusy = loaded != null
            awaiting = loaded
        }
        if (loaded != null) {
            handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
            announce(loaded)
        }
    }

    private fun onTimeout() {
        val event = synchronized(lock) { awaiting } ?: return
        take(event.error.id.orEmpty())?.let {
            log(LOG_TIMED_OUT)
            write(it, null)
        }
    }

    /** First caller wins: an answer after the timeout, or a second answer, is ignored. */
    private fun take(rumErrorId: String): ErrorEvent? {
        return synchronized(lock) {
            awaiting?.takeIf { it.error.id == rumErrorId }?.also { awaiting = null }
        }
    }

    /** A failed write keeps the record on disk and the slot busy. */
    private fun write(event: ErrorEvent, status: ErrorEvent.ProfilingStatus?) {
        val toWrite = event.copy(dd = event.dd.copy(profiling = status?.let { ErrorEvent.Profiling(status = it) }))
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.withWriteContext { _, writeScope ->
            writeScope { batchWriter ->
                if (dataWriterProvider().write(batchWriter, toWrite, EventType.CRASH)) {
                    synchronized(lock) {
                        // Freed only once the removal is enqueued, so it can't wipe a later hold.
                        dataStore?.removeValue(KEY)
                        isSlotBusy = false
                    }
                }
            }
        }
    }

    private fun announce(event: ErrorEvent) {
        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)
            ?.sendEvent(ProfilerEvent.RumHeldOomErrorEvent(event.error.id.orEmpty()))
    }

    private fun log(message: String) {
        sdkCore.internalLogger.log(InternalLogger.Level.DEBUG, InternalLogger.Target.USER, { message })
    }

    internal class ErrorEventSerializer : Serializer<ErrorEvent> {
        override fun serialize(model: ErrorEvent): String = model.toJson().toString()
    }

    internal class ErrorEventDeserializer : Deserializer<String, ErrorEvent> {
        @Suppress("SwallowedException")
        override fun deserialize(model: String): ErrorEvent? {
            return try {
                ErrorEvent.fromJson(model)
            } catch (e: JsonParseException) {
                // Left half-written by a process death, or by another SDK version.
                null
            } catch (e: IllegalStateException) {
                null
            }
        }
    }

    internal companion object {
        internal const val KEY = "pending_oom_error"
        internal const val VERSION = 1
        internal val TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1)
        internal const val LOG_HELD = "Fatal OOM error held until the outcome of its OOM profile is known."
        internal const val LOG_TIMED_OUT =
            "Held OOM error written without profiling status: no OOM profile outcome in time."
    }
}
