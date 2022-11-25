/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.net

import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface DataUploader {
    // TODO RUMM-2298 Support 1:many relationship between batch and requests
    fun upload(
        context: DatadogContext,
        batch: List<ByteArray>,
        batchMeta: ByteArray?
    ): UploadStatus
}
