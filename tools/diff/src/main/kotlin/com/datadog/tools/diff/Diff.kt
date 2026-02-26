/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.diff

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Diff

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DiffReplace

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DiffMerge

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DiffIgnore

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DiffAppend

data class DiffOptional<T>(
    val item: T?,
    val exists: Boolean
) {
    companion object {
        fun <T> empty(): DiffOptional<T> {
            return DiffOptional(
                item = null,
                exists = false
            )
        }
    }
}

fun <T> T.wrapOptional(): DiffOptional<T> {
    return DiffOptional(
        item = this,
        exists = true
    )
}

fun <T> makeDiff(old: T, new: T): DiffOptional<T> {
    return if (old == new) {
        DiffOptional.empty()
    } else {
        new.wrapOptional()
    }
}
