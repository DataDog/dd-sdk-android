/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.event

/**
 * An interface which can be implemented to modify the writable attributes inside an event [T].
 */
interface EventMapper<T : Any> {
    /**
     * By implementing this method you can intercept and modify the writable
     * attributes inside any event [T] before it gets serialised.
     *
     * @param event the event to be serialised
     * @return the modified event [T] or NULL
     *
     * Please note that if you return NULL from this method the event will be dropped and will not
     * be serialised.
     */
    fun map(event: T): T?
}
