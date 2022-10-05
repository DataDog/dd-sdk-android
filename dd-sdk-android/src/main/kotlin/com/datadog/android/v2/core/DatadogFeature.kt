/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.Storage

internal class DatadogFeature(
    val sdkCore: SdkCore,
    val storage: Storage,
    val uploader: DataUploader
) : FeatureScope {

    // TODO RUMM-0000 there is no thread switch here, it stays the same.
    // Need to clarify the threading. We either switch thread here or in Storage.
    // Or give the ability to specify the executor to the caller.
    @Suppress("ThreadSafety")
    override fun withWriteContext(callback: (DatadogContext, EventBatchWriter) -> Unit) {
        val context = (sdkCore as? DatadogCore)?.contextProvider?.context ?: return
        storage.writeCurrentBatch(context) {
            callback(context, it)
        }
    }
}
