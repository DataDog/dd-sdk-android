/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.data.upload

import androidx.annotation.WorkerThread
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.tools.annotation.NoOpImplementation

// TODO RUMM-0000 Should replace com.datadog.android.core.internal.net.Flusher once
//  features are configured as V2
@NoOpImplementation
internal interface Flusher {

    @WorkerThread
    fun flush(uploader: DataUploader)
}
