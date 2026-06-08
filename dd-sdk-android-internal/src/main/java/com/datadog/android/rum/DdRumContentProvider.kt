/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.ActivityManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * A Content provider used to monitor the Application startup time efficiently.
 */
class DdRumContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        if (_processImportance == null) {
            _processImportance = readProcessImportance()
            Log.w("DdRumContentProvider", "processImportance:$_processImportance")
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    companion object {
        @Volatile
        private var _processImportance: Int? = null

        /**
         * Process importance at the moment of creating [DdRumContentProvider].
         * If accessed before [DdRumContentProvider.onCreate] is called (e.g. by another
         * ContentProvider initialised first), it is computed on demand so the value is
         * never stale. Should be set from the outside only in tests.
         */
        var processImportance: Int
            get() {
                val stored = _processImportance
                if (stored != null) return stored
                val currentProcessImportance = readProcessImportance()
                _processImportance = currentProcessImportance
                return currentProcessImportance
            }

            /**
             * Do not call this from production code.
             */
            @VisibleForTesting
            set(value) {
                _processImportance = if (value == 0) null else value
            }

        private fun readProcessImportance(): Int {
            val processInfo = ActivityManager.RunningAppProcessInfo()
            try {
                ActivityManager.getMyMemoryState(processInfo)
            } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                // in this case default value of RunningAppProcessInfo.importance will be returned,
                // which is IMPORTANCE_FOREGROUND
                Log.e("DdRumContentProvider", "Cannot read process importance from ActivityManager", e)
            }
            return processInfo.importance
        }

        /**
         * fallback for APIs below Android N, see [DefaultAppStartTimeProvider].
         * Should be set from the outside only in tests.
         */
        var createTimeNs: Long = System.nanoTime()
    }
}
