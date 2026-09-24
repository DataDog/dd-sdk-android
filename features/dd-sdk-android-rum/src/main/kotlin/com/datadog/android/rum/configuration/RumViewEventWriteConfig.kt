/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

/**
 * Controls the write strategy for RUM view events, determining whether full view events
 * or partial diff updates (Partial View Updates) are written for each view update.
 *
 * @see com.datadog.android.rum.RumConfiguration.Builder.setRumViewEventWriteConfig
 */
sealed interface RumViewEventWriteConfig {

    /**
     * Every view update writes a complete [com.datadog.android.rum.model.ViewEvent].
     * Use this to opt out of Partial View Updates, for example if you rely on a proxy
     * that inspects the full raw RUM payload for every view update.
     */
    object AlwaysFullView : RumViewEventWriteConfig

    /**
     * Writes a complete [com.datadog.android.rum.model.ViewEvent] only for the first update
     * of a view. Subsequent updates write only the diff as a `ViewUpdateEvent`, reducing
     * payload size for views with many updates. This is the default behaviour.
     */
    object FullViewOnlyAtStart : RumViewEventWriteConfig
}
