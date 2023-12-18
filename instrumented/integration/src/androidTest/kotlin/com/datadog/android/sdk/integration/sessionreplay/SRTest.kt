/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import java.util.concurrent.TimeUnit

internal object SRTest {
    internal val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(60)
}
