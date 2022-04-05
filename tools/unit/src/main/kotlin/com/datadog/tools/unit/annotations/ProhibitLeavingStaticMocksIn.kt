/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.annotations

import java.lang.annotation.Inherited
import kotlin.reflect.KClass

/**
 * Annotation for [ProhibitLeavingStaticMocksIn]. Can be applied to both class and method, will
 * instruct extension to check that no object starting from the roots specified has a mock left
 * in a static field (Java) or in a property of object class/companion object (Kotlin).
 *
 * NB: Lateinit properties will be just reported to console, because it is not possible to
 * reset them to the original state.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class ProhibitLeavingStaticMocksIn(
    /**
     * Classes to watch.
     */
    vararg val value: KClass<*>,
    /**
     * Package prefixes to process.
     */
    val packagePrefixes: Array<String> = ["com.datadog"]
)
