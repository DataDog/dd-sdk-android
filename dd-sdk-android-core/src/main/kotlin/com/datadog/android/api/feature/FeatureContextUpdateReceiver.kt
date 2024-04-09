/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import androidx.annotation.AnyThread

/**
 * Receiver for feature context updates.
 */
fun interface FeatureContextUpdateReceiver {

    /**
     * Called when the context for a feature is updated.
     * @param featureName the name of the feature
     * @param event the updated context
     */
    @AnyThread
    fun onContextUpdate(featureName: String, event: Map<String, Any?>)
}
