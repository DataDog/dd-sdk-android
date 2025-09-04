/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

internal fun interface FeatureContextProvider {
    /**
     * Returns **frozen** snapshot of the feature context which is not mutated over the time.
     */
    fun getFeatureContext(featureName: String): Map<String, Any?>
}
