/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.flags

/**
 * Event for sending a RUM exposure.
 * @param timestamp time when the event occurred.
 * @param flagKey name of the flag.
 * @param allocationKey name of the allocation.
 * @param exposureKey name of the exposure.
 * @param subjectKey name of the subject.
 * @param variantKey name of the variant.
 * @param subjectAttributes attributes for this event.
 */
data class RumFlagExposureMessage(
    val timestamp: Long,
    val flagKey: String,
    val allocationKey: String,
    val exposureKey: String,
    val subjectKey: String,
    val variantKey: String,
    val subjectAttributes: Map<String, Any>
)
