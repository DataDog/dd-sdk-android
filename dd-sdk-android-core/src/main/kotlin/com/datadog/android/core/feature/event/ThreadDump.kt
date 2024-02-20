/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.feature.event

/**
 * Thread information to be propagated on the message bus to the features doing crash reporting.
 * Is not a part of official API.
 */
@Suppress("UndocumentedPublicClass", "UndocumentedPublicProperty")
data class ThreadDump(
    val name: String,
    val state: String,
    val stack: String,
    val crashed: Boolean
)
