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
 * @property exclude Check names or specific function/constructor names to skip for this site only.
 *   Built-in category strings: `"constructor"`, `"anonymous-object"`, `"lambda"`,
 *   `"string-template"`, `"factory"`, `"collection-ops"`.
 *   You may also pass a bare method name (e.g. `"forEach"`) or a qualified name
 *   (e.g. `"kotlin.collections.List.forEach"`) to exclude just that call.
 *   Prefer a narrow per-name exclusion over a broad category exclusion.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class HotMethod(val message: String, val exclude: Array<String> = []) {
    companion object {
        const val CHECK_CONSTRUCTOR = "constructor"
        const val CHECK_ANONYMOUS_OBJECT = "anonymous-object"
        const val CHECK_LAMBDA = "lambda"
        const val CHECK_STRING_TEMPLATE = "string-template"
        const val CHECK_FACTORY = "factory"
        const val CHECK_COLLECTION_OPS = "collection-ops"
    }
}
