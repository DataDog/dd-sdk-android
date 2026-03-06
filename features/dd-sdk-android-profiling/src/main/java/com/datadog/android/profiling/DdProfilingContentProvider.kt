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
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.system.BuildSdkVersionProvider.Companion.DEFAULT
import com.datadog.android.profiling.internal.ProfilingStartReason
import com.datadog.android.profiling.internal.ProfilingStorage
import com.datadog.android.profiling.internal.perfetto.PerfettoProfiler

/**
 * A [ContentProvider] to start Profiling request as early as possible in the app's
 * lifecycle.
 */
@OptIn(ExperimentalProfilingApi::class)
class DdProfilingContentProvider(
    private val buildSdkVersionProvider: BuildSdkVersionProvider = DEFAULT
) : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { onStart(it) }
        return true
    }

    internal fun onStart(context: Context) {
        if (buildSdkVersionProvider.isAtLeastVanillaIceCream) {
            @Suppress("NewApi")
            val instanceNames = ProfilingStorage.getProfilingEnabledInstanceNames(context)
            if (instanceNames.isNotEmpty()) {
                @Suppress("NewApi")
                sampleProfiling(context, instanceNames)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun sampleProfiling(context: Context, instanceNames: Set<String>) {
        val appStartInfo = getAppStartInfo(context) ?: return
        val sampleRate = ProfilingStorage.getSampleRate(context)
        if (RateBasedSampler<Unit>(sampleRate).sample(Unit)) {
            Profiling.start(
                context = context,
                startReason = ProfilingStartReason.APPLICATION_LAUNCH,
                additionalAttributes = mapOf(PerfettoProfiler.TELEMETRY_KEY_APP_START_INFO to appStartInfo),
                sdkInstanceNames = instanceNames
            )
        }
        ProfilingStorage.removeSampleRate(context)
    }

    /**
     * Returns the Android [ApplicationStartInfo] start reason as a telemetry string if this
     * process was started from an eligible launcher reason, or null if profiling should not run.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun getAppStartInfo(context: Context): String? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val startReason = manager?.getHistoricalProcessStartReasons(1)
            ?.firstOrNull()?.reason
        return when (startReason) {
            ApplicationStartInfo.START_REASON_LAUNCHER -> TELEMETRY_APP_START_INFO_LAUNCHER
            ApplicationStartInfo.START_REASON_START_ACTIVITY -> TELEMETRY_APP_START_INFO_ACTIVITY
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> TELEMETRY_APP_START_INFO_RECENTS
            else -> null
        }
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

    private companion object {
        private const val TELEMETRY_APP_START_INFO_LAUNCHER = "launcher"
        private const val TELEMETRY_APP_START_INFO_ACTIVITY = "start_activity"
        private const val TELEMETRY_APP_START_INFO_RECENTS = "recents"
    }
}
