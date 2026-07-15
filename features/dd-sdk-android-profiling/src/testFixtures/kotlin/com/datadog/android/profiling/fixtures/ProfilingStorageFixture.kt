/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.fixtures

import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.profiling.internal.ProfilingStorage

/**
 * Test-only access to the [SharedPreferencesStorage] used by [ProfilingStorage].
 *
 * Integration tests that drive [ProfilingFeatureTestHandle] without a real Android
 * [android.content.Context] must inject a Mockito mock here in `@BeforeEach` and call
 * [reset] in `@AfterEach`.
 */
object ProfilingStorageFixture {

    /** Replace the storage instance used by [ProfilingStorage] (typically a Mockito mock). */
    fun stubWith(storage: SharedPreferencesStorage?) {
        ProfilingStorage.sharedPreferencesStorage = storage
    }

    /** Clear the stubbed storage instance. */
    fun reset() {
        ProfilingStorage.sharedPreferencesStorage = null
    }
}
