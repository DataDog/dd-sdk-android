/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests

import java.util.concurrent.TimeUnit

@Suppress("UtilityClassWithPublicConstructor")
abstract class BaseTest {

    companion object {
        internal val LONG_WAIT_MS = TimeUnit.SECONDS.toMillis(60)
        internal val MEDIUM_WAIT_MS = TimeUnit.SECONDS.toMillis(30)

        // TODO RUM-9917 Avoid using unconditional wait locks
        // to align with UploadFrequency max value + 1s for async execution
        internal val UPLOAD_CYCLE_MAX_WAIT_MS = TimeUnit.SECONDS.toMillis(6)
        internal const val SHORT_WAIT_MS = 500L
    }
}
