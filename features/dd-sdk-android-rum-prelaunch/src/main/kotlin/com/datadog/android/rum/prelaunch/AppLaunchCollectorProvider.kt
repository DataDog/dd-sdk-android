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
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.internal.startup.PreLaunchRumAppStartupDetector

/**
 * [ContentProvider] that auto-installs [PreLaunchRumAppStartupDetector] at process start.
 *
 * Declared in this module's AndroidManifest so it is automatically merged into any
 * app that declares a dependency on `dd-sdk-android-rum-prelaunch`. Apps that do not
 * include this module are unaffected: the normal `RumAppStartupDetectorImpl` path runs
 * unchanged during [Rum.enable].
 *
 * No public API is exposed by this class; users interact with this module by adding
 * it as a Gradle dependency only.
 */
@Suppress("PackageNameVisibility")
internal class AppLaunchCollectorProvider : ContentProvider() {

    @Suppress("ReturnCount")
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application
        if (application == null) {
            return false
        }
        val importance = DdRumContentProvider.processImportance
        if (importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            return false
        }
        PreLaunchRumAppStartupDetector.install(application)
        return true
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
