/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

import com.datadog.android.lint.InternalApi

/**
 * Controls the write strategy for RUM view events, determining whether full view events
 * or partial diff updates are written for each view update.
 */
@InternalApi
sealed interface RumViewEventWriteConfig {

    /**
     * Every view update writes a complete [com.datadog.android.rum.model.ViewEvent].
     * This is the default behaviour; no partial updates are used.
     */
    object AlwaysFullView : RumViewEventWriteConfig

    /**
     * Writes a complete [com.datadog.android.rum.model.ViewEvent] only for the first update
     * of a view. Subsequent updates write only the diff as a `ViewUpdateEvent`, reducing
     * payload size for views with many updates.
     */
    object FullViewOnlyAtStart : RumViewEventWriteConfig
}
