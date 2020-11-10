/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.data.DataMigrator
import java.io.File

internal class LogFileDataMigrator(
    private val rootDirectory: File
) : DataMigrator {

    private val patches: MutableMap<String, (File) -> Unit> = HashMap()

    init {
        patches[LogFileStrategy.INTERMEDIATE_DATA_FOLDER] = {
            if (it.exists()) {
                it.deleteRecursively()
            }
        }
    }

    override fun migrateData() {
        rootDirectory.listFiles()
            ?.let {
                it.filter { patches.containsKey(it.name) }
                    .forEach { file ->
                        patches[file.name]?.invoke(file)
                    }
            }
    }
}
