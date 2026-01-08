/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.profiling.internal.ProfilingStorage

/**
 * A [ContentProvider] to start Profiling request as early as possible in the app's
 * lifecycle.
 */
class DdProfilingContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            context?.let {
                val instanceNames = ProfilingStorage.getProfilingEnabledInstanceNames(it)
                if (instanceNames.isNotEmpty() && isProcessFromLauncher()) {
                    Profiling.start(it, instanceNames)
                }
            }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun isProcessFromLauncher(): Boolean {
        val manager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return manager?.getHistoricalProcessStartReasons(1)
            ?.firstOrNull()?.reason == ApplicationStartInfo.START_REASON_LAUNCHER
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
}
