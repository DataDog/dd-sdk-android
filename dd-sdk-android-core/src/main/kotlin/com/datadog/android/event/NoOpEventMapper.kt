/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.event

/**
 * No-op implementation of [EventMapper]. Will return the same instance.
 *
 * @param T type boundary of the mapped object.
 */
class NoOpEventMapper<T : Any> : EventMapper<T> {

    /** @inheritdoc */
    override fun map(event: T): T {
        return event
    }

    /** @inheritdoc */
    override fun equals(other: Any?): Boolean {
        return other is NoOpEventMapper<*>
    }

    /** @inheritdoc */
    override fun hashCode(): Int {
        return 0
    }
}
