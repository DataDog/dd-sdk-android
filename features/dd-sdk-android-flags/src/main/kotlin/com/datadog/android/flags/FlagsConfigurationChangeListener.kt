/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/** Receives configuration availability notifications without changing client readiness. */
interface FlagsConfigurationChangeListener {
    /**
     * Called after the initial persistent storage read completes, including an empty or failed read.
     * The snapshot is readable before this callback. A getter's persistence wait timeout does not
     * trigger this callback, and this notification does not indicate network readiness.
     *
     * Callbacks run synchronously under an internal listener lock. Keep them fast and non-blocking.
     * Catch exceptions in implementations: an exception propagates and skips subsequent listeners.
     *
     * @param changedKeys Keys installed from disk, or an empty set if no assignments were installed
     * (including when a network response already superseded the persisted snapshot). These describe
     * the completed restoration; getters always read the latest snapshot.
     */
    fun onConfigurationChanged(changedKeys: Set<String>)
}
