/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.tools.unit.annotations

import com.datadog.tools.unit.extensions.SystemStreamExtension
import java.io.ByteArrayOutputStream

/**
 * Marks a [ByteArrayOutputStream] parameter in a test method as the [System.out] stream receiver.
 *
 * @see [SystemStreamExtension]
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SystemOutStream
