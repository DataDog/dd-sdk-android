/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import com.datadog.android.api.context.DatadogContext

internal interface ContextProvider {
    // TODO RUM-3784 getting context may be quite heavy, should it be something non-blocking here?

    // TODO RUM-3784 lifecycle checks may be needed for the cases when context is requested
    //  when datadog is not initialized yet/anymore (case of UploadWorker, other calls site
    //  should be in sync with lifecycle)

    // TODO RUM-3784 can be accessed from different threads
    val context: DatadogContext

    fun setFeatureContext(feature: String, context: Map<String, Any?>)

    fun getFeatureContext(feature: String): Map<String, Any?>
}
