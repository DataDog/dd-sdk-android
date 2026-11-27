/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Experimental. A flag fetch failed because response signing rejected it, not because of the network.
 *
 * @property reason why verification failed; [ResponseSigningFailureReason.code] is the cross-SDK code.
 */
@ExperimentalFlagsApi
class FlagsResponseSigningException internal constructor(
    val reason: ResponseSigningFailureReason
) : RuntimeException(reason.message) {
    // True when the failing context update also delivers this instance to its callback, not only as a state.
    internal var reportedByContextUpdate: Boolean = false
}
