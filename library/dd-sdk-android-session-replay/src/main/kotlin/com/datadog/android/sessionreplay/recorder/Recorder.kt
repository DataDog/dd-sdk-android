/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.app.Activity
import android.view.Window

internal interface Recorder {

    fun startRecording(windows: List<Window>, ownerActivity: Activity)

    fun stopRecording(windows: List<Window>)

    fun stopRecording()
}
