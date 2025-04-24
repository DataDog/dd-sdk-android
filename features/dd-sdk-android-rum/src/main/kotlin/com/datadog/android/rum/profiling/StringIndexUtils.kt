/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

internal class StringIndexUtils {
    private val stringTable = mutableListOf<String>()
    private val stringIndexMap = mutableMapOf<String, Long>()

    init {
        // string_table[0] = "" as per spec
        stringTable.add("")
        stringIndexMap[""] = 0
    }

    fun getStringIndex(str: String): Long {
        stringIndexMap[str]?.let { return it }
        // Add new string to table and map, return its index
        stringTable.add(str)
        val newIndex = (stringTable.size - 1).toLong()
        stringIndexMap[str] = newIndex
        return newIndex
    }

    fun getStringTable(): List<String> {
        return stringTable
    }
}

