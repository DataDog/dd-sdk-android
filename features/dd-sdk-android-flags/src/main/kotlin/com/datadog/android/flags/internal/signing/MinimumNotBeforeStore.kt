/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import android.content.SharedPreferences

/**
 * Per trust anchor, keyed by the hex SHA-256 of the root DER: the highest accepted minimum leaf not-before and the
 * issued-at high-water mark, both in unix seconds. It is the only signing data that required mode persists, and it
 * holds no flag data. Neither value ever goes down.
 */
internal interface MinimumNotBeforeStore {
    fun get(anchorId: String): Long?

    /** Stores [value] only when it is higher than the stored value. */
    fun raise(anchorId: String, value: Long)

    fun highWaterMark(anchorId: String): Long?

    /** Stores [value] only when it is not negative and higher than the stored value. */
    fun raiseHighWaterMark(anchorId: String, value: Long)
}

internal class InMemoryMinimumNotBeforeStore : MinimumNotBeforeStore {
    private val values = mutableMapOf<String, Long>()
    private val highWaterMarks = mutableMapOf<String, Long>()

    override fun get(anchorId: String): Long? = synchronized(values) { values[anchorId] }

    override fun raise(anchorId: String, value: Long) = synchronized(values) {
        if (value > (values[anchorId] ?: -1L)) values[anchorId] = value
    }

    override fun highWaterMark(anchorId: String): Long? = synchronized(values) { highWaterMarks[anchorId] }

    override fun raiseHighWaterMark(anchorId: String, value: Long) = synchronized(values) {
        if (value > (highWaterMarks[anchorId] ?: -1L)) highWaterMarks[anchorId] = value
    }
}

/**
 * Survives restarts. The memory copy keeps each value for the process when a read or a write fails, so revocation and
 * the clock floor never weaken within the process. An older value restored from a backup is lower, so it is safe.
 * Each SDK core has its own store over the same preferences instance, so every read, compare and write locks that
 * instance: a lower value can never overwrite a higher one.
 */
internal class SharedPreferencesMinimumNotBeforeStore(
    private val preferences: SharedPreferences
) : MinimumNotBeforeStore {
    private val memory = InMemoryMinimumNotBeforeStore()

    override fun get(anchorId: String): Long? = read(KEY_PREFIX + anchorId, memory.get(anchorId))

    override fun raise(anchorId: String, value: Long) = synchronized(preferences) {
        if (value > (get(anchorId) ?: -1L)) {
            memory.raise(anchorId, value)
            write(KEY_PREFIX + anchorId, value)
        }
    }

    override fun highWaterMark(anchorId: String): Long? =
        read(HIGH_WATER_MARK_PREFIX + anchorId, memory.highWaterMark(anchorId))

    override fun raiseHighWaterMark(anchorId: String, value: Long) = synchronized(preferences) {
        if (value > (highWaterMark(anchorId) ?: -1L)) {
            memory.raiseHighWaterMark(anchorId, value)
            write(HIGH_WATER_MARK_PREFIX + anchorId, value)
        }
    }

    private fun read(key: String, inMemory: Long?): Long? {
        val stored = try {
            preferences.getLong(key, -1L).takeIf { it >= 0 }
        } catch (_: ClassCastException) {
            null
        }
        return if (stored == null || (inMemory != null && inMemory > stored)) inMemory else stored
    }

    // apply() writes to disk later and reports no failure; the memory copy already holds the value for the process.
    private fun write(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "datadog_flags_response_signing"
        private const val KEY_PREFIX = "minimum_not_before_"
        private const val HIGH_WATER_MARK_PREFIX = "issued_at_high_water_mark_"
    }
}
