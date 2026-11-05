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
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.utils.WriteOperation
import com.datadog.android.rum.model.ErrorEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

/**
 * Holds a single fatal OOM error on disk until the next launch, where the profiling feature reports
 * whether its OOM profile was written. The error says "running" only when it was. Best effort: on
 * the next launch, the error is removed from disk as soon as it is loaded, and is only tried in
 * that process.
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

    // The single held error: stored on disk by this process, or loaded from the previous launch.
    private var heldError: ErrorEvent? = null

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
     * Holds [event] instead of writing it, once the stored error is loaded. The newest error wins:
     * the error held before it, loaded or stored by this process, is written right away, without
     * profiling status. If the stored error isn't loaded yet, the caller writes [event] right
     * away, and must not let a profile be attached to it.
     *
     * @param event the fatal OOM error to hold.
     * @return whether [event] is held.
     */
    @WorkerThread
    fun tryHold(event: ErrorEvent): Boolean {
        val older = synchronized(lock) {
            val store = dataStore
            if (!isLoaded || store == null) return false
            // TODO RUM-19677: Apply the RUM error event mapper before persisting the held error. Today the unredacted error sits on disk until it is released and mapped on the next launch.
            store.setValue(KEY, event, VERSION, holdCallback(event), ErrorEventSerializer(sdkCore.internalLogger))
            heldError.also {
                heldError = event
            }
        }
        // A pending timeout must not write the new one.
        handler.removeCallbacks(timeoutRunnable)
        log(LOG_HELD)
        older?.let { write(it, null) }
        return true
    }

    fun onProfilingReady() {
        synchronized(lock) { heldError }?.let { announce(it) }
    }

    fun onOomProfileWriteResult(rumErrorId: String, isWritten: Boolean) {
        val event = take(rumErrorId) ?: return
        val status = if (isWritten) ErrorEvent.ProfilingStatus.RUNNING else ErrorEvent.ProfilingStatus.STOPPED
        write(event, status)
    }

    /**
     * The crash write was skipped for a held error: if storing it fails (serializer failures are
     * reported here too), it is written right away instead, without profiling status.
     */
    private fun holdCallback(event: ErrorEvent) = object : DataStoreWriteCallback {
        override fun onSuccess() = Unit

        override fun onFailure() {
            // Claimed under the lock, so a newer hold doesn't write it again as the older error. If a
            // newer hold came first, it already wrote it.
            val isClaimed = synchronized(lock) {
                (heldError === event).also { if (it) heldError = null }
            }
            if (isClaimed) write(event, null)
        }
    }

    private fun onLoaded(loaded: ErrorEvent?) {
        synchronized(lock) {
            // Removed before any hold of this process can be stored: the data store runs in order.
            if (loaded != null) dataStore?.removeValue(KEY)
            isLoaded = true
            heldError = loaded
        }
        if (loaded != null) {
            handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
            announce(loaded)
        }
    }

    private fun onTimeout() {
        val event = synchronized(lock) { heldError } ?: return
        take(event.error.id.orEmpty())?.let {
            log(LOG_TIMED_OUT)
            write(it, null)
        }
    }

    /** First caller wins: a profile write result after the timeout, or a second one, is ignored. */
    private fun take(rumErrorId: String): ErrorEvent? {
        val event = synchronized(lock) {
            heldError?.takeIf { it.error.id == rumErrorId }?.also { heldError = null }
        }
        if (event != null) handler.removeCallbacks(timeoutRunnable)
        return event
    }

    private fun write(event: ErrorEvent, status: ErrorEvent.ProfilingStatus?) {
        val toWrite = event.copy(dd = event.dd.copy(profiling = status?.let { ErrorEvent.Profiling(status = it) }))
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.withWriteContext { _, writeScope ->
            writeScope { batchWriter ->
                try {
                    dataWriterProvider().write(batchWriter, toWrite, EventType.CRASH)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    sdkCore.internalLogger.log(
                        InternalLogger.Level.ERROR,
                        listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                        { WriteOperation.WRITE_OPERATION_FAILED_ERROR },
                        e
                    )
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

    /** Same attribute sanitization and safe mapping as the RUM event serializer. */
    internal class ErrorEventSerializer(
        private val internalLogger: InternalLogger,
        private val delegate: Serializer<Any> = RumEventSerializer(internalLogger)
    ) : Serializer<ErrorEvent> {

        override fun serialize(model: ErrorEvent): String? {
            return try {
                delegate.serialize(model)
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                // Throwable: customer attribute toString() runs here, and an OOM can happen under memory pressure.
                // The data store reports a null value as a failed write: the error is written right away.
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                    { LOG_SERIALIZATION_FAILED },
                    e
                )
                null
            }
        }
    }

    internal class ErrorEventDeserializer : Deserializer<String, ErrorEvent> {
        @Suppress("SwallowedException")
        override fun deserialize(model: String): ErrorEvent? {
            return try {
                val json = JsonParser.parseString(model).asJsonObject
                // The serializer moved the known context attributes to the top level.
                val moved = RumEventSerializer.knownAttributes.filter { json.has(it) }
                if (moved.isNotEmpty()) {
                    val context =
                        json.getAsJsonObject(RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX)
                            ?: JsonObject().also { json.add(RumEventSerializer.GLOBAL_ATTRIBUTE_PREFIX, it) }
                    moved.forEach { context.add(it, json.remove(it)) }
                }
                ErrorEvent.fromJsonObject(json)
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
        internal const val LOG_SERIALIZATION_FAILED =
            "Fatal OOM error could not be serialized: it is written right away."
        internal const val LOG_TIMED_OUT =
            "Held OOM error written without profiling status: no OOM profile outcome in time."
    }
}
