/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.FlagsClientEventListener
import com.datadog.android.flags.FlagsSubscription

/** Internal capability used by the public events facade without extending FlagsClient. */
internal interface FlagsEventSource {
    fun onFirstFlags(listener: FlagsClientEventListener): FlagsSubscription
}
