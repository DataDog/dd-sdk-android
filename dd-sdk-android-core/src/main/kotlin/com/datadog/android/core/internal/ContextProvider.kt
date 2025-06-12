/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature

internal interface ContextProvider {
    // TODO RUM-3784 lifecycle checks may be needed for the cases when context is requested
    //  when datadog is not initialized yet/anymore (case of UploadWorker, other calls site
    //  should be in sync with lifecycle)
    /**
     * @param withFeatureContexts Feature contexts ([DatadogContext.featuresContext] property) to include
     * in the [DatadogContext] provided. The value should be the feature names as declared by [Feature.name].
     * Default is empty, meaning that no feature contexts will be included.
     */
    fun getContext(withFeatureContexts: Set<String>): DatadogContext
}
