/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.lint

/**
 * This annotation marks given method or property as internal, meaning it shouldn't be used
 * outside of Datadog modules and it can be changed at any moment.
 *
 * This annotation participates in the lint check provided by the [InternalApiUsageDetector].
 *
 * Note: Don't use this annotation on interfaces / non-final classes, because implementation will
 * be flagged as internal as a whole, put it on individual methods/properties instead.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class InternalApi
