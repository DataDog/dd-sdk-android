/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights.timeline

internal sealed interface TimelineEvent {
    object Action : TimelineEvent
    data class SlowFrame(val duration: Long) : TimelineEvent
    data class ResourceRequest(val uri: String) : TimelineEvent
    data class LongTask(val duration: Long) : TimelineEvent
}
