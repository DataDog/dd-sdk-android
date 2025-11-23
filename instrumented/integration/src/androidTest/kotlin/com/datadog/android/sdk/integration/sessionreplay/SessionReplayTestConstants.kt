/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import java.util.concurrent.TimeUnit

/**
 * Maximum time to wait for asynchronous operations to complete in integration tests.
 * Set to 30 seconds to allow for slower CI environments and network delays.
 */
internal val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)

/**
 * Delay to allow the UI thread to settle and trigger view draws.
 * This is necessary to avoid Bitrise flakiness by waiting for SurfaceFlinger
 * to call the onDraw method which triggers the screen snapshot.
 */
internal const val UI_THREAD_DELAY_MS = 1000L

/**
 * Buffer size for decompressing session replay segments.
 * Standard buffer size for compression/decompression operations.
 */
internal const val DECOMPRESSION_BUFFER_SIZE = 1024

/**
 * Delay before launching a new activity in tests.
 * This gives time to remove the previous activity to avoid issues where Espresso
 * tries to launch a new activity while the previous one is still being removed,
 * which can cause WindowInspector.getGlobalWindowViews() to return multiple windows.
 */
internal const val SLEEP_DELAY_BEFORE_ACTIVITY_LAUNCH_MS = 2000L

/**
 * Sample rate indicating no sessions should be recorded (0%).
 */
internal const val SAMPLE_RATE_NONE = 0f
