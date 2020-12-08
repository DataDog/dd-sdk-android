/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.event

import com.datadog.android.event.EventMapper

internal class NoOpEventMapper<T : Any> : EventMapper<T> {

    override fun map(event: T): T {
        return event
    }
}
