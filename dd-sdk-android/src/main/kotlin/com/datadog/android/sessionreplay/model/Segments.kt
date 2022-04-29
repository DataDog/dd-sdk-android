/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.model

enum class CreationReason {
    INIT,
    MAX_DURATION,
    MAX_SIZE,
    VIEW_CHANGE,
    BEFORE_UNLOAD,
    VISIBILITY_HIDDEN
}

data class Segment(
    val applicationId:String,
    val sessionId:String,
    val viewId:String,
    val start: Long,
    val end: Long,
    val hasFullSnapshot: Boolean,
    val recordsCount: Int,
    val creationReason: CreationReason,
    val indexInView:Int,
    val records:List<Record>
)