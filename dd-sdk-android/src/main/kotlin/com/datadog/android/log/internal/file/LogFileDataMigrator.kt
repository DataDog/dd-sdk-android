package com.datadog.android.log.internal.file

import com.datadog.android.core.internal.data.DataMigrator
import java.io.File

internal class LogFileDataMigrator(
    private val rootDirectory: File
) : DataMigrator {

    private val patches: MutableMap<String, (File) -> Unit> = HashMap()

    init {
        patches[LogFileStrategy.DATA_FOLDER_ROOT] = {
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
