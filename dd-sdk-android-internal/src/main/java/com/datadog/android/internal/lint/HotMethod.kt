/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.lint

/**
 * Marks a method as a hot path — one that can be invoked on every frame or touch event.
 *
 * Implementations must avoid heap allocations, blocking operations, and any work
 * that introduces GC pressure. The IDE/Lint will highlight annotated methods as a
 * reminder to reviewers.
 *
 * @property message A short description of why this method is hot (e.g. "called per frame by JankStats").
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class HotMethod(val message: String)
