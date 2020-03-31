/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.annotations

import android.os.Build
import com.datadog.tools.unit.extensions.ApiLevelExtension

/**
 * Declares a test method as targeting a specific Android API level.
 *
 * @see [ApiLevelExtension]
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TestTargetApi(
    val value: Int = Build.VERSION_CODES.BASE
)
