/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import android.content.SharedPreferences

/**
 * Highest accepted minimum not-before (unix seconds) per trust anchor, keyed by the hex SHA-256 of the root DER.
 * It is the only signing data that required mode persists, and it holds no flag data.
 */
internal interface MinimumNotBeforeStore {
    fun get(anchorId: String): Long?

    /** Stores [value] only when it is higher than the stored value. */
    fun raise(anchorId: String, value: Long)
}

internal class InMemoryMinimumNotBeforeStore : MinimumNotBeforeStore {
    private val values = mutableMapOf<String, Long>()

    override fun get(anchorId: String): Long? = synchronized(values) { values[anchorId] }

    override fun raise(anchorId: String, value: Long) = synchronized(values) {
        if (value > (values[anchorId] ?: -1L)) values[anchorId] = value
    }
}

/** Survives restarts; a read failure falls back to memory so revocation never weakens within the process. */
internal class SharedPreferencesMinimumNotBeforeStore(
    private val preferences: SharedPreferences
) : MinimumNotBeforeStore {
    private val memory = InMemoryMinimumNotBeforeStore()

    override fun get(anchorId: String): Long? {
        val stored = try {
            preferences.getLong(KEY_PREFIX + anchorId, -1L).takeIf { it >= 0 }
        } catch (_: ClassCastException) {
            null
        }
        val inMemory = memory.get(anchorId)
        return if (stored == null || (inMemory != null && inMemory > stored)) inMemory else stored
    }

    override fun raise(anchorId: String, value: Long) = synchronized(this) {
        if (value > (get(anchorId) ?: -1L)) {
            memory.raise(anchorId, value)
            preferences.edit().putLong(KEY_PREFIX + anchorId, value).apply()
        }
    }

    companion object {
        const val PREFERENCES_NAME = "datadog_flags_response_signing"
        private const val KEY_PREFIX = "minimum_not_before_"
    }
}
