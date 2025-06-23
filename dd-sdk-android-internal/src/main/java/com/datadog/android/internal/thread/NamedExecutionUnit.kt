/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.thread

import java.util.Locale
import java.util.concurrent.Callable

/**
 * Provides a possibility to attach human-readable names to the execution units.
 */
interface NamedExecutionUnit {
    /**
     * Sanitized name after replacing spaces, colons, periods, and commas
     * with underscores (`_`), and converting all characters to lowercase.
     */
    val name: String
}

private val SanitizedRegex = "[ :.,]".toRegex()

/**
 * A wrapper around a [Runnable] that assigns it a sanitized, lowercase name.
 *
 * This class is useful when you want to associate a human-readable  name with a [Runnable],
 * for logging, debugging, or tracking purposes.
 *
 * The provided [name] is sanitized by replacing spaces, colons, periods, and commas
 * with underscores (`_`), and converting all characters to lowercase.
 *
 * @param name The name to associate with this runnable, will be sanitized for safe usage.
 * @param runnable The actual runnable to be executed when [run] is called.
 */
class NamedRunnable(name: String, private val runnable: Runnable) : NamedExecutionUnit, Runnable by runnable {

    /**
     * Sanitized name after replacing spaces, colons, periods, and commas
     * with underscores (`_`), and converting all characters to lowercase.
     */
    override val name: String = name.replace(SanitizedRegex, "_").lowercase(Locale.US)
}

/**
 * A wrapper around a [Callable] that assigns it a sanitized, lowercase name.
 *
 * This class is useful when you want to associate a human-readable  name with a [Callable],
 * for logging, debugging, or tracking purposes.
 *
 * The provided [name] is sanitized by replacing spaces, colons, periods, and commas
 * with underscores (`_`), and converting all characters to lowercase.
 *
 * @param V The type of the value returned by the callable.
 * @param name The name to associate with this callable, will be sanitized for safe usage.
 * @param callable The actual callable to be executed when [call] is called.
 */
class NamedCallable<V>(name: String, private val callable: Callable<V>) : NamedExecutionUnit, Callable<V> by callable {

    /**
     * Sanitized name after replacing spaces, colons, periods, and commas
     * with underscores (`_`), and converting all characters to lowercase.
     */
    override val name: String = name.replace(SanitizedRegex, "_").lowercase(Locale.US)
}
