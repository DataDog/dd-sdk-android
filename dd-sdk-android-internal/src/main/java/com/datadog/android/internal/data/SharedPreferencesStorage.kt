/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.data

import android.content.Context
import android.content.SharedPreferences

/**
 * An implementation of [PreferencesStorage] that uses Android's [SharedPreferences] as storage
 * backend.
 */
class SharedPreferencesStorage(appContext: Context) : PreferencesStorage {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(DATADOG_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return runSafe {
            // Called in safe
            @Suppress("UnsafeThirdPartyFunctionCall")
            prefs.getInt(key, defaultValue)
        } ?: defaultValue
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return runSafe {
            // Called in safe
            @Suppress("UnsafeThirdPartyFunctionCall")
            prefs.getString(key, defaultValue)
        }
    }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getStringSet(
        key: String,
        defaultValue: Set<String>?
    ): Set<String>? {
        return runSafe {
            // Called in safe
            @Suppress("UnsafeThirdPartyFunctionCall")
            prefs.getStringSet(key, defaultValue)
        }
    }

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return runSafe {
            // Called in safe
            @Suppress("UnsafeThirdPartyFunctionCall")
            prefs.getBoolean(key, defaultValue)
        } ?: defaultValue
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    @Suppress("SwallowedException")
    private fun <T> runSafe(runnable: () -> T): T? {
        return try {
            runnable.invoke()
        } catch (e: ClassCastException) {
            // It happens when SDK updates the data type, we can ignore it by returning null.
            null
        }
    }

    internal companion object {
        internal const val DATADOG_PREFERENCES_FILE_NAME = "dd_prefs"
    }
}
