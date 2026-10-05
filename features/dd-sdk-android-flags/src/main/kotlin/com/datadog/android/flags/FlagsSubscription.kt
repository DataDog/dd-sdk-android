/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/** Controls delivery for one flag client event registration. */
fun interface FlagsSubscription {
    /**
     * Removes the pending listener and releases its captured references.
     * Thread-safe and idempotent. A listener already claimed for delivery may still run;
     * this neither interrupts nor waits for it. Completed delivery cannot be undone.
     * Does not cancel flag fetching, cache loading, or other registrations.
     */
    fun unsubscribe()
}
