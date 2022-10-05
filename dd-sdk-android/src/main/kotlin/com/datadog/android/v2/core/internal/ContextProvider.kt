/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import com.datadog.android.v2.api.context.DatadogContext

internal interface ContextProvider {
    // TODO RUMM-0000 getting context may be quite heavy, should it be something non-blocking here?
    // TODO RUMM-0000 lifecycle checks may be needed for the cases when context is requested
    //  when datadog is not initialized yet/anymore (case of UploadWorker, other calls site
    //  should be in sync with lifecycle)
    // TODO RUMM-0000 can be accessed from different threads
    val context: DatadogContext

    fun setFeatureContext(feature: String, context: Map<String, Any?>)

    fun updateFeatureContext(feature: String, entries: Map<String, Any?>)
}
