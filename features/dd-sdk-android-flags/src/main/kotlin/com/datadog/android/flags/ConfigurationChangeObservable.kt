/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/** Optional capability for observing configuration availability independently of client state. */
interface ConfigurationChangeObservable {
    /**
     * Registers [listener]. If the initial disk read already completed, its completion is replayed
     * synchronously before this method returns, including completion with no restored assignments.
     * Re-registering a listener replays completion again but does not duplicate its registration.
     */
    fun addConfigurationChangeListener(listener: FlagsConfigurationChangeListener)

    /** Removes [listener] from future configuration notifications. */
    fun removeConfigurationChangeListener(listener: FlagsConfigurationChangeListener)
}
