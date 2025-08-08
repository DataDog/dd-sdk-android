/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.ActivityManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Process
import android.util.Log

/**
 * A Content provider used to monitor the Application startup time efficiently.
 */
class DdRumContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        if (processImportance == 0) {
            val manager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val currentProcessId = Process.myPid()
            val currentProcess = manager?.runningAppProcesses?.firstOrNull {
                it.pid == currentProcessId
            }
            processImportance = currentProcess?.importance ?: -1
            Log.w("DdRumContentProvider", "processImportance:$processImportance")
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

    internal companion object {
        internal const val DEFAULT_IMPORTANCE: Int =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        internal var processImportance = 0

        @Suppress("unused") // Used for instrumented tests
        @JvmStatic
        private fun overrideProcessImportance(importance: Int) {
            Log.w(
                "DdRumContentProvider",
                "override processImportance: $processImportance -> $importance"
            )
            processImportance = importance
        }
    }
}
