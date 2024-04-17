/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.annotation

/**
 * Adding this annotation on an interface will generate a No-Op implementation class.
 * @property publicNoOpImplementation if true, the NoOp implementation will be made public,
 * otherwise it will be marked as Internal (default: false)
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NoOpImplementation(
    val publicNoOpImplementation: Boolean = false
)
