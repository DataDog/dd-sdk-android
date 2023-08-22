/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils

operator fun Char.times(i: Int): String {
    check(i >= 0) { "Can't repeat character negative times " }
    return String(CharArray(i) { this })
}
