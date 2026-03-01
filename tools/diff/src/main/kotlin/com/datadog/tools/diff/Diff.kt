/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.diff

import kotlin.reflect.KClass

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

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DiffMap

enum class DiffVisibility { PUBLIC, INTERNAL, PROTECTED, PRIVATE }

@Repeatable
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class DiffConfig(
    val forClass: KClass<*>,
    val merge: Array<String> = [], // list of fields that should behave as if annotated with DiffMerge
    val ignore: Array<String> = [], // list of fields that should behave as if annotated with DiffIgnore
    val append: Array<String> = [], // list of fields that should behave as if annotated with DiffAppend
    val diffMap: Array<String> = [], // list of fields that should behave as if annotated with DiffMap
    val visibility: DiffVisibility = DiffVisibility.INTERNAL,
)

data class DiffOptional<T>(
    val item: T?,
    val exists: Boolean
) {
    fun get(): T? {
        return if (exists) {
            item
        } else {
            null
        }
    }

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

fun <K : Any, V> diffMap(old: Map<K, V>, new: Map<K, V>): Map<K, V> {
    return buildMap {
        for ((key, newValue) in new) {
            val oldValue = old[key]

            if (newValue != oldValue) {
                put(key, newValue)
            }
        }
    }
}
