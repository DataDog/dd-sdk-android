/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import java.io.File
import java.util.Locale

internal class DataStoreFileHelper(
    private val internalLogger: InternalLogger
) {
    internal fun getDataStoreFile(
        storageDir: File,
        featureName: String,
        key: String
    ): File {
        val dataStoreDirectory = getDataStoreDirectory(
            featureName = featureName,
            storageDir = storageDir
        )

        return File(dataStoreDirectory, key)
    }

    internal fun getDataStoreDirectory(
        storageDir: File,
        featureName: String
    ): File {
        val folderName = DATASTORE_FOLDER_NAME.format(
            Locale.US,
            DataStoreHandler.CURRENT_DATASTORE_VERSION
        )

        val dataStoreDirectory = File(
            File(storageDir, folderName),
            featureName
        )

        if (!dataStoreDirectory.existsSafe(internalLogger)) {
            dataStoreDirectory.mkdirsSafe(internalLogger)
        }

        return dataStoreDirectory
    }

    internal companion object {
        internal const val DATASTORE_FOLDER_NAME = "datastore_v%s"
    }
}
