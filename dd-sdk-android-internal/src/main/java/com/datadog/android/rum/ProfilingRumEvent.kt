/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * Class representing a profiling event. (Only for Dogfooding purpose)
 */
// TODO RUM-11043: Update or remove this for proper RUM profiling event.
data class ProfilingRumEvent(
    val success: Boolean,
    val errorCode: Int,
    val errorMessage: String?,
    val duration: Long,
    val fileSize: Long
)
