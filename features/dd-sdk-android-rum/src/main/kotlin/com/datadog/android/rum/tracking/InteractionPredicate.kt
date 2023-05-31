/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

/**
 * Provides custom attributes for the events produced by the user action tracking strategy.
 */
interface InteractionPredicate {
    /**
     * Sets a custom name for the intercepted touch target.
     * @return the name to use for this touch target (if null or blank, the default will be used)
     */
    fun getTargetName(target: Any): String?
}
