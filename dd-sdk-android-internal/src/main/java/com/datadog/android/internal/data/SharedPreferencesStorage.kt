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
class SharedPreferencesStorage(context: Context) : PreferencesStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(DATADOG_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    override fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        internal const val DATADOG_PREFERENCES_FILE_NAME = "dd_prefs"
    }
}
