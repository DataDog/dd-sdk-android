/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.tools.annotation.NoOpImplementation

/**
 * A callback to be notified when an async job starts or finishes.
 */
@NoOpImplementation
interface AsyncJobStatusCallback {

    /**
     * Notifies that an async job has started.
     */
    fun jobStarted()

    /**
     * Notifies that an async job has finished.
     */
    fun jobFinished()
}
