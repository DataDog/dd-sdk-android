/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api

interface DatadogSpanIdConverter {

    fun from(s: String?): Long

    @Throws(NumberFormatException::class)
    fun fromHex(s: String?): Long

    @Throws(NumberFormatException::class)
    fun fromHex(s: String?, start: Int, len: Int, lowerCaseOnly: Boolean): Long

    fun fromHexOrDefault(s: String?, defaultValue: Long): Long = try {
        fromHex(s)
    } catch (e: NumberFormatException) {
        defaultValue
    }

    fun toString(id: Long): String

    fun toHexString(id: Long): String

    fun toHexStringPadded(id: Long): String
}
