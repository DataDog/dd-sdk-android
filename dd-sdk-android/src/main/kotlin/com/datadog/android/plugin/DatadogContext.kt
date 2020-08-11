/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.plugin

/**
 * Provides general information about the current context of the library.
 * @see DatadogRumContext
 */
data class DatadogContext(
    val rum: DatadogRumContext? = null
)
