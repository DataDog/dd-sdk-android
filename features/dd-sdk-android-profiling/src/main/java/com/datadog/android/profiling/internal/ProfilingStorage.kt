/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.profiling.internal.trigger.OomProfileOutcomeMarker
import com.datadog.android.profiling.internal.trigger.PendingOomGatingEvent
import com.datadog.android.profiling.internal.trigger.PendingOomProfile

@Suppress("TooManyFunctions")
internal object ProfilingStorage {

    internal const val KEY_PROFILING_ENABLED = "dd_profiling_enabled"
    internal const val KEY_PROFILING_SAMPLE_RATE = "dd_profiling_sample_rate"
    internal const val KEY_PENDING_OOM_PROFILE = "dd_profiling_pending_oom"
    internal const val KEY_PENDING_OOM_GATING_EVENT = "dd_profiling_pending_oom_gating_event"
    internal const val KEY_OOM_PROFILE_OUTCOME = "dd_profiling_oom_profile_outcome"

    /**
     * Key of an OOM handshake record of the SDK instance named [namespace]: several instances share
     * the application preferences, and must not overwrite each other's records.
     */
    internal fun oomKey(baseKey: String, namespace: String): String = "${baseKey}_$namespace"

    @Volatile
    internal var sharedPreferencesStorage: SharedPreferencesStorage? = null

    internal fun setSampleRate(appContext: Context, sampleRate: Float) {
        getStorage(appContext).putFloat(KEY_PROFILING_SAMPLE_RATE, sampleRate)
    }

    internal fun getSampleRate(appContext: Context): Float {
        return getStorage(appContext).getFloat(KEY_PROFILING_SAMPLE_RATE, -1f)
    }

    internal fun removeSampleRate(appContext: Context) {
        return getStorage(appContext).remove(KEY_PROFILING_SAMPLE_RATE)
    }

    /**
     * Records an out-of-memory profile that still has to be uploaded. The write is synchronous:
     * the caller is typically a process that is about to be killed.
     */
    internal fun setPendingOomProfile(appContext: Context, namespace: String, profile: PendingOomProfile) {
        getStorage(appContext).putString(oomKey(KEY_PENDING_OOM_PROFILE, namespace), profile.toJson(), sync = true)
    }

    internal fun getPendingOomProfile(appContext: Context, namespace: String): PendingOomProfile? {
        val serialized = getStorage(appContext).getString(oomKey(KEY_PENDING_OOM_PROFILE, namespace)) ?: return null
        return PendingOomProfile.fromJson(serialized)
    }

    internal fun removePendingOomProfile(appContext: Context, namespace: String) {
        getStorage(appContext).remove(oomKey(KEY_PENDING_OOM_PROFILE, namespace), sync = true)
    }

    /**
     * Records a RUM OOM gating event that has no matching trigger profile yet, in case the OS
     * defers delivery of the trigger result to a later launch. The write is synchronous: the
     * event may have arrived just before the process is killed.
     */
    internal fun setPendingOomGatingEvent(appContext: Context, namespace: String, event: PendingOomGatingEvent) {
        getStorage(appContext).putString(oomKey(KEY_PENDING_OOM_GATING_EVENT, namespace), event.toJson(), sync = true)
    }

    internal fun getPendingOomGatingEvent(appContext: Context, namespace: String): PendingOomGatingEvent? {
        val serialized =
            getStorage(appContext).getString(oomKey(KEY_PENDING_OOM_GATING_EVENT, namespace)) ?: return null
        return PendingOomGatingEvent.fromJson(serialized)
    }

    internal fun removePendingOomGatingEvent(appContext: Context, namespace: String) {
        getStorage(appContext).remove(oomKey(KEY_PENDING_OOM_GATING_EVENT, namespace), sync = true)
    }

    /**
     * Records the outcome of the OOM profile of a held RUM error, synchronously.
     *
     * @return whether it was persisted.
     */
    internal fun setOomProfileOutcome(
        appContext: Context,
        namespace: String,
        marker: OomProfileOutcomeMarker
    ): Boolean {
        return getStorage(
            appContext
        ).commitStrings(mapOf(oomKey(KEY_OOM_PROFILE_OUTCOME, namespace) to marker.toJson()))
    }

    /**
     * Records an [OomProfileOutcomeMarker.State.IN_FLIGHT] marker and removes the pending OOM
     * profile in a single synchronous commit, before the profile is written.
     *
     * @return whether it was persisted.
     */
    internal fun startOomProfileWrite(
        appContext: Context,
        namespace: String,
        marker: OomProfileOutcomeMarker
    ): Boolean {
        return getStorage(appContext).commitStrings(
            values = mapOf(oomKey(KEY_OOM_PROFILE_OUTCOME, namespace) to marker.toJson()),
            removals = setOf(oomKey(KEY_PENDING_OOM_PROFILE, namespace))
        )
    }

    internal fun getOomProfileOutcome(appContext: Context, namespace: String): OomProfileOutcomeMarker? {
        val serialized = getStorage(appContext).getString(oomKey(KEY_OOM_PROFILE_OUTCOME, namespace)) ?: return null
        return OomProfileOutcomeMarker.fromJson(serialized)
    }

    internal fun removeOomProfileOutcome(appContext: Context, namespace: String): Boolean {
        return getStorage(appContext).commitStrings(emptyMap(), setOf(oomKey(KEY_OOM_PROFILE_OUTCOME, namespace)))
    }

    @JvmStatic
    internal fun addProfilingFlag(appContext: Context) {
        getStorage(appContext).putBoolean(KEY_PROFILING_ENABLED, true)
    }

    @JvmStatic
    internal fun isProfilingEnabled(appContext: Context): Boolean {
        return getStorage(appContext).getBoolean(KEY_PROFILING_ENABLED, false)
    }

    @JvmStatic
    internal fun removeProfilingFlag(appContext: Context) {
        getStorage(appContext).remove(KEY_PROFILING_ENABLED)
    }

    @Suppress("ReturnCount")
    private fun getStorage(context: Context): SharedPreferencesStorage {
        sharedPreferencesStorage?.let { return it }
        synchronized(this) {
            sharedPreferencesStorage?.let { return it }
            SharedPreferencesStorage(context).also {
                sharedPreferencesStorage = it
                return it
            }
        }
    }
}
