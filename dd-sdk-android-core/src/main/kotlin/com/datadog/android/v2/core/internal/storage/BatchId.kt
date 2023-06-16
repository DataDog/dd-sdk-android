/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import java.io.File

internal data class BatchId(
    val id: String
) {

    fun matchesFile(file: File): Boolean {
        return file.extractFileId() == id
    }

    companion object {

        fun fromFile(file: File): BatchId {
            return BatchId(file.extractFileId())
        }

        private fun File.extractFileId(): String {
            return absolutePath
        }
    }
}
