/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.prelaunch

import android.app.ActivityManager
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.datadog.android.rum.AppLaunchPreInitCollector
import com.datadog.android.rum.DdRumContentProvider

/**
 * [ContentProvider] that auto-installs [AppLaunchPreInitCollector] at process start.
 *
 * Declared in this module's AndroidManifest so it is automatically merged into any
 * app that declares a dependency on `dd-sdk-android-rum-prelaunch`. Apps that do not
 * include this module are unaffected: [AppLaunchPreInitCollector] remains in its
 * initial [AppLaunchPreInitCollector.State.NOT_INSTALLED] state and the full legacy
 * `RumAppStartupDetector` path runs unchanged.
 *
 * No public API is exposed by this class; users interact with this module by adding
 * it as a Gradle dependency only.
 */
internal class AppLaunchCollectorProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: run {
            Log.w(TAG, "onCreate: applicationContext is null, skipping install")
            return false
        }
        val importance = DdRumContentProvider.processImportance
        if (importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            Log.d(TAG, "onCreate: process is not foreground (importance=$importance), skipping install")
            return false
        }
        Log.d(TAG, "onCreate: foreground process detected, installing AppLaunchPreInitCollector")
        AppLaunchPreInitCollector.install(application)
        return true
    }

    companion object {
        // Must match AppLaunchPreInitCollector.TAG for unified logcat filtering
        private const val TAG = "DD/AppLaunch"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
