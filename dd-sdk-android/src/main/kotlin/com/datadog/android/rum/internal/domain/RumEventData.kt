/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.RumResourceKind

internal sealed class RumEventData(val category: String) {

    internal data class Resource(
        val kind: RumResourceKind,
        val url: String,
        val durationNanoSeconds: Long
    ) : RumEventData("resource")

    internal data class UserAction(
        val name: String
    ) : RumEventData("user_action")

    internal data class View(
        val name: String,
        val durationNanoSeconds: Long,
        val version: Int = 1
    ) : RumEventData("view")

    internal data class Error(
        val message: String,
        val origin: String,
        val throwable: Throwable
    ) : RumEventData("error")
}
