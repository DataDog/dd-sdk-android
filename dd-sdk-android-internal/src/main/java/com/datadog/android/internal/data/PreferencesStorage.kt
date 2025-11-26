/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.data

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface to define the preference storage.
 */
@NoOpImplementation
interface PreferencesStorage {

    /**
     * Put an integer value in the storage.
     */
    fun putInt(key: String, value: Int)

    /**
     * Get an integer value from the storage.
     */
    fun getInt(key: String, defaultValue: Int = 0): Int

    /**
     * Put a string value in the storage.
     */
    fun getString(key: String, defaultValue: String? = null): String?

    /**
     * Put a string value in the storage.
     */
    fun putString(key: String, value: String)

    /**
     * Get a string set value from the storage.
     */
    fun getStringSet(key: String, defaultValue: Set<String>? = null): Set<String>?

    /**
     * Put a string set value in the storage.
     */
    fun putStringSet(key: String, value: Set<String>)

    /**
     * Put a boolean value in the storage.
     */
    fun putBoolean(key: String, value: Boolean)

    /**
     * Get a boolean value from the storage.
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    /**
     * Remove a value from the storage.
     */
    fun remove(key: String)

    /**
     * Clear all values from the storage.
     */
    fun clear()
}
