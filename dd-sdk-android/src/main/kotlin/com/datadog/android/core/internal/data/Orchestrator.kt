/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data

import java.io.File

internal interface Orchestrator {

    @Throws(SecurityException::class)
    fun getWritableFile(itemSize: Int): File

    @Throws(SecurityException::class)
    fun getReadableFile(excludeFileNames: Set<String>): File?

    fun getAllFiles(): Array<out File>
}
