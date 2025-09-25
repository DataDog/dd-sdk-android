/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.storage

internal data class ExposureAllocation(
    val key: String
)

internal data class ExposureFlag(
    val key: String
)

internal data class ExposureVariant(
    val key: String
)

internal data class ExposureSubject(
    val id: String,
    val attributes: Any
)

internal data class FlagsExposureEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val allocation: ExposureAllocation,
    val flag: ExposureFlag,
    val variant: ExposureVariant,
    val subject: ExposureSubject
)
