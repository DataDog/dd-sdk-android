/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

internal class NoOpRecorder : Recorder {

    override fun unregisterCallbacks() {
        // No Op
    }

    override fun registerCallbacks() {
        // No Op
    }

    override fun resumeRecorders() {
        // No Op
    }

    override fun stopRecorders() {
        // No Op
    }

    override fun stopProcessingRecords() {
        // No Op
    }
}
