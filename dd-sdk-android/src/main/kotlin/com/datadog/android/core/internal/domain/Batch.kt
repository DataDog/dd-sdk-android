/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

/**
 * Represent a batch of logs read from a persisted location.
 */
internal data class Batch(
    val id: String,
    val logs: List<String>
)
