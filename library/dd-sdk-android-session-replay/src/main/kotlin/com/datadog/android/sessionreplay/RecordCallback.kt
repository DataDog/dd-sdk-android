/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Notifies the receiver whenever the screen is recorded or not.
 * For internal usage only.
 */
interface RecordCallback {

    /**
     * Called when we started recording the current screen.
     */
    fun onStartRecording()

    /**
     * Called when we stopped recording the current screen.
     */
    fun onStopRecording()
}
