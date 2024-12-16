/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

/**
 * This utility function helps replace joinToString calls with more efficient StringBuilder's methods calls.
 * In case when content should be separated with some separator it's handy to add it in front of new string only
 * if buffer already contain some data
 * @param str - string that should be added into buffer only if it already contains some data
 */
fun StringBuilder.appendIfNotEmpty(str: String) = apply {
    if (isNotEmpty()) append(str)
}

/**
 * This utility function helps replace joinToString calls with more efficient StringBuilder's methods calls.
 * In case when content should be separated with some separator it's handy to add it in front of new string only
 * if buffer already contain some data
 * @param char - char that should be added into buffer only if it already contains some data
 */
fun StringBuilder.appendIfNotEmpty(char: Char) = apply {
    if (isNotEmpty()) append(char)
}
